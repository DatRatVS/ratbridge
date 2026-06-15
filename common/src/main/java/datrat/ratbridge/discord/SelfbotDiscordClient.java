package datrat.ratbridge.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.DiscordBridgeClient;
import datrat.ratbridge.bridge.DiscordInboundMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SelfbotDiscordClient implements DiscordBridgeClient {
    private static final String API_BASE = "https://discord.com/api/v10";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("RatBridge");

    private HttpClient http;
    private ScheduledExecutorService poller;
    private ScheduledExecutorService temporaryMessageDeleter;
    private BridgeConfig config;
    private Consumer<DiscordInboundMessage> inboundConsumer;
    private String token;
    private String selfUserId;
    private String lastMessageId;
    private ChannelContext channelContext = ChannelContext.privateChannel();

    @Override
    public void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) throws Exception {
        LOGGER.warn("RatBridge selfbot mode uses a normal Discord user token. Discord forbids selfbots and the account can be banned.");
        this.config = config;
        this.inboundConsumer = inboundConsumer;
        this.token = config.resolvedToken(System::getenv);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.selfUserId = fetchSelfUserId();
        this.channelContext = fetchChannelContext();
        validateChannelContext(config, channelContext);
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RatBridge-Selfbot-Poller");
            thread.setDaemon(true);
            return thread;
        });
        this.temporaryMessageDeleter = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RatBridge-Selfbot-Temporary-Message-Deleter");
            thread.setDaemon(true);
            return thread;
        });
        this.poller.scheduleWithFixedDelay(
                this::pollMessagesSafely,
                0,
                config.selfbotPollIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public CompletableFuture<Void> sendMessage(String message) {
        return sendChannelMessage(config.channelId(), message);
    }

    @Override
    public CompletableFuture<Void> sendDirectMessage(String userId, String channelId, String message) {
        String targetChannelId = BridgeConfig.hasText(channelId) ? channelId : config.channelId();
        return sendChannelMessage(targetChannelId, message);
    }

    private CompletableFuture<Void> sendChannelMessage(String channelId, String message) {
        if (message.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", message);
        HttpRequest request = baseRequest(channelUri(channelId, "/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LOGGER.warn("Discord selfbot send failed with HTTP {}", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    LOGGER.warn("Discord selfbot send failed", error);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> sendTemporaryMessage(String channelId, String message, Duration deleteAfter) {
        String targetChannelId = BridgeConfig.hasText(channelId) ? channelId : config.channelId();
        if (message.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        JsonObject body = new JsonObject();
        body.addProperty("content", message);
        HttpRequest request = baseRequest(channelUri(targetChannelId, "/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LOGGER.warn("Discord selfbot temporary send failed with HTTP {}", response.statusCode());
                        return;
                    }
                    String messageId = getString(JsonParser.parseString(response.body()).getAsJsonObject(), "id");
                    if (BridgeConfig.hasText(messageId) && temporaryMessageDeleter != null) {
                        temporaryMessageDeleter.schedule(
                                () -> deleteMessageSafely(targetChannelId, messageId),
                                Math.max(1L, deleteAfter.toSeconds()),
                                TimeUnit.SECONDS
                        );
                    }
                })
                .exceptionally(error -> {
                    LOGGER.warn("Discord selfbot temporary send failed", error);
                    return null;
                });
    }

    @Override
    public void close() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
        if (temporaryMessageDeleter != null) {
            temporaryMessageDeleter.shutdownNow();
            temporaryMessageDeleter = null;
        }
    }

    private String fetchSelfUserId() throws IOException, InterruptedException {
        HttpRequest request = baseRequest(URI.create(API_BASE + "/users/@me"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Discord selfbot login failed with HTTP " + response.statusCode());
        }
        JsonObject user = JsonParser.parseString(response.body()).getAsJsonObject();
        return user.get("id").getAsString();
    }

    private void pollMessagesSafely() {
        try {
            pollMessages();
        } catch (Exception error) {
            LOGGER.warn("Discord selfbot poll failed", error);
        }
    }

    private void pollMessages() throws IOException, InterruptedException {
        URI uri = channelUri("/messages?limit=10");
        HttpRequest request = baseRequest(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            LOGGER.warn("Discord selfbot polling is rate limited");
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.warn("Discord selfbot poll failed with HTTP {}", response.statusCode());
            return;
        }

        JsonArray messages = JsonParser.parseString(response.body()).getAsJsonArray();
        if (messages.isEmpty()) {
            return;
        }

        if (lastMessageId == null) {
            lastMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
            return;
        }

        List<JsonObject> unseen = new ArrayList<>();
        for (JsonElement element : messages) {
            JsonObject message = element.getAsJsonObject();
            String messageId = message.get("id").getAsString();
            if (messageId.equals(lastMessageId)) {
                break;
            }
            unseen.add(message);
        }

        if (unseen.isEmpty()) {
            return;
        }

        Collections.sort(unseen, Comparator.comparing(message -> message.get("id").getAsString()));
        for (JsonObject message : unseen) {
            consumeMessage(message);
        }
        lastMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
    }

    private void consumeMessage(JsonObject message) {
        JsonObject author = message.getAsJsonObject("author");
        if (author != null && selfUserId.equals(author.get("id").getAsString())) {
            return;
        }

        String content = getString(message, "content");
        JsonArray attachments = message.getAsJsonArray("attachments");
        if (attachments != null && !attachments.isEmpty()) {
            StringBuilder builder = new StringBuilder(content);
            for (JsonElement element : attachments) {
                String url = getString(element.getAsJsonObject(), "url");
                if (!url.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(url);
                }
            }
            content = builder.toString();
        }

        if (content.isBlank()) {
            return;
        }

        String authorName = author == null ? "Discord" : displayName(author);
        String replyAuthorName = referencedAuthorName(message);
        String authorId = author == null ? "" : getString(author, "id");
        inboundConsumer.accept(new DiscordInboundMessage(authorName, content, replyAuthorName, authorId, config.channelId(), channelContext.privateMessage(), true));
    }

    private ChannelContext fetchChannelContext() throws IOException, InterruptedException {
        HttpRequest request = baseRequest(channelUri(""))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Discord selfbot channel lookup failed with HTTP " + response.statusCode());
        }
        return channelContextFrom(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private static void validateChannelContext(BridgeConfig config, ChannelContext context) {
        if (context.privateMessage()) {
            return;
        }
        if (!BridgeConfig.hasText(config.serverId())) {
            throw new IllegalStateException("serverId is required in selfbot mode when channelId points to a Discord server/guild channel");
        }
        if (!config.serverId().trim().equals(context.guildId())) {
            throw new IllegalStateException("Discord selfbot channel belongs to guild/server " + context.guildId()
                    + " but serverId is " + config.serverId().trim());
        }
    }

    static ChannelContext channelContextFrom(JsonObject channel) {
        String guildId = getString(channel, "guild_id");
        if (BridgeConfig.hasText(guildId)) {
            return new ChannelContext(false, guildId);
        }
        return ChannelContext.privateChannel();
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("Authorization", token)
                .header("User-Agent", "RatBridge/0.1.18");
    }

    private URI channelUri(String suffix) {
        return channelUri(config.channelId(), suffix);
    }

    private URI channelUri(String channelId, String suffix) {
        channelId = URLEncoder.encode(channelId, StandardCharsets.UTF_8);
        return URI.create(API_BASE + "/channels/" + channelId + suffix);
    }

    private void deleteMessageSafely(String channelId, String messageId) {
        try {
            HttpRequest request = baseRequest(channelUri(channelId, "/messages/" + URLEncoder.encode(messageId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            LOGGER.warn("Discord selfbot temporary delete failed with HTTP {}", response.statusCode());
                        }
                    })
                    .exceptionally(error -> {
                        LOGGER.warn("Discord selfbot temporary delete failed", error);
                        return null;
                    });
        } catch (Exception error) {
            LOGGER.warn("Discord selfbot temporary delete failed", error);
        }
    }

    private static String displayName(JsonObject author) {
        String globalName = getString(author, "global_name");
        if (!globalName.isBlank()) {
            return globalName;
        }
        String username = getString(author, "username");
        return username.isBlank() ? "Discord" : username;
    }

    private static String referencedAuthorName(JsonObject message) {
        if (message == null || !message.has("referenced_message") || message.get("referenced_message").isJsonNull()) {
            return "";
        }
        JsonObject referenced = message.getAsJsonObject("referenced_message");
        JsonObject author = referenced.getAsJsonObject("author");
        return author == null ? "" : displayName(author);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    record ChannelContext(boolean privateMessage, String guildId) {
        private static ChannelContext privateChannel() {
            return new ChannelContext(true, "");
        }
    }
}
