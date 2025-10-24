package com.azuriom.azlink.bukkit.listeners;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@NullMarked
public class ServerJoinListener implements Listener {

    /**
     * A map for holding all currently connecting players.
     */
    private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();

    @EventHandler
    void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
        Dialog dialog = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(Key.key("papermc:custom_dialog"));
        if (dialog == null) {
            // The dialog failed to load :(
            // This would happen if the dialog could not be found in the registry by the provided key.
            // Usually that is an indicator that the used key is different from the one used to register your
            // dialog, your bootstrapper might not be registered, or an exception occurred in the bootstrap phase.
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uniqueId = connection.getProfile().getId();
        if (uniqueId == null) {
            return;
        }

        // Construct a new completable future without a task.
        CompletableFuture<Boolean> response = new CompletableFuture<>();
        // Complete the future if nothing has been done after one minute.
        response.completeOnTimeout(false, 1, TimeUnit.MINUTES);

        // Put it into our map.
        awaitingResponse.put(uniqueId, response);

        Audience audience = connection.getAudience();
        // Show the connecting player the dialog.
        audience.showDialog(dialog);

        // Wait until the future is complete. This step is necessary in order to keep the player in the configuration phase.
        if (!response.join()) {
            // We close the dialog manually because the client might not do it on its own.
            audience.closeDialog();
            // If the response is false, they declined. Therefore, we kick them from the server.
            connection.disconnect(Component.text("You hate Paper-chan :(", NamedTextColor.RED));
        }

        // We clean the map to avoid unnecessary entry buildup.
        awaitingResponse.remove(uniqueId);
    }

    /**
     * An event for handling dialog button click events.
     */
    /**
     * An event handler for cleanup the map to avoid unnecessary entry buildup.
     */
    @EventHandler
    void onConnectionClose(PlayerConnectionCloseEvent event) {
        awaitingResponse.remove(event.getPlayerUniqueId());
    }

    /**
     * Simple utility method for setting a connection's dialog response result.
     */
    private void setConnectionJoinResult(UUID uniqueId, boolean value) {
        CompletableFuture<Boolean> future = awaitingResponse.get(uniqueId);
        if (future != null) {
            future.complete(value);
        }
    }
}