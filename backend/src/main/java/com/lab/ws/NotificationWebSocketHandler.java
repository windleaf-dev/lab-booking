package com.lab.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护「用户 → 多个 WebSocket 会话」的映射，支持多标签页同时在线。
 * 消息为 JSON 文本，客户端通过 type 区分业务事件。
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoles = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long uid = (Long) session.getAttributes().get("uid");
        String role = (String) session.getAttributes().get("role");
        if (uid == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        userSessions.computeIfAbsent(uid, key -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoles.put(session.getId(), role == null ? "STUDENT" : role);
        log.info("WebSocket 已连接：uid={}, role={}, session={}", uid, role, session.getId());

        Map<String, Object> hello = message("CONNECTED", "实时通道已连接",
                "预约、审批与违约状态会实时推送", null);
        send(session, hello);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 前端每 25 秒发一次 ping，用于探测半开连接
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            Map<String, Object> pong = new LinkedHashMap<>();
            pong.put("type", "PONG");
            pong.put("at", LocalDateTime.now().format(TIME_FORMAT));
            send(session, pong);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = (Long) session.getAttributes().get("uid");
        sessionRoles.remove(session.getId());
        if (uid == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(uid);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(uid);
            }
        }
        log.info("WebSocket 已断开：uid={}, status={}", uid, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常：session={}, reason={}", session.getId(), exception.getMessage());
    }

    /** 推送给指定用户的所有在线标签页 */
    public void sendToUser(long userId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : Set.copyOf(sessions)) {
            if (session.isOpen()) {
                send(session, payload);
            } else {
                sessions.remove(session);
            }
        }
    }

    /** 按角色推送，例如新的待审批预约只通知管理员 / 教师 */
    public void sendToRoles(Collection<String> roles, Map<String, Object> payload) {
        for (Map.Entry<Long, Set<WebSocketSession>> entry : userSessions.entrySet()) {
            for (WebSocketSession session : Set.copyOf(entry.getValue())) {
                String role = sessionRoles.get(session.getId());
                if (role != null && roles.contains(role) && session.isOpen()) {
                    send(session, payload);
                }
            }
        }
    }

    /** 在线会话数，健康检查 / 调试用 */
    public int onlineUsers() {
        return userSessions.size();
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            // WebSocketSession 不是线程安全的，多线程推送时要串行化
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("WebSocket 发送失败：session={}, reason={}", session.getId(), e.getMessage());
        }
    }

    public static Map<String, Object> message(String type, String title, String content, Long bookingId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("bookingId", bookingId);
        payload.put("at", LocalDateTime.now().format(TIME_FORMAT));
        return payload;
    }
}
