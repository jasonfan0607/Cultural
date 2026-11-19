package com.cultural.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    public record ChatRequest(@Size(max = 36) String sessionId,
                              @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,80}") String requestId,
                              @NotBlank @Size(max = 4000) String message, boolean allowImage) {}
    public record ImageRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,80}") String requestId,
                               @NotBlank @Size(max = 4000) String prompt, @NotBlank String size) {}
    private final ProviderClient client;
    private final AgentEngine engine;
    private final SessionStore sessions;
    private final ImageService images;
    private final AgentSettings settings;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService workers = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    public AgentController(ProviderClient client, AgentEngine engine, SessionStore sessions, ImageService images, AgentSettings settings) {
        this.client = client; this.engine = engine; this.sessions = sessions; this.images = images; this.settings = settings;
    }
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "modelConfigured", settings.modelReady(), "imageConfigured", settings.imageReady(),
                "features", List.of("web-search", "search-fallback-cache", "search-query-relaxation", "search-partial-recovery", "search-persistent-cache", "content-dedup", "text-delta-sse"));
    }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chat(@RequestHeader(value = "Authorization", required = false) String bearer,
                                          @Valid @RequestBody ChatRequest request) {
        long user = client.authenticate(bearer);
        if (!settings.modelReady()) throw new AgentException(503, "尚未配置服务端 DEEPSEEK_API_KEY");
        sessions.reserve(user);
        SessionStore.Session session;
        try { session = sessions.open(user, request.sessionId()); }
        catch (RuntimeException e) { sessions.release(user); throw e; }
        SseEmitter emitter = new SseEmitter((settings.timeoutSeconds() + 5) * 1000L);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<Future<?>> task = new AtomicReference<>();
        BiConsumer<String, Object> send = (event, data) -> {
            if (closed.get()) throw new AgentException(499, "请求已取消");
            try { emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data), MediaType.APPLICATION_JSON)); }
            catch (Exception e) { closed.set(true); throw new AgentException(499, "连接已断开"); }
        };
        Runnable stop = () -> { closed.set(true); Future<?> f = task.get(); if (f != null) f.cancel(true); };
        emitter.onCompletion(stop);
        emitter.onError(e -> stop.run());
        emitter.onTimeout(stop);
        ScheduledFuture<?> heartbeat = timer.scheduleAtFixedRate(() -> {
            try { send.accept("ping", Map.of()); } catch (RuntimeException e) { stop.run(); }
        }, 10, 10, TimeUnit.SECONDS);
        ScheduledFuture<?> deadline = timer.schedule(() -> {
            try { send.accept("error", Map.of("code", 504, "message", "本轮执行超时，请缩小问题范围")); }
            catch (RuntimeException ignored) { }
            stop.run(); emitter.complete();
        }, settings.timeoutSeconds(), TimeUnit.SECONDS);
        emitter.onCompletion(() -> { heartbeat.cancel(false); deadline.cancel(false); });
        AtomicBoolean started = new AtomicBoolean();
        try {
            FutureTask<Void> work = new FutureTask<>(() -> {
                started.set(true);
                try {
                    send.accept("session", Map.of("sessionId", session.id));
                    var answer = engine.run(session, bearer, request.requestId(), request.message(), request.allowImage(), send);
                    send.accept("done", answer);
                } catch (Exception e) {
                    if (!closed.get()) {
                        var a = e instanceof AgentException known ? known : new AgentException(500, "服务暂不可用，请稍后重试");
                        try { send.accept("error", Map.of("code", a.status(), "message", a.getMessage())); }
                        catch (RuntimeException ignored) { }
                    }
                } finally {
                    sessions.release(user);
                    heartbeat.cancel(false); deadline.cancel(false); emitter.complete();
                }
                return null;
            }) {
                @Override protected void done() {
                    if (!started.get()) sessions.release(user);
                }
            };
            task.set(work);
            if (closed.get()) work.cancel(true); else workers.execute(work);
        } catch (RejectedExecutionException e) {
            sessions.release(user); heartbeat.cancel(false); deadline.cancel(false);
            throw new AgentException(429, "服务繁忙，请稍后再试");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no").body(emitter);
    }
    @PostMapping("/image")
    public Map<String, String> image(@RequestHeader(value = "Authorization", required = false) String bearer,
                                     @Valid @RequestBody ImageRequest request) {
        long user = client.authenticate(bearer);
        sessions.reserve(user);
        try { return Map.of("url", images.generate(user, request.requestId(), request.prompt(), request.size())); }
        finally { sessions.release(user); }
    }
    @DeleteMapping("/sessions/{id}")
    public Map<String, Boolean> delete(@RequestHeader(value = "Authorization", required = false) String bearer, @PathVariable String id) {
        sessions.delete(client.authenticate(bearer), id);
        return Map.of("deleted", true);
    }
    @PreDestroy
    void close() { workers.shutdownNow(); timer.shutdownNow(); }
}
