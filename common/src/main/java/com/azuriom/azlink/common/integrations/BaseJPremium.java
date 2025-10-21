package com.azuriom.azlink.common.integrations;

import com.azuriom.azlink.common.AzLinkPlugin;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BaseJPremium {

    protected final AzLinkPlugin plugin;

    public BaseJPremium(AzLinkPlugin plugin) {
        this.plugin = plugin;

        this.plugin.getLogger().info("nLogin integration enabled.");
    }

    protected CompletableFuture<Void> handleRegister(UUID uuid, String name, String password, InetAddress address) {
        return this.plugin.getHttpClient()
                .registerUser(name, null, uuid, password, address)
                .exceptionally(ex -> {
                    this.plugin.getLogger().error("Unable to register " + name, ex);

                    return null;
                });
    }
}
