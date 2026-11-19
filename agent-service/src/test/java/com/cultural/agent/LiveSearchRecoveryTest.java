package com.cultural.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in: real public HTTP search, no model/image billing. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_SEARCH", matches = "true")
class LiveSearchRecoveryTest {
    @Test void screenshotQueriesReturnRealRelevantSources() {
        var search = new WebSearchService("", "google");
        for (String query : new String[]{"云龙湖", "云龙湖 湖面面积 景区等级 历史文化", "南京云锦 传统纹样 种类", "徐州云龙山 历史 文化"}) {
            var sources = search.search(query);
            assertFalse(sources.isEmpty(), "Need relevant sources for " + query);
            String topic = query.contains("云锦") ? "云锦" : query.contains("云龙湖") ? "云龙湖" : "云龙山";
            assertTrue(sources.stream().anyMatch(s -> s.path("title").asText().contains(topic)));
            sources.forEach(s -> System.out.println("LIVE SEARCH | " + query + " | " + s.path("engine").asText() + " | " + s.path("title").asText() + " | " + s.path("url").asText()));
            assertTrue(search.search(query).get(0).path("cached").asBoolean());
        }
    }
}
