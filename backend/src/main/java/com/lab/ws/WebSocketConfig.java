package com.lab.ws;

import com.lab.common.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(NotificationWebSocketHandler handler, JwtUtil jwtUtil) {
        this.handler = handler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .addInterceptors(new JwtHandshakeInterceptor(jwtUtil))
                // 开发期前端 5174、生产 Nginx 同源；如需限制可改成具体域名
                .setAllowedOriginPatterns("*");
    }
}
