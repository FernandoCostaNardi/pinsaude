package br.com.pinsaude.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClicksignWebhookPayload(
    @JsonProperty("event") Event event
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
        @JsonProperty("name") String name,
        @JsonProperty("data") Data data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
        @JsonProperty("document") Document document
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
        @JsonProperty("key") String key,
        @JsonProperty("status") String status
    ) {}
}
