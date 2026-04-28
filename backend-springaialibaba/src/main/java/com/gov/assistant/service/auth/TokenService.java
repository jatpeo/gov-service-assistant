package com.gov.assistant.service.auth;

import com.gov.assistant.entity.auth.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
/**
 * 简单 HMAC Token 服务。
 *
 * 调用链：
 * 登录时 AuthService -> createToken()；
 * 请求鉴权时 SecurityConfig 过滤器 -> parseToken() -> SecurityContext。
 */
public class TokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.security.token-secret:gov-service-assistant-local-secret}")
    private String tokenSecret;

    @Value("${app.security.token-ttl-seconds:28800}")
    private long tokenTtlSeconds;

    /**
     * 为登录用户生成带过期时间的 HMAC 签名令牌。
     *
     * 调用链：
     * AuthService.login() -> createToken() -> sign()。
     */
    public String createToken(UserAccount user) {
        long expiresAt = Instant.now().getEpochSecond() + tokenTtlSeconds;
        String payload = user.getUsername() + "." + user.getRole().name() + "." + expiresAt;
        return base64Url(payload) + "." + sign(payload);
    }

    /**
     * 解析并校验前端携带的 Bearer Token。
     *
     * 调用链：
     * SecurityConfig TokenFilter -> parseToken()
     * -> 校验签名 -> 校验过期时间 -> 返回 TokenClaims。
     */
    public Optional<TokenClaims> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        try {
            String[] payloadParts = payload.split("\\.");
            if (payloadParts.length != 3) {
                return Optional.empty();
            }

            long expiresAt = Long.parseLong(payloadParts[2]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            return Optional.of(new TokenClaims(payloadParts[0], payloadParts[1], expiresAt));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成认证签名", e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenClaims(String username, String role, long expiresAt) {
    }
}
