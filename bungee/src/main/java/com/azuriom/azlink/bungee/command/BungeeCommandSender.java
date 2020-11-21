package com.azuriom.azlink.bungee.command;

import com.azuriom.azlink.common.command.CommandSender;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

public class BungeeCommandSender implements CommandSender {

    private final net.md_5.bungee.api.CommandSender sender;

    public BungeeCommandSender(net.md_5.bungee.api.CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String getName() {
        return this.sender.getName();
    }

    @Override
    public UUID getUuid() {
        if (this.sender instanceof ProxiedPlayer) {
            return ((ProxiedPlayer) this.sender).getUniqueId();
        }

        return UUID.nameUUIDFromBytes(getName().getBytes());
    }

    @Override
    public void sendMessage(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);

        this.sender.sendMessage(TextComponent.fromLegacyText(formatted));
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.sender.hasPermission(permission);
    }
}
