package com.cultural.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import java.util.*;

/** Single-instance, bounded, user-scoped conversation storage. Store complete turns, never partial tool pairs. */
@Component
public class SessionStore {
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<Long, Window> windows = new HashMap<>();
    private final Set<Long> activeUsers = new HashSet<>();
    private final AgentSettings settings;
    public SessionStore(AgentSettings settings) { this.settings = settings; }
    public static class Session {
        final long user;
        final String id;
        long touched = System.currentTimeMillis();
        final Deque<List<Message>> turns = new ArrayDeque<>();
        String lastRequest, lastInput;
        AgentEngine.Answer lastAnswer;
        Session(long user, String id) { this.user = user; this.id = id; }
        List<Message> history() { return turns.stream().flatMap(Collection::stream).toList(); }
    }
    private record Window(long start, int count) {}
    public synchronized void reserve(long user) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start > 60000);
        Window w = windows.get(user);
        if (w != null && w.count >= settings.requestsPerMinute()) throw new AgentException(429, "请求过于频繁，请一分钟后再试");
        if (activeUsers.contains(user)) throw new AgentException(409, "已有请求执行中，请等待完成或取消");
        if (activeUsers.size() >= 8 || windows.size() >= 5000) throw new AgentException(429, "服务繁忙，请稍后再试");
        activeUsers.add(user);
        windows.put(user, new Window(w == null ? now : w.start, w == null ? 1 : w.count + 1));
    }
    public synchronized void release(long user) { activeUsers.remove(user); }
    public synchronized Session open(long user, String id) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> (!activeUsers.contains(s.user) || s.user == user)
                && now - s.touched > settings.sessionTtlMinutes() * 60000L);
        if (id != null && !id.isBlank()) {
            Session s = sessions.get(id);
            if (s == null || s.user != user || now - s.touched > settings.sessionTtlMinutes() * 60000L)
                throw new AgentException(404, "会话不存在或已过期，请新建对话");
            s.touched = now;
            return s;
        }
        if (sessions.size() >= settings.maxSessions()) throw new AgentException(429, "会话数量已达上限，请稍后再试");
        Session s = new Session(user, UUID.randomUUID().toString());
        sessions.put(s.id, s);
        return s;
    }
    public synchronized void delete(long user, String id) {
        Session s = sessions.get(id);
        if (s == null || s.user != user) throw new AgentException(404, "会话不存在");
        if (activeUsers.contains(user)) throw new AgentException(409, "请等待当前请求结束");
        sessions.remove(id);
    }
    public synchronized void commit(Session s, List<Message> turn, String request, String input, AgentEngine.Answer answer) {
        s.turns.addLast(List.copyOf(turn));
        while (s.turns.size() > 6 || (s.turns.size() > 1 && size(s.history()) > settings.maxContextChars() / 2)) s.turns.removeFirst();
        s.lastRequest = request; s.lastInput = input; s.lastAnswer = answer;
        s.touched = System.currentTimeMillis();
    }
    static int size(List<Message> messages) {
        // toString includes reasoning and tool arguments/results; count those too.
        return messages.stream().mapToInt(m -> m.toString().length()).sum();
    }
}
