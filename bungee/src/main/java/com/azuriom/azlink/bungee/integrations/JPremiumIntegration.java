package com.azuriom.azlink.bungee.integrations;

import com.azuriom.azlink.bungee.AzLinkBungeePlugin;
import com.azuriom.azlink.common.integrations.BaseJPremium;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.jakub.jpremium.proxy.api.event.bungee.UserEvent;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
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
        if (!commandSender.isPresent()) return;

        CommandSender sender = commandSender.get();
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) sender;
        SocketAddress socketAddress = player.getSocketAddress();
        InetAddress address = socketAddress instanceof InetSocketAddress
                ? ((InetSocketAddress) socketAddress).getAddress()
                : null;

        player.sendMessage("§aДля регистрации на сайте введите свой пароль ещё раз в чат на сервере.");

        sendRegisterEvent(player, event.getUserProfile().getHashedPassword(), address);
    }

    public static void register(AzLinkBungeePlugin plugin) {
        plugin.getProxy().getPluginManager().registerListener(plugin, new JPremiumIntegration(plugin));
        plugin.getProxy().registerChannel("azlink:jpremium");
    }

    private void sendRegisterEvent(ProxiedPlayer player, String hashedPassword, InetAddress address) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("registerEvent");
        out.writeUTF(player.getName());
        out.writeUTF(hashedPassword);
        out.writeUTF(address != null ? address.getHostAddress() : "unknown");

        player.getServer().sendData("azlink:jpremium", out.toByteArray());
    }
}
