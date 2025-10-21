package com.azuriom.azlink.bukkit.integrations;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class WebsiteHttpClient {

    private static final String DEFAULT_SUCCESS_MESSAGE = "Регистрация на сайте выполнена успешно.";
    private static final String DEFAULT_ERROR_MESSAGE = "Не удалось зарегистрироваться на сайте.";

    private final AzLinkBukkitPlugin bukkitPlugin;
    private final AzLinkPlugin azLinkPlugin;
    private final Executor executor;

    private final URL registerUrl;
    private final String token;
    private final String signatureSecret;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxRetries;
    private final long retryDelayMillis;
    private final boolean enabled;

    public WebsiteHttpClient(AzLinkBukkitPlugin plugin) {
        this.bukkitPlugin = plugin;
        this.azLinkPlugin = plugin.getPlugin();
        this.executor = this.azLinkPlugin != null
                ? this.azLinkPlugin.getScheduler().asyncExecutor()
                : ForkJoinPool.commonPool();

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("website");

        if (section == null) {
            this.registerUrl = null;
            this.token = null;
            this.signatureSecret = null;
            this.connectTimeout = 5000;
            this.readTimeout = 5000;
            this.maxRetries = 3;
            this.retryDelayMillis = 1000;
            this.enabled = false;
            return;
        }

        String baseUrl = trimTrailingSlash(section.getString("url"));
        String registerPath = section.getString("register-path", "/register");
        if (registerPath == null) {
            registerPath = "/register";
        }

        String normalizedPath = registerPath.startsWith("/") ? registerPath : "/" + registerPath;

        URL computedUrl = null;
        if (baseUrl != null && !baseUrl.isEmpty()) {
            try {
                computedUrl = new URL(baseUrl + normalizedPath);
            } catch (MalformedURLException e) {
                logError("Invalid website registration url: " + baseUrl + normalizedPath, e);
            }
        }

        this.registerUrl = computedUrl;
        this.token = section.getString("token");
        this.signatureSecret = section.getString("signature-secret");
        this.connectTimeout = Math.max(1000, section.getInt("timeouts.connect", 5000));
        this.readTimeout = Math.max(1000, section.getInt("timeouts.read", 5000));
        this.maxRetries = Math.max(1, section.getInt("max-retries", 3));
        this.retryDelayMillis = Math.max(0L, section.getLong("retry-delay-ms", 1000L));
        this.enabled = this.registerUrl != null && this.token != null && !this.token.isEmpty();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public CompletableFuture<RegistrationResult> registerPlayer(UUID uuid, String username, String password,
            String email, String ip) {
        if (!this.enabled) {
            return CompletableFuture.completedFuture(RegistrationResult.success(DEFAULT_SUCCESS_MESSAGE));
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", uuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        if (email != null) {
            payload.addProperty("email", email);
        }
        if (ip != null) {
            payload.addProperty("ip", ip);
        }

        String body = AzLinkPlugin.getGson().toJson(payload);

        return CompletableFuture.supplyAsync(() -> executeRequest(body), this.executor);
    }

    private RegistrationResult executeRequest(String body) {
        IOException lastException = null;

        for (int attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                HttpURLConnection connection = (HttpURLConnection) Objects.requireNonNull(this.registerUrl)
                        .openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setConnectTimeout(this.connectTimeout);
                connection.setReadTimeout(this.readTimeout);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + this.token);
                connection.setRequestProperty("User-Agent", "AzLink Bukkit Website Client");

                String signature = computeSignature(body);
                if (signature != null) {
                    connection.setRequestProperty("X-Signature", signature);
                }

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();

                if (status >= 200 && status < 300) {
                    String response = readStream(connection.getInputStream());
                    String message = extractMessage(response, status, true);

                    return RegistrationResult.success(message);
                }

                String errorResponse = readStream(connection.getErrorStream());
                boolean retryable = status >= 500;
                String message = extractMessage(errorResponse, status, false);

                if (retryable && attempt < this.maxRetries) {
                    sleepRetry(attempt);
                    continue;
                }

                return RegistrationResult.failure(status, message, retryable, null);
            } catch (IOException e) {
                lastException = e;

                if (attempt < this.maxRetries) {
                    sleepRetry(attempt);
                    continue;
                }

                String message = DEFAULT_ERROR_MESSAGE + " Причина: " + e.getMessage();
                return RegistrationResult.failure(-1, message, true, e);
            }
        }

        return RegistrationResult.failure(-1, DEFAULT_ERROR_MESSAGE, true, lastException);
    }

    private void sleepRetry(int attempt) {
        if (this.retryDelayMillis <= 0) {
            return;
        }

        try {
            long delay = this.retryDelayMillis * attempt;
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String computeSignature(String body) {
        if (this.signatureSecret == null || this.signatureSecret.isEmpty()) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (GeneralSecurityException e) {
            logError("Unable to compute request signature", e);
            return null;
        }
    }

    private String extractMessage(String response, int status, boolean success) {
        if (response != null && !response.isEmpty()) {
            try {
                JsonElement element = JsonParser.parseString(response);
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("message") && obj.get("message").isJsonPrimitive()) {
                        return obj.get("message").getAsString();
                    }
                    if (!success && obj.has("error") && obj.get("error").isJsonPrimitive()) {
                        return obj.get("error").getAsString();
                    }
                }
            } catch (JsonSyntaxException ignored) {
                // Ignore malformed responses
            }
        }

        if (success) {
            return DEFAULT_SUCCESS_MESSAGE;
        }

        switch (status) {
            case 400:
                return "Сервер отклонил регистрацию: некорректные данные.";
            case 401:
            case 403:
                return "Доступ к регистрации отклонён. Обратитесь к администрации сервера.";
            case 404:
                return "Сервис регистрации на сайте временно недоступен.";
            case 409:
            case 422:
                return "Аккаунт уже зарегистрирован на сайте.";
            default:
                return DEFAULT_ERROR_MESSAGE + " (код " + status + ")";
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return builder.toString();
        }
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return null;
        }

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    private void logError(String message) {
        if (this.azLinkPlugin != null) {
            this.azLinkPlugin.getLogger().error(message);
        } else {
            this.bukkitPlugin.getLogger().severe(message);
        }
    }

    private void logError(String message, Throwable throwable) {
        if (this.azLinkPlugin != null) {
            this.azLinkPlugin.getLogger().error(message, throwable);
        } else {
            this.bukkitPlugin.getLogger().log(Level.SEVERE, message, throwable);
        }
    }

    public static final class RegistrationResult {

        private final boolean success;
        private final int statusCode;
        private final String message;
        private final boolean retryable;
        private final Throwable error;

        private RegistrationResult(boolean success, int statusCode, String message, boolean retryable, Throwable error) {
            this.success = success;
            this.statusCode = statusCode;
            this.message = message;
            this.retryable = retryable;
            this.error = error;
        }

        public static RegistrationResult success(String message) {
            return new RegistrationResult(true, 200, message, false, null);
        }

        public static RegistrationResult failure(int statusCode, String message, boolean retryable, Throwable error) {
            return new RegistrationResult(false, statusCode, message, retryable, error);
        }

        public boolean isSuccess() {
            return this.success;
        }

        public int getStatusCode() {
            return this.statusCode;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isRetryable() {
            return this.retryable;
        }

        public Throwable getError() {
            return this.error;
        }
    }
}
