/**
 * Copyright 2015 StreamSets Inc.
 * <p>
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.stage.destination.waveanalytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.soap.partner.*;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.SessionRenewer;
import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseTarget;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.CsvHeader;
import com.streamsets.pipeline.config.CsvMode;
import com.streamsets.pipeline.lib.generator.DataGenerator;
import com.streamsets.pipeline.lib.generator.delimited.DelimitedCharDataGenerator;
import com.streamsets.stage.lib.waveanalytics.Errors;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This target writes records to Salesforce Wave Analytics
 */
public abstract class WaveAnalyticsTarget extends BaseTarget {

    private static final Logger LOG = LoggerFactory.getLogger(WaveAnalyticsTarget.class);
    private static final String dataflowList = "/insights/internal_api/v1.0/esObject/workflow";
    private static final String dataflowJson = "/insights/internal_api/v1.0/esObject/workflow/%s/json";
    private static final String startDataflow = "/insights/internal_api/v1.0/esObject/workflow/%s/start";

    // Status values indicating that the job is done
    private static final List<String> DONE = (List<String>)Arrays.asList(
            "Completed",
            "CompletedWithWarnings",
            "Failed",
            "NotProcessed"
    );

    private PartnerConnection connection;
    private String restEndpoint;
    private String datasetID = null;
    private int partNumber = 1;
    private long lastBatchTime = 0;
    private String datasetName = null;

    /**
     * Gives access to the UI configuration of the stage provided by the {@link WaveAnalyticsDTarget} class.
     */
    public abstract String getMetadataJson();
    public abstract String getEdgemartAliasPrefix();
    public abstract String getUsername();
    public abstract String getPassword();
    public abstract int getDatasetWaitTime();
    public abstract boolean getUseDataflow();
    public abstract String getDataflowName();
    public abstract boolean getRunDataflow();

    private ConnectorConfig getConnectorConfig() {
        ConnectorConfig connectorConfig = new ConnectorConfig();

        connectorConfig.setUsername(getUsername());
        connectorConfig.setPassword(getPassword());
        connectorConfig.setCompression(true);
        connectorConfig.setSessionRenewer(new WaveSessionRenewer());

        return connectorConfig;
    }

    // Renew the Salesforce session on timeout
    public class WaveSessionRenewer implements SessionRenewer {
        @Override
        public SessionRenewalHeader renewSession(ConnectorConfig config) throws ConnectionException {
            connection = Connector.newConnection(getConnectorConfig());
            SessionRenewalHeader header = new SessionRenewalHeader();
            header.name = new QName("urn:enterprise.soap.sforce.com", "SessionHeader");
            header.headerElement = connection.getSessionHeader();
            return header;
        }
    }

    private void openDataset() throws ConnectionException, StageException {
        datasetName = getEdgemartAliasPrefix() + "_" + System.currentTimeMillis();

        SObject sobj = new SObject();
        sobj.setType("InsightsExternalData");
        sobj.setField("Format", "Csv");
        sobj.setField("EdgemartAlias", datasetName);
        sobj.setField("MetadataJson", getMetadataJson().getBytes(StandardCharsets.UTF_8));
        sobj.setField("Operation", "Overwrite");
        sobj.setField("Action", "None");

        SaveResult[] results = connection.create(new SObject[]{sobj});
        for (SaveResult sv : results) {
            if (sv.isSuccess()) {
                datasetID = sv.getId();
                partNumber = 1;
                LOG.info("Success creating InsightsExternalData: " + datasetID);
            } else {
                for (Error e : sv.getErrors()) {
                    throw new StageException(Errors.WAVE_01, e.getMessage());
                }
            }
        }
    }

    private void writeToDataset(Batch batch) throws StageException {
        StringWriter writer = new StringWriter();
        DataGenerator gen;
        try {
            gen = new DelimitedCharDataGenerator(writer, CsvMode.CSV.getFormat(), CsvHeader.NO_HEADER, "header", "value", false);
        } catch (IOException ioe) {
            throw new StageException(Errors.WAVE_01, ioe);
        }

        Iterator<Record> batchIterator = batch.getRecords();

        while (batchIterator.hasNext()) {
            Record record = batchIterator.next();
            try {
                gen.write(record);
            } catch (Exception e) {
                switch (getContext().getOnErrorRecord()) {
                    case DISCARD:
                        break;
                    case TO_ERROR:
                        getContext().toError(record, Errors.WAVE_01, e.toString());
                        break;
                    case STOP_PIPELINE:
                        throw new StageException(Errors.WAVE_01, e.toString());
                    default:
                        throw new IllegalStateException(
                                Utils.format("Unknown OnError value '{}'", getContext().getOnErrorRecord(), e)
                        );
                }
            }
        }

        try {
            gen.close();
        } catch (IOException ioe) {
            throw new StageException(Errors.WAVE_01, ioe);
        }

        SObject sobj = new SObject();
        sobj.setType("InsightsExternalDataPart");
        sobj.setField("DataFile", writer.toString().getBytes(StandardCharsets.UTF_8));
        sobj.setField("InsightsExternalDataId", datasetID);
        sobj.setField("PartNumber", partNumber);

        partNumber++;

        try {
            SaveResult[] results = connection.create(new SObject[]{sobj});
            for (SaveResult sv : results) {
                if (sv.isSuccess()) {
                    String rowId = sv.getId();
                    LOG.info("Success creating InsightsExternalDataPart: " + rowId);
                } else {
                    for (Error e : sv.getErrors()) {
                        throw new StageException(Errors.WAVE_01, e.getMessage());
                    }
                }
            }
        } catch (ConnectionException ce) {
            throw new StageException(Errors.WAVE_01, ce);
        }
    }

    private String getDataflowId() throws IOException {
        LOG.info("*** "+restEndpoint + dataflowList);

        String json = Request.Get(restEndpoint + dataflowList)
                .addHeader("Authorization", "OAuth " + connection.getConfig().getSessionId())
                .execute()
                .returnContent()
                .asString();

        LOG.info("Got dataflow list: {}", json);

        ObjectMapper mapper = new ObjectMapper();
        DataflowList dataflows = mapper.readValue(json, DataflowList.class);

        for (int i = 0; i < dataflows.result.size(); i++) {
            Result r = dataflows.result.get(i);
            if (r.name.equals(getDataflowName())) {
                return r._uid;
            }

        }

        return null;
    }

    private String getDataflowJson(String dataflowId) throws IOException {
        // Add the dataset to the dataflow
        return Request.Get(String.format(restEndpoint + dataflowJson, dataflowId))
                .addHeader("Authorization", "OAuth " + connection.getConfig().getSessionId())
                .execute()
                .returnContent()
                .asString();
    }

    private void putDataflowJson(String dataflowId, String payload) throws IOException {
        try {
            HttpResponse res = Request.Patch(restEndpoint + String.format(dataflowJson, dataflowId))
                    .addHeader("Authorization", "OAuth " + connection.getConfig().getSessionId())
                    .bodyString(payload, ContentType.APPLICATION_JSON)
                    .execute()
                    .returnResponse();

            int statusCode = res.getStatusLine().getStatusCode();
            String content = EntityUtils.toString(res.getEntity());

            LOG.info("PATCH dataflow with result {} content {}", statusCode, content);
        } catch (HttpResponseException e) {
            LOG.error("PATCH dataflow with result {} {}", e.getStatusCode(), e.getMessage());
            throw e;
        }
    }

    private void runDataflow(String dataflowId) throws IOException {
        try {
            HttpResponse res = Request.Put(restEndpoint + String.format(startDataflow, dataflowId))
                    .addHeader("Authorization", "OAuth " + connection.getConfig().getSessionId())
                    .execute()
                    .returnResponse();

            int statusCode = res.getStatusLine().getStatusCode();
            String content = EntityUtils.toString(res.getEntity());

            LOG.info("PUT dataflow with result {} content {}", statusCode, content);
        } catch (HttpResponseException e) {
            LOG.error("PUT dataflow with result {} {}", e.getStatusCode(), e.getMessage());
            throw e;
        }
    }

    private String addDatasetToDataflow(String dataflowJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> dataflow = mapper.readValue(dataflowJson, new TypeReference<Map<String, Object>>() {
        });

        List<Object> result = (List<Object>) dataflow.get("result");
        Map<String, Object> workflow = null;
        for (int i = 0; i < result.size(); i++) {
            Map<String, Object> w = (Map<String, Object>) result.get(i);
            if (w.get("name").equals(getDataflowName())) {
                workflow = w;
                break;
            }
        }
        if (workflow == null) {
            LOG.info("Can't find dataflow " + getDataflowName());
            return null;
        }
        // TODO - create dataflow!

        Map<String, Object> workflowDefinition = (Map<String, Object>) workflow.get("workflowDefinition");

        String appendJob = "Append_" + getEdgemartAliasPrefix();
        Map<String, Object> append = (Map<String, Object>) workflowDefinition.get(appendJob);

        if (append == null) {
            LOG.info("Can't find {} in dataflow {}", appendJob, getDataflowName());

            // Clear out dataflow and create bits we need
            workflowDefinition = new HashMap<String, Object>();

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("sources", new ArrayList<String>());

            append = new HashMap<String, Object>();
            append.put("action", "append");
            append.put("parameters", parameters);

            workflowDefinition.put(appendJob, append);

            parameters = new HashMap<String, Object>();
            parameters.put("alias", getEdgemartAliasPrefix());
            parameters.put("name", getEdgemartAliasPrefix());
            parameters.put("source", appendJob);

            Map<String, Object> register = new HashMap<String, Object>();
            register.put("action", "sfdcRegister");
            register.put("parameters", parameters);

            workflowDefinition.put("Register_" + getEdgemartAliasPrefix(), register);
        }

        // Add new 'extract' source
        String extractSource = "Extract_" + datasetName;
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("alias", datasetName);
        Map<String, Object> extractDataset = new HashMap<String, Object>();
        extractDataset.put("action", "edgemart");
        extractDataset.put("parameters", parameters);
        workflowDefinition.put(extractSource, extractDataset);

        // Add 'extract' to 'append' job
        Map<String, Object> parameters2 = (Map<String, Object>) append.get("parameters");
        List<String> sources = (List<String>) parameters2.get("sources");
        sources.add(extractSource);

        // Make upload map
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("workflowDefinition", workflowDefinition);

        return mapper.writeValueAsString(map);
    }

    private void closeDataset() throws StageException, ConnectionException, IOException {
        // Instruct Wave to start processing the data
        SObject sobj = new SObject();
        sobj.setType("InsightsExternalData");
        sobj.setField("Action", "Process");
        sobj.setId(datasetID);

        SaveResult[] results = connection.update(new SObject[]{sobj});
        for (SaveResult sv : results) {
            if (sv.isSuccess()) {
                String rowId = sv.getId();
                LOG.info("Success updating InsightsExternalData: {}", rowId);
            } else {
                for (Error e : sv.getErrors()) {
                    throw new StageException(Errors.WAVE_01, e.getMessage());
                }
            }
        }

        if (getUseDataflow()) {
            // Poll until the dataset has been processed
            boolean done = false;
            int sleepTime = 1000;
            while (!done) {
                try {
                    Thread.sleep(sleepTime);
                    sleepTime *= 2;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                QueryResult queryResults = connection.query(
                        "SELECT Status, StatusMessage FROM InsightsExternalData WHERE Id = '" + datasetID + "'"
                );
                if (queryResults.getSize() > 0) {
                    for (SObject s : queryResults.getRecords()) {
                        String status = (String) s.getField("Status");
                        LOG.info("Dataset status is {}", status);
                        if (DONE.contains(status)) {
                            done = true;
                            String statusMessage = (String) s.getField("StatusMessage");
                            if (statusMessage != null) {
                                LOG.info("Dataset status message is {}", statusMessage);
                            }
                        }
                    }
                } else {
                    System.out.println("Can't find InsightsExternalData with Id " + datasetID);
                }
            }

            // Add the dataset to the dataflow
            ConnectorConfig config = connection.getConfig();
            String dataflowId = getDataflowId();
            String dataflowJson = getDataflowJson(dataflowId);

            LOG.info("Got dataflow json: {}", dataflowJson);

            String newDataflowJson = addDatasetToDataflow(dataflowJson);

            LOG.info("Uploading dataflow {}", newDataflowJson);

            putDataflowJson(dataflowId, newDataflowJson);

            if (getRunDataflow()) {
                LOG.info("Running dataflow {}", dataflowId);
                runDataflow(dataflowId);
            }
        }

        datasetID = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<ConfigIssue> init() {
        // Validate configuration values and open any required resources.
        List<ConfigIssue> issues = super.init();

        try {
            connection = Connector.newConnection(getConnectorConfig());
            LOG.info("Successfully authenticated as {}", getUsername());
        } catch (ConnectionException ce) {
            issues.add(
                    getContext().createConfigIssue(
                            Groups.WAVE.name(), "connectorConfig", Errors.WAVE_00, ce.getMessage()
                    )
            );
        }

        String soapEndpoint = connection.getConfig().getServiceEndpoint();
        restEndpoint = soapEndpoint.substring(0, soapEndpoint.indexOf("services/Soap/"));

        // If issues is not empty, the UI will inform the user of each configuration issue in the list.
        return issues;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        LOG.info("In WaveAnalyticsTarget destroy(), datasetID is " + datasetID);
        if (datasetID != null) {
            try {
                closeDataset();
            } catch (Exception e) {
                LOG.error("Exception updating InsightsExternalData", e);
            }
        }

        super.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Batch batch) throws StageException {
        if (! batch.getRecords().hasNext()) {
            LOG.info("Empty batch");
            if (System.currentTimeMillis() > (lastBatchTime + (getDatasetWaitTime() * 1000L))
                    && datasetID != null) {
                // We've waited datasetWaitTime since the last batch
                LOG.info("Closing dataset");
                try {
                    closeDataset();
                } catch (Exception e) {
                    throw new StageException(Errors.WAVE_01, e);
                }
            }
        } else {
            if (datasetID == null) {
                LOG.info("Opening dataset");
                try {
                    openDataset();
                } catch (ConnectionException ce) {
                    throw new StageException(Errors.WAVE_01, ce);
                }
            }

            LOG.info("Writing batch to dataset");
            writeToDataset(batch);

            lastBatchTime = System.currentTimeMillis();
        }
    }
}
