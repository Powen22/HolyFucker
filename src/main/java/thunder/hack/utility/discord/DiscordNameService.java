package thunder.hack.utility.discord;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordNameService {
    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);
    private static final long NEGATIVE_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    private HttpClient httpClient;
    private final String backendBaseUrl;
    private final Map<UUID, CachedName> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public DiscordNameService(@NotNull String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl.endsWith("/") ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1) : backendBaseUrl;
        // Ленивая инициализация HttpClient - создаем только при первом использовании
    }
    
    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        }
        return httpClient;
    }

    @Nullable
    public String getDiscordName(@NotNull UUID playerUuid) {
        CachedName cached = cache.get(playerUuid);
        
        if (cached != null) {
            long age = System.currentTimeMillis() - cached.timestamp;
            if (cached.name != null && age < CACHE_TTL_MS) {
                return cached.name;
            }
            if (cached.name == null && age < NEGATIVE_CACHE_TTL_MS) {
                return null; // Negative cache hit
            }
        }

        // Request asynchronously только если executor активен
        if (!executor.isShutdown() && !executor.isTerminated()) {
            requestDiscordNameAsync(playerUuid, 0);
        }
        
        // Return cached value if available, otherwise null
        return cached != null ? cached.name : null;
    }

    private void requestDiscordNameAsync(@NotNull UUID playerUuid, int retryCount) {
        // Проверяем, что executor не завершен
        if (executor.isShutdown() || executor.isTerminated()) {
            return; // Не можем выполнить запрос, executor завершен
        }
        
        CompletableFuture.supplyAsync(() -> {
            try {
                String url = backendBaseUrl + "/lookup?mc_uuid=" + playerUuid.toString();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    // Parse JSON response: {"display_name": "...", "updated_at": "..."}
                    String body = response.body();
                    String displayName = parseDisplayName(body);
                    if (displayName != null) {
                        updateCache(playerUuid, displayName);
                        return displayName;
                    }
                } else if (response.statusCode() == 404) {
                    // Not linked - cache negative result
                    updateCache(playerUuid, null);
                    return null;
                } else if (response.statusCode() == 429) {
                    // Rate limited - retry with backoff
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("1");
                    long delay;
                    try {
                        delay = Long.parseLong(retryAfter) * 1000;
                    } catch (NumberFormatException e) {
                        delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount);
                    }
                    if (retryCount < MAX_RETRIES && !executor.isShutdown() && !executor.isTerminated()) {
                        executor.schedule(() -> requestDiscordNameAsync(playerUuid, retryCount + 1), delay, TimeUnit.MILLISECONDS);
                    }
                    return null;
                } else if (response.statusCode() >= 500) {
                    // Server error - retry with exponential backoff
                    if (retryCount < MAX_RETRIES && !executor.isShutdown() && !executor.isTerminated()) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount);
                        executor.schedule(() -> requestDiscordNameAsync(playerUuid, retryCount + 1), delay, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
            } catch (java.net.http.HttpTimeoutException e) {
                // Timeout - retry with exponential backoff
                if (retryCount < MAX_RETRIES && !executor.isShutdown() && !executor.isTerminated()) {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount);
                    executor.schedule(() -> requestDiscordNameAsync(playerUuid, retryCount + 1), delay, TimeUnit.MILLISECONDS);
                }
            } catch (java.io.IOException e) {
                // Network error - retry with exponential backoff
                if (retryCount < MAX_RETRIES && !executor.isShutdown() && !executor.isTerminated()) {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount);
                    executor.schedule(() -> requestDiscordNameAsync(playerUuid, retryCount + 1), delay, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                // Other errors - don't retry
                // Log error but don't crash
            }
            return null;
        }, executor).exceptionally(e -> {
            // Обрабатываем RejectedExecutionException и другие ошибки
            if (e instanceof java.util.concurrent.RejectedExecutionException) {
                // Executor был завершен, игнорируем
                return null;
            }
            return null;
        }).thenAccept(displayName -> {
            // Update on main thread if needed
            if (displayName != null && MinecraftClient.getInstance() != null) {
                MinecraftClient.getInstance().execute(() -> {
                    // Cache is already updated, this is just for thread safety
                });
            }
        });
    }

    private void updateCache(@NotNull UUID playerUuid, @Nullable String displayName) {
        cache.put(playerUuid, new CachedName(displayName, System.currentTimeMillis()));
    }

    @Nullable
    private String parseDisplayName(@NotNull String json) {
        // Simple JSON parsing - можно улучшить используя Gson
        try {
            // Ищем "display_name":"value"
            int start = json.indexOf("\"display_name\"");
            if (start < 0) return null;
            
            // Находим начало значения
            start = json.indexOf(":", start) + 1;
            // Пропускаем пробелы
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                start++;
            }
            
            if (start >= json.length()) return null;
            
            // Если значение в кавычках
            if (json.charAt(start) == '"') {
                start++; // Пропускаем открывающую кавычку
                int end = json.indexOf("\"", start);
                if (end < 0) return null;
                return json.substring(start, end);
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void openLinkPage(@NotNull UUID playerUuid) {
        String url = backendBaseUrl + "/link/start?mc_uuid=" + playerUuid.toString();
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
            }
            Util.getOperatingSystem().open(uri);
        } catch (URISyntaxException e) {
            // Handle URI syntax error
        } catch (Exception e) {
            // Handle other errors (including from Util.getOperatingSystem().open())
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    private static class CachedName {
        @Nullable
        final String name;
        final long timestamp;

        CachedName(@Nullable String name, long timestamp) {
            this.name = name;
            this.timestamp = timestamp;
        }
    }
}

