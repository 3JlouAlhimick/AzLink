package com.azuriom.azlink.bukkit.listeners;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.data.LoginSyncResponse;
import com.azuriom.azlink.common.data.LoginSyncState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

public class JPremiumLoginListener {

    private static final String[] LOGIN_EVENT_CLASSES = {
            "com.jodexindustries.jpremium.api.events.PlayerLoginEvent",
            "com.jodexindustries.jpremium.bukkit.events.PlayerLoginEvent"
    };
    private static final String[] LOGOUT_EVENT_CLASSES = {
            "com.jodexindustries.jpremium.api.events.PlayerLogoutEvent",
            "com.jodexindustries.jpremium.bukkit.events.PlayerLogoutEvent"
    };

    private final AzLinkBukkitPlugin plugin;
    private final AzLinkPlugin azLinkPlugin;

    public JPremiumLoginListener(AzLinkBukkitPlugin plugin) {
        this.plugin = plugin;
        this.azLinkPlugin = plugin.getPlugin();
    }

    public void register() {
        Plugin jpremium = this.plugin.getServer().getPluginManager().getPlugin("JPremium");
        if (jpremium == null) {
            this.azLinkPlugin.getLogger().warn("JPremium login sync could not be enabled because the plugin is missing.");
            return;
        }

        ClassLoader classLoader = jpremium.getClass().getClassLoader();
        Listener listener = new Listener() {
        };
        PluginManager manager = this.plugin.getServer().getPluginManager();

        boolean loginRegistered = register(manager, classLoader, listener, LOGIN_EVENT_CLASSES,
                event -> handleEvent(event, LoginSyncState.LOGIN));
        boolean logoutRegistered = register(manager, classLoader, listener, LOGOUT_EVENT_CLASSES,
                event -> handleEvent(event, LoginSyncState.LOGOUT));

        if (loginRegistered || logoutRegistered) {
            this.azLinkPlugin.getLogger().info("JPremium login synchronization enabled.");
        }
    }

    private boolean register(PluginManager manager, ClassLoader classLoader, Listener listener,
            String[] classNames, Consumer<Event> handler) {
        for (String className : classNames) {
            try {
                Class<? extends Event> eventClass = Class.forName(className, false, classLoader)
                        .asSubclass(Event.class);

                EventExecutor executor = (ignored, event) -> handler.accept(event);
                manager.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, this.plugin, true);
                return true;
            } catch (ClassNotFoundException e) {
                // Try next class name variant
            } catch (Throwable throwable) {
                this.azLinkPlugin.getLogger().error("Unable to register JPremium listener for " + className,
                        throwable);
                return false;
            }
        }

        this.azLinkPlugin.getLogger().warn("Unable to find any JPremium event classes among "
                + Arrays.toString(classNames));
        return false;
    }

    private void handleEvent(Event event, LoginSyncState state) {
        Player player = extractPlayer(event);
        if (player == null) {
            this.azLinkPlugin.getLogger().warn("Received JPremium event without a Bukkit player instance: "
                    + event.getClass().getName());
            return;
        }

        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = resolvePlayerIp(player);
        Instant timestamp = Instant.now();

        if (!this.azLinkPlugin.isConfigured()) {
            return;
        }

        this.azLinkPlugin.getHttpClient()
                .syncLogin(state, uuid, ip, timestamp)
                .thenAccept(response -> handleResponse(state, uuid, name, response))
                .exceptionally(ex -> {
                    this.azLinkPlugin.getLogger().error("Failed to synchronize JPremium " + state.getAction()
                            + " for " + name, ex);
                    return null;
                });
    }

    private void handleResponse(LoginSyncState state, UUID uuid, String name, LoginSyncResponse response) {
        if (response == null) {
            return;
        }

        if (state == LoginSyncState.LOGIN && !response.isAllowed()) {
            String message = response.getMessage();
            String formattedMessage = formatMessage(message);
            this.azLinkPlugin.getLogger().warn("Website rejected login for " + name
                    + (message != null && !message.isEmpty() ? ": " + ChatColor.stripColor(formattedMessage) : ""));

            this.azLinkPlugin.getScheduler().executeSync(() -> {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    target.kickPlayer(formattedMessage);
                }
            });

            return;
        }

        if (response.getMessage() != null && !response.getMessage().isEmpty()) {
            String formattedMessage = formatMessage(response.getMessage());
            this.azLinkPlugin.getScheduler().executeSync(() -> {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    target.sendMessage(formattedMessage);
                }
            });
        }
    }

    private Player extractPlayer(Event event) {
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Object player = getPlayer.invoke(event);
            if (player instanceof Player) {
                return (Player) player;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            this.azLinkPlugin.getLogger().warn("Unable to resolve player for JPremium event "
                    + event.getClass().getName(), e);
        }
        return null;
    }

    private String resolvePlayerIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null) {
            return null;
        }
        InetAddress inetAddress = address.getAddress();
        return inetAddress != null ? inetAddress.getHostAddress() : null;
    }

    private String formatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return ChatColor.RED + "Your login was rejected by the website.";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
