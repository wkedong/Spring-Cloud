package com.wkedong.springcloud.security.web.dto;

/**
 * 登录成功返回的令牌信息。
 * <p>
 * 字段是 OAuth2 令牌响应的通用形状（RFC 6749 的 access_token/token_type/expires_in）：
 * 调用方拿到后，后续请求带 {@code Authorization: Bearer <accessToken>} 即可，
 * 不需要 Cookie、不需要服务端 Session。
 *
 * @author wkedong
 */
public class TokenResponse {

    /** JWT 本体（三段 Base64URL：Header.Payload.Signature） */
    private String accessToken;

    /** 令牌类型，固定 Bearer：客户端据此拼 Authorization 头 */
    private String tokenType;

    /** 有效期（秒）：客户端可据此提前刷新，而不是等到 401 才补救 */
    private long expiresIn;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String tokenType, long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
