package com.cultural.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SearchRecoveryTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path cacheDirectory;
    @Test void restartRetainsRealSourcesAndMarksOutageFallbackWithoutRefreshingTimestamp() {
        var now = new AtomicLong(1000);
        var first = new WebSearchService("", "google", url -> url.contains("wikipedia")
                ? new WebSearchService.Page(200, WIKI, "") : new WebSearchService.Page(202, "", ""), now::get);
        var file = cacheDirectory.resolve("search.json");
        first.enablePersistence(file);
        var original = first.search("南京云锦");
        now.addAndGet(600_001);
        var restarted = new WebSearchService("", "google", url -> {throw new java.net.http.HttpTimeoutException("offline");}, now::get);
        restarted.enablePersistence(file);
        var recovered = restarted.search("南京云锦 纹样种类");
        assertFalse(recovered.isEmpty());
        assertTrue(recovered.get(0).path("stale").asBoolean());
        assertTrue(recovered.get(0).path("searchNotice").asText().contains("主题背景"));
        assertEquals(original.get(0).path("retrievedAt"), recovered.get(0).path("retrievedAt"));
        assertThrows(AgentException.class, () -> restarted.search("无关实体"));
        now.addAndGet(24 * 60 * 60_000L);
        assertThrows(AgentException.class, () -> restarted.search("南京云锦"));
    }
    private static final String WIKI = """
            {"query":{"search":[
              {"title":"云锦","snippet":"南京<span class='searchmatch'>云锦</span>织造工艺"},
              {"title":"南京云锦博物馆","snippet":"展示织造技艺"},
              {"title":"云锦路站 (南京市)","snippet":"地铁交通"},
              {"title":"南京市","snippet":"城市概述"},
              {"title":"不相关页面","snippet":"随机文本"}]}}
            """;
    @Test void captchaFallsBackToRealApiAndCachesDefensivelyWithExpiry() {
        var calls = new ArrayList<String>();
        var now = new AtomicLong(1000);
        var search = new WebSearchService("", "google", url -> {
            calls.add(url);
            if (url.contains("duckduckgo")) return new WebSearchService.Page(202, "verification", "");
            if (url.contains("sogou")) return new WebSearchService.Page(302, "", "https://www.sogou.com/captcha/");
            assertTrue(url.startsWith("https://zh.wikipedia.org/w/api.php?"));
            return new WebSearchService.Page(200, WIKI, "");
        }, now::get);
        var result = search.search("南京云锦纹样");
        assertEquals(2, result.size()); assertEquals("云锦", result.get(0).path("title").asText());
        assertEquals("南京云锦", result.get(0).path("searchQuery").asText());
        assertEquals("南京云锦织造工艺", result.get(0).path("description").asText());
        assertFalse(result.get(0).path("cached").asBoolean());
        assertTrue(result.get(0).has("searchNotice"));
        ((ObjectNode) result.get(0)).put("title", "mutated");
        var cached = search.search("南京云锦纹样");
        assertEquals(3, calls.size()); assertEquals("云锦", cached.get(0).path("title").asText());
        assertTrue(cached.get(0).path("cached").asBoolean());
        search.search("南京云锦资料");
        assertEquals(3, calls.size(), "rephrased questions share cached topic results");
        now.addAndGet(600_001);
        search.search("南京云锦纹样"); assertEquals(6, calls.size(), "cache expires and providers recover after cooldown");
    }
    @Test void allFailedReportsSpecificReasonsWithoutLeakingCredentialOrInventingResults() {
        var calls = new ArrayList<String>();
        var search = new WebSearchService("secret-test-key", "google", url -> {
            calls.add(url);
            if (url.contains("serpapi")) return new WebSearchService.Page(401, "secret-test-key", "");
            if (url.contains("duckduckgo")) return new WebSearchService.Page(202, "", "");
            if (url.contains("sogou")) return new WebSearchService.Page(429, "", "");
            throw new java.net.http.HttpTimeoutException("secret-test-key");
        }, () -> 1000);
        var error = assertThrows(AgentException.class, () -> search.search("云龙山"));
        assertTrue(error.getMessage().contains("需要网站验证"));
        assertTrue(error.getMessage().contains("请求频率受限"));
        assertTrue(error.getMessage().contains("连接超时"));
        assertFalse(error.getMessage().contains("secret-test-key"));
        assertThrows(AgentException.class, () -> search.search("放鹤亭"));
        assertEquals(5, calls.size(), "one bounded retry for the encyclopedia connection timeout");
    }
    @Test void detectsHttp200VerificationAndKeepsOriginalQueryWhenReductionIsEmpty() {
        assertEquals("需要网站验证", WebSearchService.failureReason(new WebSearchService.Page(200, "<title>搜狗安全验证</title>", "")));
        assertEquals("徐州云龙山", WebSearchService.encyclopediaQuery("徐州云龙山 中国风海报"));
        assertEquals("纹样", WebSearchService.encyclopediaQuery("纹样"));
    }
    @Test void rejectsAnotherMountainThatOnlySharesGenericWords() throws Exception {
        var search = new WebSearchService("", "google");
        assertTrue(search.parseEncyclopedia("{\"query\":{\"search\":[{\"title\":\"乐山大佛\",\"snippet\":\"四川佛像\"}]}}", "云龙山大佛", "云龙山大佛").isEmpty());
        assertTrue(search.parseEncyclopedia("{\"query\":{\"search\":[{\"title\":\"团花\",\"snippet\":\"茜草科植物\"}]}}", "云锦图案 团花 缠枝 云纹", "云锦 团花 缠枝 云纹").isEmpty());
    }
    @Test void relaxesActualModelQueryAndLabelsBroaderEvidence() {
        List<String> queries = new ArrayList<>();
        var search = new WebSearchService("", "google", url -> {
            if (!url.contains("wikipedia")) return new WebSearchService.Page(202, "", "");
            String query = java.net.URLDecoder.decode(url.split("srsearch=", 2)[1], java.nio.charset.StandardCharsets.UTF_8);
            queries.add(query);
            return new WebSearchService.Page(200, query.equals("南京云锦") ? WIKI : "{\"query\":{\"search\":[]}}", "");
        }, () -> 1000);
        var results = search.search("南京云锦 传统纹样 种类");
        assertEquals(List.of("南京云锦 传统 种类", "南京云锦"), queries);
        assertEquals(2, results.size());
        assertEquals("topic-overview", results.get(0).path("retrievalScope").asText());
        assertEquals("南京云锦", results.get(0).path("searchQuery").asText());
        assertEquals("南京云锦 传统纹样 种类", results.get(0).path("query").asText());
    }
    @Test void successfulEmptySearchIsNotReportedAsNetworkFailure() {
        var search = new WebSearchService("", "google", url -> url.contains("wikipedia")
                ? new WebSearchService.Page(200, "{\"query\":{\"search\":[]}}", "")
                : new WebSearchService.Page(202, "", ""), () -> 1000);
        assertTrue(search.search("不存在的文化实体").isEmpty());
    }
    @Test void rephrasedQueryUsesCachedTopicEvenWhileApiIsRateLimited() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var search = new WebSearchService("", "google", url -> {
            if (!url.contains("wikipedia")) return new WebSearchService.Page(202, "", "");
            calls.incrementAndGet();
            String query = java.net.URLDecoder.decode(url.split("srsearch=", 2)[1], java.nio.charset.StandardCharsets.UTF_8);
            return query.equals("南京云锦") ? new WebSearchService.Page(200, WIKI, "") : new WebSearchService.Page(429, "", "");
        }, () -> 1000);
        assertEquals(2, search.search("南京云锦").size());
        var sources = search.search("南京云锦 吉祥纹样 包装应用");
        assertEquals(2, sources.size());
        assertTrue(sources.get(0).path("cached").asBoolean());
        assertEquals("南京云锦 吉祥纹样 包装应用", sources.get(0).path("query").asText());
        assertEquals(2, calls.get());
    }
    @Test void transientConnectionRetriesOnceAndSingleRelevantResultIsKept() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var search = new WebSearchService("", "google", url -> {
            if (!url.contains("wikipedia")) return new WebSearchService.Page(202, "", "");
            if (attempts.incrementAndGet() == 1) throw new java.net.http.HttpTimeoutException("test timeout");
            return new WebSearchService.Page(200, "{\"query\":{\"search\":[{\"title\":\"云龙湖\",\"snippet\":\"徐州湖泊\"}]}}", "");
        }, () -> 1000);
        var result = search.search("云龙湖 湖面面积 景区等级");
        assertEquals(1, result.size());
        assertEquals(2, attempts.get());
    }
    @Test void geographicSearchExcludesSameNamePlacesInOtherRegions() throws Exception {
        var search = new WebSearchService("", "google");
        var sources = search.parseEncyclopedia("""
                {"query":{"search":[
                  {"title":"云龙湖","snippet":"位于江苏徐州的湖泊"},
                  {"title":"云龙湖水库","snippet":"位于辽宁省"},
                  {"title":"云龙湖大桥","snippet":"位于陕西延安"}]}}
                """, "云龙湖", "云龙湖");
        assertEquals(1, sources.size());
        assertEquals("云龙湖", sources.get(0).path("title").asText());
    }
    @Test void transientFailureRecoversSoonAndOldCacheIsExplicitlyLabelled() {
        var now = new AtomicLong(1000);
        var failing = new java.util.concurrent.atomic.AtomicBoolean();
        var search = new WebSearchService("", "google", url -> {
            if (!url.contains("wikipedia")) return new WebSearchService.Page(202, "", "");
            if (failing.get()) throw new java.net.http.HttpTimeoutException("test timeout");
            return new WebSearchService.Page(200, WIKI, "");
        }, now::get);
        var first = search.search("云锦");
        now.addAndGet(600_001); failing.set(true);
        var retained = search.search("云锦");
        assertTrue(retained.get(0).path("stale").asBoolean());
        assertEquals(first.get(0).path("retrievedAt"), retained.get(0).path("retrievedAt"));
        failing.set(false); now.addAndGet(10_001);
        assertFalse(search.search("云锦").get(0).path("stale").asBoolean());
    }
}
