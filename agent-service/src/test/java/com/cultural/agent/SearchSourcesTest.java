package com.cultural.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchSourcesTest {
    final ObjectMapper json = new ObjectMapper();
    @Test void identicalLocalRecordsReturnOneCanonicalCitationAndPreserveDetail() throws Exception {
        var client = mock(ProviderClient.class);
        when(client.backend(anyString(), eq("Bearer test"))).thenReturn(json.readTree("""
                [{"id":2,"type":"heritage","title":"南京云锦","description":"丝织工艺","city":"南京"},
                 {"id":12,"type":"heritage","title":"南京云锦","description":"丝织工艺","city":"南京"},
                 {"id":22,"type":"heritage","title":"云锦纹样","description":"纹样寓意","city":"南京"}]
                """));
        var tools = new CultureTools(client, mock(ImageService.class), mock(WebSearchService.class), "Bearer test", 1, "test", false, (e,d) -> {});
        var result = json.readTree(tools.searchCulture("云锦", "all", ""));
        assertEquals(2, result.size());
        assertEquals("heritage:2", result.get(0).path("citation").asText());
        when(client.backend("/agent/culture/heritage/12", "Bearer test")).thenReturn(json.readTree("""
                {"id":12,"type":"heritage","title":"南京云锦","description":"丝织工艺","city":"南京","content":"详细工艺"}
                """));
        assertEquals(2, json.readTree(tools.getCultureDetail("heritage", 12)).path("id").asInt());
        assertEquals(2, tools.sources.size());
        assertEquals("详细工艺", tools.sources.get("heritage:2").path("content").asText());
    }
    @Test void parsesRealSearchMarkupAndRejectsUnsafeLinksAndChallengePages() {
        var search = new WebSearchService("", "google");
        var result = search.parseHtml("""
                <div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fyunjin">云锦纹样</a><a class="result__snippet">织造文化</a></div>
                <div class="result"><a class="result__a" href="https://example.org/yunjin#section">重复页</a></div>
                <div class="result"><a class="result__a" href="javascript:alert(1)">恶意链接</a></div>
                """, "DuckDuckGo", "云锦");
        assertEquals(1, result.size());
        assertEquals("https://example.org/yunjin", result.get(0).path("url").asText());
        assertEquals("织造文化", result.get(0).path("description").asText());
        assertEquals("search-snippet", result.get(0).path("sourceKind").asText());
        assertTrue(search.parseHtml("<title>安全验证</title>", "搜狗", "云锦").isEmpty());
    }
    @Test void exposesActualWebSourcesThroughTool() throws Exception {
        var search = mock(WebSearchService.class);
        when(search.search("云锦")).thenReturn(List.of(json.readTree("""
                {"type":"web","id":"a","title":"云锦织造","url":"https://example.org/yunjin","description":"独立来源"}
                """)));
        List<String> events = new ArrayList<>();
        var tools = new CultureTools(mock(ProviderClient.class), mock(ImageService.class), search, "token", 1, "test", false, (e,d) -> events.add(e));
        assertEquals("web:a", json.readTree(tools.searchWeb("云锦")).get(0).path("citation").asText());
        verify(search).search("云锦"); assertTrue(events.contains("source"));
        tools.searchWeb("云锦");
        var stopped = json.readTree(tools.searchWeb("云锦 其他纹样"));
        assertEquals("search_budget_reached", stopped.path("status").asText());
        assertEquals(1, stopped.path("sources").size());
        verify(search, times(2)).search(anyString());
    }
    @Test void failedFollowupKeepsEarlierSourcesAndCancellationStillPropagates() throws Exception {
        var search = mock(WebSearchService.class);
        when(search.search("云龙湖")).thenReturn(List.of(json.readTree("{\"type\":\"web\",\"id\":\"lake\",\"title\":\"云龙湖\",\"url\":\"https://example.org/lake\"}")));
        when(search.search("云龙湖 面积")).thenThrow(new AgentException(502, "连接超时"));
        var tools = new CultureTools(mock(ProviderClient.class), mock(ImageService.class), search, "token", 1, "test", false, (e,d) -> {});
        tools.searchWeb("云龙湖");
        var response = json.readTree(tools.searchWeb("云龙湖 面积"));
        assertEquals("partial_results", response.path("status").asText());
        assertEquals("web:lake", response.path("sources").get(0).path("citation").asText());
        var empty = new CultureTools(mock(ProviderClient.class), mock(ImageService.class), search, "token", 1, "test", false, (e,d) -> {});
        assertEquals("unavailable", json.readTree(empty.searchWeb("云龙湖 面积")).path("status").asText());
        when(search.search("取消")).thenThrow(new AgentException(499, "请求已取消"));
        assertThrows(AgentException.class, () -> empty.searchWeb("取消"));
    }
}
