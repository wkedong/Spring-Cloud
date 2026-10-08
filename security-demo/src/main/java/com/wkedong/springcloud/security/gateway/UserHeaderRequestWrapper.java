package com.wkedong.springcloud.security.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求头包装器：把「网关注入的用户身份」以请求头的形式交给后面的业务代码。
 * <p>
 * 为什么必须「覆盖」而不是「追加」：请求头是客户端可以随便写的，
 * 如果只是 addHeader，攻击者带上 {@code X-User-Id: admin} 就能伪造身份。
 * 所以做法是——网关验证令牌后，**先删掉客户端传来的同名头，再写入可信值**。
 * <p>
 * 另一条铁律：这类头只在内网可信。业务服务必须只能被网关访问到
 * （安全组 / K8s NetworkPolicy / 服务网格），否则绕过网关直接打服务等于绕过鉴权。
 *
 * @author wkedong
 */
public class UserHeaderRequestWrapper extends HttpServletRequestWrapper {

    /** 需要覆写的头（不可变副本，保证包装后不再被修改） */
    private final Map<String, String> overrideHeaders;

    /**
     * @param request        原始请求
     * @param userId         令牌里的 sub
     * @param userRoles      令牌里的 roles（逗号分隔）
     */
    public UserHeaderRequestWrapper(HttpServletRequest request, String userId, String userRoles) {
        super(request);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(JwtAuthGlobalFilter.HEADER_USER_ID, userId);
        headers.put(JwtAuthGlobalFilter.HEADER_USER_ROLES, userRoles);
        this.overrideHeaders = Collections.unmodifiableMap(headers);
    }

    @Override
    public String getHeader(String name) {
        for (Map.Entry<String, String> entry : overrideHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        for (Map.Entry<String, String> entry : overrideHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(entry.getValue()));
            }
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        // 先拿原始头名，剔除被覆盖的，再补上可信值——否则下游可能同时读到两个同名头
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        if (original != null) {
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                boolean overridden = false;
                for (String key : overrideHeaders.keySet()) {
                    if (key.equalsIgnoreCase(name)) {
                        overridden = true;
                        break;
                    }
                }
                if (!overridden) {
                    names.add(name);
                }
            }
        }
        names.addAll(overrideHeaders.keySet());
        return Collections.enumeration(names);
    }
}
