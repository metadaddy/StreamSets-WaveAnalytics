
package com.streamsets.stage.destination.waveanalytics;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;

@Generated("org.jsonschema2pojo")
public class Result {

    public Boolean isDeletable;
    public String WorkflowType;
    public String _type;
    public String name;
    public com.streamsets.stage.destination.waveanalytics.LastModifiedBy _lastModifiedBy;
    public Integer RefreshFrequencySec;
    public String MasterLabel;
    public String _url;
    public String _uid;
    public String WorkflowStatus;
    public String nextRun;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
