package net.modtale.model.dto.request.auth;

public class VerifyMfaRequest {
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
