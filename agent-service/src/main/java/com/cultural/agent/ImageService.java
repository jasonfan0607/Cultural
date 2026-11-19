package com.cultural.agent;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

/** Idempotency includes failures: a timeout must not silently create another paid image. */
@Service
public class ImageService {
    private record Entry(String fingerprint, long created, FutureTask<String> task) {}
    private final Map<String, Entry> cache = new HashMap<>();
    private final ProviderClient client;
    public ImageService(ProviderClient client) { this.client = client; }
    public String generate(long user, String requestId, String prompt, String size) {
        String key = user + ":" + requestId;
        String fingerprint = prompt + "\n" + size;
        Entry entry;
        synchronized (cache) {
            long now = System.currentTimeMillis();
            cache.values().removeIf(e -> e.task.isDone() && now - e.created > 1800000);
            entry = cache.get(key);
            if (entry != null && !entry.fingerprint.equals(fingerprint))
                throw new AgentException(409, "同一图片请求不能更换参数，请发起新请求");
            if (entry == null) {
                if (cache.size() >= 500) throw new AgentException(429, "图片任务繁忙，请稍后再试");
                entry = new Entry(fingerprint, now, new FutureTask<>(() -> client.image(prompt, size)));
                cache.put(key, entry);
            }
        }
        entry.task.run();
        try { return entry.task.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AgentException(499, "请求已取消"); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof AgentException a) throw a;
            throw new AgentException(502, "图片生成失败");
        }
    }
}
