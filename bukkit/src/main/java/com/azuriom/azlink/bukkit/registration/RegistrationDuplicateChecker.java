package com.azuriom.azlink.bukkit.registration;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Helper used to prevent duplicate registrations by checking the website database before
 * attempting to register a new player through the HTTP API.
 */
public class RegistrationDuplicateChecker {

    public enum DuplicateType {
        UUID,
        USERNAME,
        EMAIL
    }

    public static final class DuplicateCheckResult {

        private static final DuplicateCheckResult NO_DUPLICATE = new DuplicateCheckResult(null, null);

        private final DuplicateType type;
        private final String value;

        private DuplicateCheckResult(DuplicateType type, String value) {
            this.type = type;
            this.value = value;
        }

        public static DuplicateCheckResult noDuplicate() {
            return NO_DUPLICATE;
        }

        public static DuplicateCheckResult duplicate(DuplicateType type, String value) {
            return new DuplicateCheckResult(Objects.requireNonNull(type, "type"), value);
        }

        public boolean isDuplicate() {
            return this.type != null;
        }

        public DuplicateType getType() {
            return this.type;
        }

        public String getValue() {
            return this.value;
        }
    }

    private final AzLinkBukkitPlugin plugin;

    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String table;
    private final String uuidColumn;
    private final String usernameColumn;
    private final String emailColumn;

    public RegistrationDuplicateChecker(AzLinkBukkitPlugin plugin) {
        this.plugin = plugin;

        boolean enabled = false;
        String jdbcUrl = null;
        String username = null;
        String password = null;
        String table = null;
        String uuidColumn = null;
        String usernameColumn = null;
        String emailColumn = null;

        if (plugin.getConfig().isConfigurationSection("registration-check")) {
            enabled = plugin.getConfig().getBoolean("registration-check.enabled", false);
            jdbcUrl = plugin.getConfig().getString("registration-check.jdbc-url");
            username = plugin.getConfig().getString("registration-check.username");
            password = plugin.getConfig().getString("registration-check.password");
            table = plugin.getConfig().getString("registration-check.table", "users");
            uuidColumn = plugin.getConfig().getString("registration-check.uuid-column", "game_id");
            usernameColumn = plugin.getConfig().getString("registration-check.username-column", "name");
            emailColumn = plugin.getConfig().getString("registration-check.email-column", "email");

            if (enabled && (jdbcUrl == null || jdbcUrl.isEmpty()
                    || username == null || username.isEmpty()
                    || password == null)) {
                plugin.getLogger().warning("Duplicate registration check is enabled but the database configuration is incomplete."
                        + " The check will be disabled until the configuration is fixed.");
                enabled = false;
            }
        }

        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.table = table;
        this.uuidColumn = uuidColumn;
        this.usernameColumn = usernameColumn;
        this.emailColumn = emailColumn;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public DuplicateCheckResult check(UUID uuid, String playerName, String email) {
        if (!this.enabled) {
            return DuplicateCheckResult.noDuplicate();
        }

        try (Connection connection = DriverManager.getConnection(this.jdbcUrl, this.username, this.password)) {
            if (uuid != null && this.uuidColumn != null && !this.uuidColumn.isEmpty()
                    && exists(connection, this.uuidColumn, uuid.toString())) {
                return DuplicateCheckResult.duplicate(DuplicateType.UUID, uuid.toString());
            }

            if (playerName != null && !playerName.isEmpty() && this.usernameColumn != null && !this.usernameColumn.isEmpty()
                    && exists(connection, this.usernameColumn, playerName)) {
                return DuplicateCheckResult.duplicate(DuplicateType.USERNAME, playerName);
            }

            if (email != null && !email.isEmpty() && this.emailColumn != null && !this.emailColumn.isEmpty()
                    && exists(connection, this.emailColumn, email)) {
                return DuplicateCheckResult.duplicate(DuplicateType.EMAIL, email);
            }
        } catch (SQLException exception) {
            this.plugin.getLogger().log(Level.WARNING,
                    "Unable to verify duplicate registration in the database: " + exception.getMessage(), exception);
        }

        return DuplicateCheckResult.noDuplicate();
    }

    private boolean exists(Connection connection, String column, String value) throws SQLException {
        String query = "SELECT 1 FROM " + this.table + " WHERE " + column + " = ? LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
