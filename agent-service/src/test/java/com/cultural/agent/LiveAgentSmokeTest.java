package com.cultural.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.model.ChatModel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in only: calls configured DeepSeek and public search; never generates a paid image. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiveAgentSmokeTest {
    @Autowired ChatModel model;
    @Autowired AgentSettings settings;
    @Autowired WebSearchService search;
    @ParameterizedTest
    @ValueSource(strings = {"南京云锦有哪些适合包装设计的纹样？", "设计一个徐州云龙山主题的中国风海报，只需文字方案。"})
    void liveModelCallsRealSearchAndStreamsAnswer(String topic) throws Exception {
        ProviderClient backend = mock(ProviderClient.class);
        when(backend.backend(anyString(), anyString())).thenReturn(new ObjectMapper().createArrayNode());
        SessionStore store = new SessionStore(settings);
        WebSearchService observedSearch = spy(search);
        doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            System.out.println("LIVE MODEL SEARCH QUERY: " + query);
            return invocation.callRealMethod();
        }).when(observedSearch).search(anyString());
        AgentEngine engine = new AgentEngine(model, settings, store, backend, mock(ImageService.class), observedSearch);
        var deltas = new AtomicInteger();
        List<String> toolNames = new ArrayList<>();
        long start = System.nanoTime();
        long[] first = {0};
        var answer = engine.run(store.open(987654321, null), "test-local-backend", UUID.randomUUID().toString(),
                topic, false,
                (event, data) -> {
                    if (event.equals("delta")) { deltas.incrementAndGet(); if (first[0] == 0) first[0] = System.nanoTime(); }
                    if (event.equals("tool")) toolNames.add(((Map<?,?>) data).get("name").toString());
                });
        assertTrue(toolNames.contains("searchWeb"));
        assertTrue(answer.sources().stream().anyMatch(s -> s.path("type").asText().equals("web")));
        assertTrue(deltas.get() > 1);
        assertFalse(answer.content().isBlank());
        assertFalse(answer.content().contains("全部失败"));
        assertTrue(answer.content().contains("https://") || answer.content().contains("http://"), "Answer must cite actual returned URLs");
        System.out.printf("LIVE PASS: sources=%d deltas=%d firstTextMs=%d totalMs=%d%n", answer.sources().size(), deltas.get(),
                (first[0]-start)/1_000_000, (System.nanoTime()-start)/1_000_000);
        answer.sources().forEach(s -> System.out.println(s.path("engine").asText() + " | " + s.path("title").asText() + " | " + s.path("url").asText()));
    }
    @Test void lakeQuestionWithFailedFollowupStillCitesRealSearch() throws Exception {
        ProviderClient backend = mock(ProviderClient.class);
        when(backend.backend(anyString(), anyString())).thenReturn(new ObjectMapper().createArrayNode());
        var observed = spy(search);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() > 1) throw new AgentException(502, "连接超时（回归测试注入）");
            return invocation.callRealMethod();
        }).when(observed).search(anyString());
        var store = new SessionStore(settings);
        var engine = new AgentEngine(model, settings, store, backend, mock(ImageService.class), observed);
        var answer = engine.run(store.open(987654320, null), "test-local-backend", UUID.randomUUID().toString(),
                "介绍徐州云龙湖的景观与历史文化，先搜索云龙湖，再补充搜索景区等级和湖面面积，引用实际来源，无法核实的数字不写。", false, (e,d) -> {});
        assertTrue(attempts.get() >= 2, "exercise the failed follow-up search");
        assertTrue(answer.sources().stream().anyMatch(s -> s.path("title").asText().contains("云龙湖")));
        assertTrue(answer.content().contains("https://") || answer.content().contains("http://"));
        assertFalse(answer.content().matches("(?s).*(本轮联网检索服务出现故障|全部联网失败|所有.*入口.*失败).*"));
        System.out.println("LIVE LAKE PARTIAL RECOVERY: " + answer.content());
    }
}
