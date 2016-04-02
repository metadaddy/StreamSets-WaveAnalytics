/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.stage.destination.waveanalytics;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.StageDef;

@StageDef(
    version = 1,
    label = "Wave Analytics",
    description = "",
    icon = "waveanalytics.png",
    recordsByRef = true,
    onlineHelpRefUrl = ""
)

@ConfigGroups(value = Groups.class)
@GenerateResourceBundle
public class WaveAnalyticsDTarget extends WaveAnalyticsTarget {

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "user@example.com",
      label = "Username",
      description = "Salesforce username",
      displayPosition = 10,
      group = "WAVE"
  )
  public String username;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "${runtime:loadResource('wavePassword.txt',true)}",
      label = "Password",
      description = "Salesforce password",
      displayPosition = 20,
      group = "WAVE"
  )
  public String password;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Edgemart Alias",
      description = "The alias of a dataset, which must be unique across an organization.",
      displayPosition = 30,
      group = "WAVE"
  )
  public String edgemartAliasPrefix;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.NUMBER,
          defaultValue = "0",
          label = "Dataset Wait Time (secs)",
          description = "Max time to wait for new data before requesting that the dataset be processed.",
          displayPosition = 40,
          group = "WAVE"
  )
  public int datasetWaitTime = 0;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.BOOLEAN,
          defaultValue = "false",
          label = "Use Dataflow",
          description = "Whether or not to use a dataflow to combine datasets into one.",
          displayPosition = 50,
          group = "WAVE"
  )
  public boolean useDataflow;

  @ConfigDef(
          required = false,
          type = ConfigDef.Type.STRING,
          defaultValue = "SalesEdgeEltWorkflow",
          label = "Dataflow Name",
          description = "Name of a dataflow to combine datasets into one. CAUTION - existing dataflow content will be overwritten!",
          displayPosition = 60,
          dependsOn = "useDataflow",
          triggeredByValue = "true",
          group = "WAVE"
  )
  public String dataflowName;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.BOOLEAN,
          defaultValue = "false",
          label = "Run Dataflow After Upload",
          description = "Enable this to run the dataflow after each dataset is uploaded. Caution - ensure that your Dataset Wait Time is at least an hour or you will overrun the limit on dataflow runs!",
          displayPosition = 70,
          dependsOn = "useDataflow",
          triggeredByValue = "true",
          group = "WAVE"
  )
  public boolean runDataflow = false;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.TEXT,
      defaultValue = "",
      label = "Metadata JSON",
      description = "Metadata in JSON format, which describes the structure of the uploaded file.",
      displayPosition = 80,
      group = "WAVE"
  )
  public String metadataJson = "";

  /** {@inheritDoc} */
  @Override
  public String getMetadataJson() {
    return metadataJson;
  }

  /** {@inheritDoc} */
  public String getEdgemartAliasPrefix() {
    return edgemartAliasPrefix;
  }

  /** {@inheritDoc} */
  @Override
  public String getUsername() {
    return username;
  }

  /** {@inheritDoc} */
  @Override
  public String getPassword() {
    return password;
  }

  /** {@inheritDoc} */
  @Override
  public int getDatasetWaitTime() { return datasetWaitTime; }

  /** {@inheritDoc} */
  @Override
  public boolean getUseDataflow() { return useDataflow; }

  /** {@inheritDoc} */
  @Override
  public String getDataflowName() { return dataflowName; }

  /** {@inheritDoc} */
  @Override
  public boolean getRunDataflow() { return runDataflow; }
}
