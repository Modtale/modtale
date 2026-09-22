package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public class LauncherAuthExchangeRequest {

    @NotBlank(message = "A launcher authorization code is required.")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
