package com.cultural.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Validated
@ConfigurationProperties("agent")
public record AgentSettings(
        @NotBlank String backendUrl, String deepseekKey, @NotBlank String deepseekUrl,
        @NotBlank String model, boolean thinking, String imageKey, @NotBlank String imageUrl,
        @NotBlank String imageModel, @Min(1) @Max(12) int maxRounds,
        @Min(1) @Max(30) int maxToolCalls, @Min(128) @Max(16384) int maxOutputTokens,
        @Min(4000) @Max(500000) int maxContextChars, @Min(10) @Max(300) int timeoutSeconds,
        @Min(1) @Max(1440) int sessionTtlMinutes, @Min(1) @Max(10000) int maxSessions,
        @Min(1) @Max(100) int requestsPerMinute) {
    public boolean modelReady() { return deepseekKey != null && !deepseekKey.isBlank(); }
    public boolean imageReady() { return imageKey != null && !imageKey.isBlank(); }
}
