package com.lab.ws;

import com.lab.common.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 *
 * <p>浏览器原生 WebSocket 不能自定义 Authorization 头，这里使用
 * {@code ws://host/ws/notifications?token=xxx}。生产环境建议改用
 * HttpOnly Cookie 或短期一次性 ticket，避免 token 出现在访问日志里。
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        try {
            JwtUtil.Claims claims = jwtUtil.parse(token);
            attributes.put("uid", claims.uid());
            attributes.put("role", claims.role());
            return true;
        } catch (Exception e) {
            // token 为空、格式错误、签名不合法、已过期统一拒绝握手
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
