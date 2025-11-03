package com.mx.liverpool.automatizacionbackend.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class PaypalWebhookRequest {
    private String id;

    @JsonProperty("event_version")
    private String eventVersion;

    @JsonProperty("create_time")
    private String createTime;

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("event_type")
    private String eventType;

    private String summary;

    private Map<String, Object> resource;

    private Object[] links;
}
