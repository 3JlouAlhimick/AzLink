package com.azuriom.azlink.common.data;

public enum LoginSyncState {

    LOGIN("login"),
    LOGOUT("logout");

    private final String action;

    LoginSyncState(String action) {
        this.action = action;
    }

    public String getAction() {
        return this.action;
    }
}
