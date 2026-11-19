package com.cultural.agent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Full HTTP -> Spring AI -> mock provider -> actual annotated tools -> SSE protocol tests. No paid API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpServer UPSTREAM;
    static final List<JsonNode> REQUESTS = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger IMAGE_CALLS = new AtomicInteger();
    static volatile CountDownLatch FIRST_DELTA = new CountDownLatch(1);
    static volatile CountDownLatch RELEASE_STREAM = new CountDownLatch(1);
    static {
        try {
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UPSTREAM.createContext("/", exchange -> {
                int status = 200;
                Object body;
                String path = exchange.getRequestURI().getPath();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (path.equals("/api/agent/identity")) {
                    if ("Bearer alice".equals(authorization)) body = Map.of("code", 200, "data", Map.of("userId", 1));
                    else if ("Bearer bob".equals(authorization)) body = Map.of("code", 200, "data", Map.of("userId", 2));
                    else { status = 401; body = Map.of("code", 401); }
                } else if (path.startsWith("/api/agent/culture")) {
                    body = Map.of("code", 200, "data", List.of(Map.of("id", 7, "type", "heritage", "title", "南京云锦", "description", "云锦资料")));
                } else if (path.equals("/images/generations")) {
                    IMAGE_CALLS.incrementAndGet(); body = Map.of("data", List.of(Map.of("b64_json", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")));
                } else if (path.equals("/chat/completions")) {
                    JsonNode request = JSON.readTree(exchange.getRequestBody()); REQUESTS.add(request);
                    ArrayNode messages = (ArrayNode) request.path("messages");
                    JsonNode last = messages.get(messages.size() - 1);
                    String text = last.path("content").asText();
                    if (text.equals("provider-failure")) { status = 429; body = Map.of("error", Map.of("message", "secret-provider-detail")); }
                    else {
                        boolean loop = messages.toString().contains("force-loop");
                        boolean useTool = loop || last.path("role").asText().equals("user");
                        if (request.path("stream").asBoolean()) {
                            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                            exchange.sendResponseHeaders(200, 0);
                            var out = exchange.getResponseBody();
                            chunk(out, Map.of("role", "assistant", "reasoning_content", useTool ? "provider-" : "final-"), "");
                            chunk(out, Map.of("reasoning_content", "reasoning"), "");
                            if (useTool) {
                                chunk(out, Map.of("tool_calls", List.of(Map.of("index", 0, "id", "call-" + messages.size(), "type", "function", "function",
                                        Map.of("name", "searchCulture", "arguments", "{\"keyword\":\"云")))), "");
                                chunk(out, Map.of("tool_calls", List.of(Map.of("index", 0, "function", Map.of("arguments", "锦\",\"type\":\"all\",\"city\":\"\"}")))), "");
                            } else {
                                chunk(out, Map.of("content", "参考南京云锦 "), "");
                                if (messages.toString().contains("slow-stream")) {
                                    FIRST_DELTA.countDown();
                                    try { RELEASE_STREAM.await(8, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                                }
                                chunk(out, Map.of("content", "[heritage:7] 的包装建议。"), "");
                            }
                            chunk(out, Map.of(), useTool ? "tool_calls" : "stop");
                            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                            exchange.close(); return;
                        }
                        Object message = useTool
                                ? Map.of("role", "assistant", "content", "", "reasoning_content", "provider-reasoning",
                                    "tool_calls", List.of(Map.of("id", "call-" + messages.size(), "type", "function", "function",
                                    Map.of("name", "searchCulture", "arguments", "{\"keyword\":\"云锦\",\"type\":\"all\",\"city\":\"\"}"))))
                                : Map.of("role", "assistant", "content", "参考南京云锦 [heritage:7] 的包装建议。", "reasoning_content", "final-reasoning");
                        body = Map.of("id", "mock", "object", "chat.completion", "created", 1, "model", "deepseek-flash",
                                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", useTool ? "tool_calls" : "stop")),
                                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 10, "total_tokens", 20));
                    }
                } else { status = 404; body = Map.of(); }
                byte[] bytes = JSON.writeValueAsBytes(body);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            UPSTREAM.start();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void chunk(OutputStream out, Object delta, String finish) throws IOException {
        var choice = new HashMap<String, Object>();
        choice.put("index", 0); choice.put("delta", delta); choice.put("finish_reason", finish.isEmpty() ? null : finish);
        String data = JSON.writeValueAsString(Map.of("id", "mock", "object", "chat.completion.chunk", "created", 1,
                "model", "deepseek-flash", "choices", List.of(choice)));
        out.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8)); out.flush();
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        String origin = "http://127.0.0.1:" + UPSTREAM.getAddress().getPort();
        r.add("agent.backend-url", () -> origin + "/api");
        r.add("agent.deepseek-url", () -> origin);
        r.add("agent.image-url", () -> origin);
        r.add("agent.image-directory", () -> "target/test-image-archive");
        r.add("agent.deepseek-key", () -> "test-key"); r.add("agent.image-key", () -> "image-key");
        r.add("agent.thinking", () -> true); r.add("agent.requests-per-minute", () -> 100);
        r.add("agent.max-rounds", () -> 3);
    }
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newHttpClient();
    @BeforeEach void reset() { REQUESTS.clear(); IMAGE_CALLS.set(0); }
    @AfterAll static void stop() { UPSTREAM.stop(0); }
    HttpResponse<String> post(String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent" + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    Map<String, Object> chat(String session, String message) {
        var body = new HashMap<String, Object>();
        if (session != null) body.put("sessionId", session);
        body.put("message", message); body.put("requestId", UUID.randomUUID().toString()); body.put("allowImage", false);
        return body;
    }
    JsonNode event(String sse, String name) throws Exception {
        for (String block : sse.replace("\r\n", "\n").split("\n\n")) {
            if (block.contains("event:" + name)) {
                for (String line : block.split("\n")) if (line.startsWith("data:")) return JSON.readTree(line.substring(5));
            }
        }
        fail("Missing event " + name + " in " + sse); return null;
    }
    @Test void toolLoopPreservesReasoningAndIsolatesSessions() throws Exception {
        var first = post("/chat", "alice", chat(null, "云锦设计"));
        assertEquals(200, first.statusCode());
        JsonNode answer = event(first.body(), "done");
        assertEquals("南京云锦", answer.path("sources").path(0).path("title").asText());
        assertEquals(2, REQUESTS.size());
        assertTrue(REQUESTS.get(0).path("stream").asBoolean());
        assertTrue(first.body().contains("event:delta"));
        JsonNode secondRequest = REQUESTS.get(1);
        assertEquals("provider-reasoning", secondRequest.path("messages").path(2).path("reasoning_content").asText());
        assertEquals("tool", secondRequest.path("messages").path(3).path("role").asText());
        assertFalse(REQUESTS.get(0).path("tools").toString().contains("generateDesignImage"));
        String session = answer.path("sessionId").asText();
        assertEquals(404, post("/chat", "bob", chat(session, "窃取上下文")).statusCode());
        var followup = post("/chat", "alice", chat(session, "请改为海报"));
        event(followup.body(), "done");
        assertTrue(REQUESTS.get(2).path("messages").toString().contains("final-reasoning"));
        assertFalse(followup.body().contains("provider-reasoning"));
    }
    @Test void emitsTextBeforeUpstreamFinishes() throws Exception {
        FIRST_DELTA = new CountDownLatch(1); RELEASE_STREAM = new CountDownLatch(1);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/chat"))
                .header("Authorization", "Bearer alice").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(chat(null, "slow-stream")))).build();
        try (var body = http.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()) {
            assertTrue(FIRST_DELTA.await(5, TimeUnit.SECONDS));
            var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            var read = CompletableFuture.supplyAsync(() -> {
                try { String line; while ((line = reader.readLine()) != null) if (line.equals("event:delta")) return true; }
                catch (IOException e) { throw new UncheckedIOException(e); }
                return false;
            });
            assertTrue(read.get(3, TimeUnit.SECONDS), "delta must arrive while provider is still blocked");
            RELEASE_STREAM.countDown();
            assertTrue(reader.lines().anyMatch(line -> line.equals("event:done")));
        } finally { RELEASE_STREAM.countDown(); }
    }
    @Test void unauthorizedAndInvalidInputNeverCallModel() throws Exception {
        assertEquals(401, post("/chat", null, chat(null, "test")).statusCode());
        assertEquals(401, post("/chat", "invalid", chat(null, "test")).statusCode());
        assertEquals(400, post("/chat", "alice", chat(null, "x".repeat(4001))).statusCode());
        assertTrue(REQUESTS.isEmpty());
    }
    @Test void limitsLoopsAndDoesNotRetryProviderFailures() throws Exception {
        var loop = post("/chat", "alice", chat(null, "force-loop"));
        assertEquals(422, event(loop.body(), "error").path("code").asInt());
        assertEquals(3, REQUESTS.size()); REQUESTS.clear();
        var failed = post("/chat", "alice", chat(null, "provider-failure"));
        assertEquals(502, event(failed.body(), "error").path("code").asInt());
        assertEquals(1, REQUESTS.size()); assertFalse(failed.body().contains("secret-provider-detail"));
    }
    @Test void imageRequestsAreIdempotentAndValidateParameters() throws Exception {
        var body = new HashMap<String, Object>(Map.of("requestId", UUID.randomUUID().toString(), "prompt", "云锦包装", "size", "2048x2048"));
        assertEquals(200, post("/image", "alice", body).statusCode());
        assertEquals(200, post("/image", "alice", body).statusCode());
        assertEquals(1, IMAGE_CALLS.get());
        body.put("prompt", "不同的需求"); assertEquals(409, post("/image", "alice", body).statusCode());
        body.put("requestId", UUID.randomUUID().toString()); body.put("size", "100x100");
        assertEquals(400, post("/image", "alice", body).statusCode()); assertEquals(1, IMAGE_CALLS.get());
    }
    @Test void completedChatRequestIsReplayedWithoutPayingAgain() throws Exception {
        var body = chat(null, "云锦包装");
        var first = post("/chat", "alice", body);
        body.put("sessionId", event(first.body(), "done").path("sessionId").asText());
        int calls = REQUESTS.size();
        event(post("/chat", "alice", body).body(), "done");
        assertEquals(calls, REQUESTS.size());
    }
}
