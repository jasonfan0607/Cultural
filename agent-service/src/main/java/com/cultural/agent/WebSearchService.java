package com.cultural.agent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.nio.file.*;

/** Fetch search results on demand. Never fetch model-supplied URLs or bypass search challenges. */
@Service
public class WebSearchService {
    private static final Logger LOG = LoggerFactory.getLogger(WebSearchService.class);
    private static final long CACHE_MS = 10 * 60_000L, STALE_MS = 24 * 60 * 60_000L, COOLDOWN_MS = 5 * 60_000L;
    private static final Set<String> CITY_PREFIXES = Set.of("南京", "苏州", "无锡", "常州", "镇江", "扬州", "泰州", "南通", "盐城", "淮安", "宿迁", "徐州", "连云港");
    record Page(int status, String body, String location) {}
    interface Transport { Page fetch(String url) throws Exception; }
    private record Cached(long time, List<JsonNode> sources) {}
    private record Blocked(long until, String reason) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Cached> encyclopediaCache = new ConcurrentHashMap<>();
    private final Map<String, Blocked> blocked = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey, engine;
    private final Transport transport;
    private final LongSupplier clock;
    private Path cacheFile;
    public WebSearchService(String apiKey, String engine) {
        this(apiKey, engine, network(), System::currentTimeMillis);
    }
    @Autowired
    public WebSearchService(@Value("${search.serpapi-key:}") String apiKey,
                            @Value("${search.engine:google}") String engine,
                            @Value("${search.cache-file:./data/search-cache.json}") String cacheFile) {
        this(apiKey, engine, network(), System::currentTimeMillis);
        enablePersistence(Path.of(cacheFile));
    }
    WebSearchService(String apiKey, String engine, Transport transport, LongSupplier clock) {
        this.apiKey = apiKey; this.engine = engine; this.transport = transport; this.clock = clock;
    }
    private static Transport network() {
        var http = new java.util.concurrent.atomic.AtomicReference<>(newHttpClient());
        var encyclopediaGate = new java.util.concurrent.Semaphore(1);
        long[] nextRequest = {0};
        return url -> {
            boolean encyclopedia = url.startsWith("https://zh.wikipedia.org/w/api.php?");
            if (encyclopedia) encyclopediaGate.acquire();
            try {
                if (encyclopedia) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(Math.max(0, nextRequest[0] - System.nanoTime()));
                var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "CulturalResearch/1.0 (educational cultural reference search)")
                        .GET().build();
                HttpResponse<String> response;
                try { response = http.get().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
                catch (java.io.IOException e) {
                    // Discard pooled connections after a network/VPN change; the bounded API retry uses a fresh client.
                    http.set(newHttpClient());
                    throw e;
                }
                return new Page(response.statusCode(), response.body(), response.headers().firstValue("location").orElse(""));
            } finally {
                if (encyclopedia) { nextRequest[0] = System.nanoTime() + 1_000_000_000L; encyclopediaGate.release(); }
            }
        };
    }
    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public List<JsonNode> search(String query) {
        if (query == null || query.isBlank() || query.length() > 160)
            throw new AgentException(400, "联网搜索词须为 1 至 160 字");
        query = query.trim().replaceAll("\\s+", " ");
        String cacheKey = query.toLowerCase(Locale.ROOT);
        long now = clock.getAsLong();
        Cached existing = cache.get(cacheKey);
        if (existing != null && now - existing.time < CACHE_MS) return copies(existing.sources, true);
        List<String> providers = new ArrayList<>();
        if (!apiKey.isBlank()) providers.add(engine.equals("baidu") ? "百度（SerpAPI）" : "Google（SerpAPI）");
        providers.addAll(List.of("DuckDuckGo", "搜狗", "维基百科"));
        List<String> failures = new ArrayList<>();
        boolean completedSearch = false;
        for (String provider : providers) {
            Blocked previous = blocked.get(provider);
            if (!provider.equals("维基百科") && previous != null && previous.until > now) { failures.add(provider + "：" + previous.reason + "（冷却中）"); continue; }
            try {
                if (provider.equals("维基百科")) {
                    List<JsonNode> results = new ArrayList<>();
                    Set<String> seen = new HashSet<>();
                    List<String> candidates = encyclopediaQueries(query);
                    for (String candidate : candidates) {
                        Cached known = encyclopediaCache.get(candidate);
                        List<JsonNode> found;
                        if (known != null && now - known.time < (known.sources.isEmpty() ? 60_000 : CACHE_MS)) {
                            found = copies(known.sources, true);
                            for (JsonNode source : found) ((ObjectNode) source).put("query", query);
                        } else {
                            Blocked current = blocked.get(provider);
                            if (current != null && current.until > clock.getAsLong()) {
                                failures.add(provider + "：" + current.reason + "（冷却中）");
                                continue; // A broader candidate may already be cached from another phrasing.
                            }
                            Page page = fetchEncyclopedia("https://zh.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=10&srsearch=" + encode(candidate));
                            String failure = failureReason(page);
                            if (failure != null) { unavailable(provider, failure); failures.add(provider + "：" + failure); continue; }
                            found = parseEncyclopedia(page.body, query, candidate);
                            encyclopediaCache.entrySet().removeIf(e -> now - e.getValue().time >= CACHE_MS);
                            if (encyclopediaCache.size() >= 200) encyclopediaCache.keySet().stream().findFirst().ifPresent(encyclopediaCache::remove);
                            encyclopediaCache.put(candidate, new Cached(now, copies(found, false)));
                            blocked.remove(provider);
                        }
                        completedSearch = true;
                        LOG.info("Web search provider={} query={} resultCount={}", provider, candidate, found.size());
                        if (found.size() >= 2) { results.clear(); seen.clear(); }
                        for (JsonNode source : found) {
                            if (!seen.add(source.path("url").asText())) continue;
                            ObjectNode data = (ObjectNode) source;
                            boolean broader = !candidate.equals(candidates.get(0));
                            data.put("retrievalScope", broader ? "topic-overview" : "query-match");
                            data.put("searchNotice", "通用网页搜索未返回可用结果，已使用维基百科公开检索 API。" +
                                    (broader ? "关键词已缩短为主题名称；这些资料支持主题背景，不代表已核实全部细节。" : "以下为百科搜索摘要。"));
                            results.add(data);
                        }
                        // One relevant source is useful; do not discard it by making an unnecessary follow-up request.
                        if (!results.isEmpty()) break;
                    }
                    if (!results.isEmpty()) return remember(cacheKey, results);
                    failures.add(provider + "：未找到相关结果");
                    continue;
                }
                String requestQuery = query;
                String url = switch (provider) {
                    case "DuckDuckGo" -> "https://html.duckduckgo.com/html/?q=" + encode(requestQuery);
                    case "搜狗" -> "https://www.sogou.com/web?query=" + encode(requestQuery);
                    default -> "https://serpapi.com/search.json?engine=" + (engine.equals("baidu") ? "baidu&wd=" : "google&q=") + encode(requestQuery) + "&api_key=" + encode(apiKey);
                };
                Page page = transport.fetch(url);
                String failure = failureReason(page);
                if (failure != null) { unavailable(provider, failure); failures.add(provider + "：" + failure); continue; }
                List<JsonNode> results = provider.contains("SerpAPI") ? parseApi(page.body, provider, query) : parseHtml(page.body, provider, query);
                completedSearch = true;
                if (results.isEmpty()) { failures.add(provider + "：未找到相关结果"); continue; }
                blocked.remove(provider);
                LOG.info("Web search provider={} resultCount={}", provider, results.size());
                return remember(cacheKey, results);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AgentException(499, "请求已取消"); }
            catch (Exception e) {
                String reason = e instanceof java.net.http.HttpTimeoutException ? "连接超时" : "连接或响应解析失败";
                LOG.warn("Web search provider={} exceptionType={}", provider, e.getClass().getSimpleName());
                unavailable(provider, reason); failures.add(provider + "：" + reason);
            }
        }
        // A successful API search with no matching articles is not a network outage.
        if (completedSearch) return List.of();
        Cached retained = existing;
        boolean topicFallback = false;
        if (retained == null || now - retained.time >= STALE_MS) {
            for (String candidate : encyclopediaQueries(query)) {
                Cached topic = encyclopediaCache.get(candidate);
                if (topic != null && !topic.sources.isEmpty() && now - topic.time < STALE_MS) {
                    retained = topic; topicFallback = true; break;
                }
            }
        }
        if (retained != null && now - retained.time < STALE_MS) {
            var saved = copies(retained.sources, true);
            for (JsonNode source : saved) {
                ((ObjectNode) source).put("query", query).put("stale", true).put("searchNotice",
                        "本次实时检索暂不可用，展示此前取得的" + (topicFallback ? "主题背景" : "同关键词")
                                + "网页资料；检索时间见 retrievedAt，不代表刚刚核实，不能据此补全未核实的细节。");
            }
            return saved;
        }
        throw new AgentException(502, "联网检索暂不可用（" + String.join("；", failures) + "）。不要连续换词重试受阻入口；可稍后重试或配置正式搜索 API。不得编造来源。");
    }
    private Page fetchEncyclopedia(String url) throws Exception {
        try { return transport.fetch(url); }
        catch (java.io.IOException transientFailure) {
            // Retry a transient connection failure once. Never retry a CAPTCHA, 403 or 429 response.
            LOG.info("Encyclopedia connection interrupted; retrying once ({})", transientFailure.getClass().getSimpleName());
            return transport.fetch(url);
        }
    }
    private List<JsonNode> remember(String key, List<JsonNode> results) {
        long now = clock.getAsLong();
        cache.entrySet().removeIf(e -> now - e.getValue().time >= STALE_MS);
        if (cache.size() >= 200) cache.keySet().stream().findFirst().ifPresent(cache::remove);
        results.forEach(source -> { if (!source.has("cached")) ((ObjectNode) source).put("cached", false); });
        cache.put(key, new Cached(now, copies(results, false)));
        persist();
        return results;
    }
    synchronized void enablePersistence(Path file) {
        cacheFile = file.toAbsolutePath();
        if (!Files.isRegularFile(cacheFile)) return;
        try {
            if (Files.size(cacheFile) > 8_000_000) return;
            JsonNode saved = json.readTree(Files.readString(cacheFile));
            restore(saved.path("queries"), cache);
            restore(saved.path("topics"), encyclopediaCache);
        } catch (Exception e) { LOG.warn("Search cache could not be read ({})", e.getClass().getSimpleName()); }
    }
    private void restore(JsonNode saved, Map<String, Cached> destination) {
        saved.fields().forEachRemaining(entry -> {
            if (destination.size() >= 200 || entry.getKey().length() > 160) return;
            long time = entry.getValue().path("time").asLong();
            long age = clock.getAsLong() - time;
            if (age < 0 || age >= STALE_MS) return;
            List<JsonNode> sources = new ArrayList<>();
            for (JsonNode source : entry.getValue().path("sources")) {
                if (sources.size() >= 5) break;
                if (source.isObject() && source.path("type").asText().equals("web")
                        && source.path("url").asText().matches("https?://[^\\s]+") && source.hasNonNull("retrievedAt")) sources.add(source);
            }
            if (!sources.isEmpty()) destination.put(entry.getKey(), new Cached(time, sources));
        });
    }
    private synchronized void persist() {
        if (cacheFile == null) return;
        Path temp = null;
        try {
            Files.createDirectories(cacheFile.getParent());
            ObjectNode root = json.createObjectNode();
            for (var pair : Map.of("queries", cache, "topics", encyclopediaCache).entrySet()) {
                ObjectNode entries = root.putObject(pair.getKey());
                pair.getValue().forEach((key, value) -> {
                    if (clock.getAsLong() - value.time >= STALE_MS || value.sources.isEmpty()) return;
                    ObjectNode item = entries.putObject(key).put("time", value.time);
                    item.set("sources", json.valueToTree(value.sources));
                });
            }
            temp = Files.createTempFile(cacheFile.getParent(), "search-cache-", ".tmp");
            Files.writeString(temp, json.writeValueAsString(root));
            try { Files.move(temp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { LOG.warn("Search cache could not be saved ({})", e.getClass().getSimpleName()); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (java.io.IOException ignored) {} }
    }
    private void unavailable(String provider, String reason) {
        long delay = reason.equals("需要网站验证") || reason.equals("请求频率受限") ? COOLDOWN_MS : 10_000L;
        blocked.put(provider, new Blocked(clock.getAsLong() + delay, reason));
        LOG.warn("Web search provider={} reason={} cooldownSeconds={}", provider, reason, delay / 1000);
    }
    static String failureReason(Page page) {
        String location = page.location.toLowerCase(Locale.ROOT);
        if (location.contains("captcha") || location.contains("challenge") || page.status == 202 || page.status == 403)
            return "需要网站验证";
        if (page.status == 429) return "请求频率受限";
        if (page.status != 200) return "HTTP " + page.status;
        if (page.body.length() > 2_000_000) return "响应过大";
        var doc = Jsoup.parse(page.body);
        if (doc.title().matches(".*(安全验证|验证码|访问验证).*") || !doc.select("form[action*=anomaly],#captcha,#anomaly-modal").isEmpty())
            return "需要网站验证";
        return null;
    }
    private static List<JsonNode> copies(List<JsonNode> sources, boolean cached) {
        return sources.stream().map(source -> (JsonNode) ((ObjectNode) source.deepCopy()).put("cached", cached)).toList();
    }
    static String encyclopediaQuery(String query) {
        // Remove design/output requirements, not cultural entities. Record the actual query on every source.
        String reduced = query.replaceAll("中国风|现代简约|包装设计|海报设计|设计方案|包装|海报|设计|纹样|图案|寓意|参考资料|资料|官方网站|官网|历史文化|历史|工艺|特点|介绍", " ")
                .replaceAll("[，,。；;：:\\[\\]\"']", " ").replaceAll("\\s+", " ").trim();
        return reduced.length() >= 2 ? reduced : query;
    }
    static List<String> encyclopediaQueries(String query) {
        String reduced = encyclopediaQuery(query);
        String[] terms = reduced.split("\\s+");
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(reduced);
        // General search accepts many descriptive words; encyclopedia search needs a topic name.
        // For separated city + subject tokens (南京 云锦), keep both before trying the subject alone.
        if (terms.length > 1) {
            candidates.add(CITY_PREFIXES.contains(terms[0]) && terms[1].length() >= 2 ? terms[0] + terms[1] : terms[0]);
        }
        return candidates.stream().filter(s -> s.length() >= 2).limit(3).toList();
    }
    List<JsonNode> parseEncyclopedia(String body, String original, String actual) throws Exception {
        JsonNode root = json.readTree(body);
        if (root.has("error")) throw new IllegalStateException("Encyclopedia API error");
        List<JsonNode> items = new ArrayList<>();
        root.path("query").path("search").forEach(items::add);
        items.sort(Comparator.comparingInt((JsonNode item) -> relevance(item.path("title").asText(), actual)).reversed());
        String[] originalTerms = encyclopediaQuery(original).split("\\s+");
        String primary = originalTerms.length > 1 && CITY_PREFIXES.contains(originalTerms[0]) && originalTerms[1].length() >= 2
                ? originalTerms[0] + originalTerms[1] : originalTerms[0];
        var exactTopics = items.stream().map(item -> item.path("title").asText())
                .filter(title -> title.length() >= 2 && primary.contains(title)).toList();
        // Same-name lakes/reservoirs in other provinces are not evidence about the requested destination.
        String geographicTopic = exactTopics.stream().filter(title -> title.matches(".*(湖|山|河|园|寺|陵|古镇|景区)$"))
                .max(Comparator.comparingInt(String::length)).orElse("");
        String topicCity = items.stream().filter(item -> item.path("title").asText().equals(geographicTopic))
                .flatMap(item -> CITY_PREFIXES.stream().filter(city -> item.path("snippet").asText().contains(city)))
                .findFirst().orElse("");
        List<JsonNode> results = new ArrayList<>();
        for (JsonNode item : items) {
            String title = item.path("title").asText();
            if (relevance(title, actual) < 2) continue;
            if (!geographicTopic.isEmpty() && !title.equals(geographicTopic)
                    && (topicCity.isEmpty() || !(title + Jsoup.parse(item.path("snippet").asText()).text()).contains(topicCity))) continue;
            if (!exactTopics.isEmpty() && exactTopics.stream().noneMatch(title::contains)) continue;
            if (exactTopics.isEmpty() && !(title + Jsoup.parse(item.path("snippet").asText()).text()).contains(primary)) continue;
            if (exactTopics.isEmpty() && Arrays.stream(actual.split("\\s+")).filter(term -> term.length() >= 2).noneMatch(title::contains)) continue;
            // A place named after the cultural subject is not evidence about that subject.
            if (!actual.matches(".*(站|铁路|交通|地铁).*") && title.matches(".*(路站|车站|地铁站|站)([ （(].*)?")) continue;
            int before = results.size();
            add(results, item.path("title").asText(), "https://zh.wikipedia.org/wiki/" + encode(item.path("title").asText()).replace("+", "%20"),
                    Jsoup.parse(item.path("snippet").asText()).text(), "维基百科", original);
            if (results.size() > before) ((ObjectNode) results.get(results.size()-1)).put("searchQuery", actual);
        }
        return results;
    }
    private static int relevance(String title, String query) {
        int longest = 0;
        for (int start = 0; start < title.length(); start++) for (int end = start + 2; end <= title.length(); end++)
            if (query.contains(title.substring(start, end))) longest = Math.max(longest, end-start);
        return longest + (longest == title.length() ? 10 : 0);
    }
    List<JsonNode> parseApi(String body, String provider, String query) throws Exception {
        JsonNode root = json.readTree(body);
        if (root.has("error")) throw new IllegalStateException("Search unavailable");
        List<JsonNode> results = new ArrayList<>();
        for (JsonNode item : root.path("organic_results")) add(results, item.path("title").asText(), item.path("link").asText(), item.path("snippet").asText(), provider, query);
        return results;
    }
    List<JsonNode> parseHtml(String html, String provider, String query) {
        var doc = Jsoup.parse(html);
        doc.select("script,style").remove();
        boolean duck = provider.equals("DuckDuckGo");
        List<JsonNode> results = new ArrayList<>();
        for (Element row : doc.select(duck ? ".result" : ".vrwrap,.rb")) {
            Element link = row.selectFirst(duck ? "a.result__a" : "h3 a,h2 a");
            if (link == null) continue;
            String url = link.attr("href");
            if (duck && url.contains("uddg=")) {
                try { url = URLDecoder.decode(url.substring(url.indexOf("uddg=") + 5).split("&", 2)[0], StandardCharsets.UTF_8); }
                catch (IllegalArgumentException e) { continue; }
            }
            // Sogou redirect links are kept as labelled search redirect links. Do not follow arbitrary destinations.
            if (url.startsWith("/link?")) url = "https://www.sogou.com" + url;
            Element snippet = row.selectFirst(duck ? ".result__snippet" : ".str_info,.str_content,.ft");
            add(results, link.text(), url, snippet == null ? row.text() : snippet.text(), provider, query);
        }
        return results;
    }
    private void add(List<JsonNode> list, String title, String url, String excerpt, String provider, String query) {
        if (list.size() >= 5 || title.isBlank()) return;
        try {
            URI uri = URI.create(url);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return;
            String canonical = url.split("#", 2)[0];
            if (list.stream().anyMatch(s -> s.path("url").asText().equals(canonical) || s.path("title").asText().equals(title))) return;
            ObjectNode source = json.createObjectNode();
            source.put("type", "web").put("id", UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString())
                    .put("title", title).put("url", canonical).put("domain", uri.getHost()).put("engine", provider)
                    .put("description", excerpt.substring(0, Math.min(900, excerpt.length())))
                    .put("query", query).put("retrievedAt", Instant.now().toString()).put("sourceKind", "search-snippet");
            list.add(source);
        } catch (IllegalArgumentException ignored) { }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
