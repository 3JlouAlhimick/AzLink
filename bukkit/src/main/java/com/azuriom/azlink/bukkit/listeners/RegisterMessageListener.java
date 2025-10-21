package com.azuriom.azlink.bukkit.listeners;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;
import com.azuriom.azlink.bukkit.registration.RegistrationDuplicateChecker;
import com.azuriom.azlink.bukkit.integrations.WebsiteHttpClient;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.integrations.BaseJPremium;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class RegisterMessageListener extends BaseJPremium implements PluginMessageListener {

    private final RegistrationDuplicateChecker duplicateChecker;
    private final WebsiteHttpClient websiteClient;
    private final AzLinkBukkitPlugin bukkitPlugin;

    public RegisterMessageListener(AzLinkBukkitPlugin bukkitPlugin, AzLinkPlugin plugin,
                                   RegistrationDuplicateChecker duplicateChecker) {
        super(plugin);
        this.bukkitPlugin = bukkitPlugin;
        this.duplicateChecker = duplicateChecker;
        this.websiteClient = new WebsiteHttpClient(bukkitPlugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("azlink:jpremium")) return;

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = input.readUTF();
            if (!subChannel.equals("registerEvent")) return;

            String playerName = input.readUTF();
            String hashedPassword = input.readUTF();
            String ip = input.readUTF();

            Player target = Bukkit.getPlayerExact(playerName);
            if (target == null) return;

            // Регистрируем слушатель для этого игрока
            PlayerChatListener listener = new PlayerChatListener(super.plugin, this.duplicateChecker, this.bukkitPlugin,
                    this.websiteClient, target, hashedPassword);
            Bukkit.getPluginManager().registerEvents(listener, this.bukkitPlugin);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
