package com.cultural.agent;

import org.springframework.context.annotation.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.RetryPolicy;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ModelConfig {
    @Bean
    ChatModel chatModel(AgentSettings settings) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(45));
        var api = DeepSeekApi.builder().baseUrl(settings.deepseekUrl())
                .apiKey(settings.modelReady() ? settings.deepseekKey() : "not-configured")
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        return DeepSeekChatModel.builder().deepSeekApi(api)
                .options(DeepSeekChatOptions.builder().model(settings.model()).maxTokens(settings.maxOutputTokens()).build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
    }
}
