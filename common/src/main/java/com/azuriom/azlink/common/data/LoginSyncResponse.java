package com.azuriom.azlink.common.data;

public class LoginSyncResponse {

    private boolean allowed = true;
    private String message;

    public boolean isAllowed() {
        return this.allowed;
    }

    public String getMessage() {
        return this.message;
    }
}
