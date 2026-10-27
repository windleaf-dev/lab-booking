package com.lab.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量 JWT（HS256）：只依赖 JDK + Jackson，不引入 jjwt。
 */
@Component
public class JwtUtil {

    public record Claims(long uid, String username, String role, long exp) {
    }

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long expireMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:12}") long expireHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMillis = expireHours * 3600_000L;
    }

    public String create(long uid, String username, String role) {
        long exp = System.currentTimeMillis() + expireMillis;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", uid);
        payload.put("un", username);
        payload.put("role", role);
        payload.put("exp", exp);
        try {
            String head = b64(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String body = b64(objectMapper.writeValueAsBytes(payload));
            String content = head + "." + body;
            return content + "." + b64(hmac(content));
        } catch (Exception e) {
            throw new BizException(500, "生成令牌失败");
        }
    }

    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw BizException.unauthorized("未登录");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw BizException.unauthorized("登录状态无效");
        }
        String content = parts[0] + "." + parts[1];
        byte[] expected = hmac(content);
        if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) {
            throw BizException.unauthorized("登录状态无效");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long exp = ((Number) payload.get("exp")).longValue();
            if (System.currentTimeMillis() > exp) {
                throw BizException.unauthorized("登录已过期，请重新登录");
            }
            return new Claims(
                    ((Number) payload.get("uid")).longValue(),
                    String.valueOf(payload.get("un")),
                    String.valueOf(payload.get("role")),
                    exp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.unauthorized("登录状态无效");
        }
    }

    private byte[] hmac(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException(500, "签名失败");
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
