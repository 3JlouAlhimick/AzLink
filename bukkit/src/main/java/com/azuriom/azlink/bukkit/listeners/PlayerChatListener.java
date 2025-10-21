package com.azuriom.azlink.bukkit.listeners;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;
import com.azuriom.azlink.bukkit.BCrypt;
import com.azuriom.azlink.bukkit.registration.RegistrationDuplicateChecker;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.integrations.BaseJPremium;
import com.azuriom.azlink.bukkit.integrations.WebsiteHttpClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.HandlerList;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerChatListener extends BaseJPremium implements Listener {

    private String password;
    private final String hashedPassword;
    private final Player targetPlayer;
    private final RegistrationDuplicateChecker duplicateChecker;
    private final WebsiteHttpClient websiteClient;
    private final AzLinkBukkitPlugin bukkitPlugin;
    private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);

    public PlayerChatListener(AzLinkPlugin plugin, RegistrationDuplicateChecker duplicateChecker,
                              AzLinkBukkitPlugin bukkitPlugin, WebsiteHttpClient websiteClient,
                              Player targetPlayer, String hashedPassword) {
        super(plugin);
        this.targetPlayer = targetPlayer;
        this.duplicateChecker = duplicateChecker;
        this.websiteClient = websiteClient;
        this.bukkitPlugin = bukkitPlugin;
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
                if (this.registrationInProgress.get()) {
                    player.sendMessage("§eРегистрация уже выполняется, пожалуйста, подождите.");
                    return;
                }

                RegistrationDuplicateChecker.DuplicateCheckResult duplicateResult = this.duplicateChecker != null
                        ? this.duplicateChecker.check(player.getUniqueId(), player.getName(), null)
                        : RegistrationDuplicateChecker.DuplicateCheckResult.noDuplicate();

                if (duplicateResult.isDuplicate()) {
                    handleDuplicate(player, duplicateResult);
                    return;
                }

                this.registrationInProgress.set(true);
                player.sendMessage("§7Выполняем регистрацию на сайте, пожалуйста, подождите...");

                InetAddress address = player.getAddress() != null ? player.getAddress().getAddress() : null;
                String ip = address != null ? address.getHostAddress() : null;

                CompletableFuture<WebsiteHttpClient.RegistrationResult> pipeline = this.websiteClient
                        .registerPlayer(player.getUniqueId(), player.getName(), message, null, ip)
                        .thenCompose(result -> {
                            if (!result.isSuccess()) {
                                return CompletableFuture.completedFuture(result);
                            }

                            return handleRegister(player.getUniqueId(), player.getName(), message, address)
                                    .thenApply(ignored -> result);
                        });

                pipeline.whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(this.bukkitPlugin, () -> {
                    this.registrationInProgress.set(false);

                    if (!player.isOnline()) {
                        return;
                    }

                    if (throwable != null) {
                        Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                                && throwable.getCause() != null ? throwable.getCause() : throwable;
                        String error = cause.getMessage() != null ? cause.getMessage() : "Неизвестная ошибка";
                        player.sendMessage("§cПроизошла ошибка при регистрации: " + error);
                        this.plugin.getLogger().error("Website registration failed for " + player.getName(), cause);
                        return;
                    }

                    if (result == null) {
                        player.sendMessage("§cНе удалось завершить регистрацию. Попробуйте снова.");
                        return;
                    }

                    if (!result.isSuccess()) {
                        if (result.getError() != null) {
                            this.plugin.getLogger().error("Website registration error for " + player.getName(),
                                    result.getError());
                        } else {
                            this.plugin.getLogger().warn("Website registration returned status "
                                    + result.getStatusCode() + " for " + player.getName());
                        }

                        player.sendMessage("§c" + result.getMessage());
                        if (result.getStatusCode() == 409 || result.getStatusCode() == 422) {
                            player.sendMessage("§eЕсли это ваш аккаунт, авторизуйтесь на сайте или воспользуйтесь восстановлением доступа.");
                            setPassword("duplicate");
                            unregisterListeners();
                        } else {
                            player.sendMessage("§eПовторите попытку позже или обратитесь в поддержку.");
                        }
                        return;
                    }

                    setPassword(message);
                    player.sendMessage("§a" + result.getMessage());
                    player.sendMessage("§aВы зарегистрировались в игре и на сайте, но для игры на ванильном выживании необходимо подтвердить почту");
                    player.sendMessage("§aдля этого зайдите на сайт www.eclipsecraft.pro авторизуйтесь в свой аккаунт, в личном профиле введи почту");
                    player.sendMessage("§aа затем перейдите по ссылке в письме отправленной на ранне введенную почту");

                    unregisterListeners();
                }));
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

    private void handleDuplicate(Player player, RegistrationDuplicateChecker.DuplicateCheckResult result) {
        setPassword("duplicate");

        switch (result.getType()) {
            case EMAIL:
                player.sendMessage("§cНа сайте уже существует аккаунт с такой почтой.");
                break;
            case USERNAME:
                player.sendMessage("§cИгрок с таким ником уже зарегистрирован на сайте.");
                break;
            case UUID:
            default:
                player.sendMessage("§cЭтот игровой аккаунт уже связан с записью на сайте.");
                break;
        }

        player.sendMessage("§eЕсли это ваш аккаунт, авторизуйтесь на сайте или восстановите доступ через форму восстановления.");

        if (super.plugin != null) {
            super.plugin.getLogger().warn("Пропускаем регистрацию игрока " + player.getName()
                    + ": найдена запись в базе данных (" + result.getType() + ").");
        }

        unregisterListeners();
    }

    private void unregisterListeners() {
        Bukkit.getScheduler().runTask(this.bukkitPlugin, () -> HandlerList.unregisterAll(this));
    }
}
