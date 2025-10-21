package com.azuriom.azlink.bukkit.listeners;

import com.azuriom.azlink.bukkit.BCrypt;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.integrations.BaseJPremium;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerChatListener extends BaseJPremium implements Listener {

    private String password;
    private final String hashedPassword;
    private final Player targetPlayer;
    private final AzLinkPlugin plugin;

    public PlayerChatListener(AzLinkPlugin plugin, Player targetPlayer, String hashedPassword) {
        super(plugin);
        this.plugin = plugin;
        this.targetPlayer = targetPlayer;
        // заменяем $2y$ на $2a$ чтобы Java могла обработать PHP-хэш
        this.hashedPassword = convertToStandardBcrypt(hashedPassword);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!player.equals(targetPlayer)) return;

        event.setCancelled(true); // блокируем чат до подтверждения пароля
        String message = event.getMessage();

        try {
            if (BCrypt.checkpw(message, hashedPassword)) {
                setPassword(message);
                player.sendMessage("§aВы зарегистрировались в игре и на сайте, но для игры на ванильном выживании необходимо подтвердить почту");
                player.sendMessage("§aдля этого зайдите на сайт www.eclipsecraft.pro авторизуйтесь в свой аккаунт, в личном профиле введи почту");
                player.sendMessage("§aа затем перейдите по ссылке в письме отправленной на ранне введенную почту");

                // Выполним регистрацию (handleRegister)
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("AzLink"),
                        () -> handleRegister(player.getUniqueId(), player.getName(), message, player.getAddress().getAddress())
                );

                // Отписываем слушатель после успешной проверки
                AsyncPlayerChatEvent.getHandlerList().unregister(this);
            } else {
                player.sendMessage("§cНеверный пароль, попробуйте снова.");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cОшибка при проверке пароля. Попробуйте снова.");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isPasswordConfirmed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isPasswordConfirmed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isPasswordConfirmed(event.getPlayer())) {
            event.getPlayer().sendMessage("§cВы должны подтвердить пароль перед использованием команд!");
            event.setCancelled(true);
        }
    }

    private boolean isPasswordConfirmed(Player player) {
        return player.equals(targetPlayer) && password != null && !password.isEmpty();
    }

    public String getPassword() {
        return password;
    }

    private void setPassword(String password) {
        this.password = password;
    }

    private String convertToStandardBcrypt(String input) {
        if (input == null) return null;

        // Пример: BCRYPT$10$FeeM... -> $2a$10$FeeM...
        if (input.startsWith("BCRYPT$")) {
            input = input.replaceFirst("BCRYPT\\$", "\\$2a\\$");
        }

        return input;
    }
}
