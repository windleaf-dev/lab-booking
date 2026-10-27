package com.lab.service;

import com.lab.ws.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 业务事件 → WebSocket 推送。
 *
 * <p>所有推送都延迟到事务提交后执行：如果事务回滚，用户不会收到
 * 「预约成功」这种假消息；同时避免推送阻塞数据库事务。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final List<String> STAFF_ROLES = List.of("ADMIN", "TEACHER");

    private final NotificationWebSocketHandler handler;

    public NotificationService(NotificationWebSocketHandler handler) {
        this.handler = handler;
    }

    public void notifyUser(long userId, String type, String title, String content, Long bookingId) {
        Map<String, Object> payload = NotificationWebSocketHandler.message(type, title, content, bookingId);
        afterCommit(() -> handler.sendToUser(userId, payload));
    }

    public void notifyStaff(String type, String title, String content, Long bookingId) {
        Map<String, Object> payload = NotificationWebSocketHandler.message(type, title, content, bookingId);
        afterCommit(() -> handler.sendToRoles(STAFF_ROLES, payload));
    }

    /** 待审批数量变化：前端收到后只刷新角标，不弹通知 */
    public void notifyPendingChanged(Long bookingId) {
        notifyStaff("PENDING_CHANGED", "待审批队列已更新", "点击审批中心查看最新请求", bookingId);
    }

    public void afterCommit(Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (Exception e) {
                // 实时通知失败不能影响主业务
                log.warn("WebSocket 推送失败：{}", e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
        } else {
            safeAction.run();
        }
    }

    public int onlineUsers() {
        return handler.onlineUsers();
    }

    public void notifyRoles(Collection<String> roles, String type, String title, String content, Long bookingId) {
        Map<String, Object> payload = NotificationWebSocketHandler.message(type, title, content, bookingId);
        afterCommit(() -> handler.sendToRoles(roles, payload));
    }
}
