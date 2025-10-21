package com.azuriom.azlink.common.integrations;

import com.azuriom.azlink.common.AzLinkPlugin;

import java.net.InetAddress;
import java.util.UUID;

public class BaseJPremium {

    protected final AzLinkPlugin plugin;

    public BaseJPremium(AzLinkPlugin plugin) {
        this.plugin = plugin;

        this.plugin.getLogger().info("JPremium integration enabled.");
    }

    protected void handleRegister(UUID uuid, String name, String password, InetAddress address) {
        this.plugin.getHttpClient()
                .registerUser(name, null, uuid, password, address)
                .exceptionally(ex -> {
                    this.plugin.getLogger().error("Unable to register " + name, ex);

                    return null;
                });
    }
}
