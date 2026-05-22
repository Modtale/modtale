package net.modtale.model.dto.request.auth;

public class MfaLoginRequest {
    private String pre_auth_token;
    private String code;

    public String getPre_auth_token() {
        return pre_auth_token;
    }

    public void setPre_auth_token(String pre_auth_token) {
        this.pre_auth_token = pre_auth_token;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
