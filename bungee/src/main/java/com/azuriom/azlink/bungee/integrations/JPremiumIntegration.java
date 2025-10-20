package com.azuriom.azlink.bungee.integrations;

import com.azuriom.azlink.bungee.AzLinkBungeePlugin;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.jakub.jpremium.proxy.api.event.bungee.UserEvent;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import com.azuriom.azlink.common.integrations.BaseJPremium;
import net.md_5.bungee.event.EventHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

public class JPremiumIntegration extends BaseJPremium implements Listener {

    public JPremiumIntegration(AzLinkBungeePlugin plugin) {
        super(plugin.getPlugin());
    }

    @EventHandler
    public void onRegister(UserEvent.Register event) {
        Optional<CommandSender> commandSender = event.getCommandSender();
        if(commandSender.isPresent()) {
            CommandSender sender = commandSender.get();
            ProxiedPlayer player =  (ProxiedPlayer) sender;
            SocketAddress socketAddress = player.getSocketAddress();
            InetAddress address = socketAddress instanceof InetSocketAddress
                    ? ((InetSocketAddress) socketAddress).getAddress() : null;

            handleRegister(player.getUniqueId(), player.getName(), event.getUserProfile().getHashedPassword(), address);
        } else{
            return;
        }
    }

    public static void register(AzLinkBungeePlugin plugin) {
        plugin.getProxy().getPluginManager().registerListener(plugin, new JPremiumIntegration(plugin));
    }
}
