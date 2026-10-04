package top.zxylearn.chatserver.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Component
public class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String token = firstNonBlank(request.getHeaders().getFirst("satoken"), query.getFirst("satoken"));
        if (token == null) return false;
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) return false;
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            attributes.put("userId", userId);
            attributes.put("deviceId", normalizeDeviceId(query.getFirst("deviceId")));
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private String normalizeDeviceId(String value) {
        if (value != null && value.matches("^[a-zA-Z0-9_-]{8,64}$")) return value;
        return UUID.randomUUID().toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
