package datrat.ratbridge.discord;

import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.DiscordBridgeClient;
import datrat.ratbridge.bridge.DiscordInboundMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdaDiscordBotClient implements DiscordBridgeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("RatBridge");

    private JDA jda;
    private MessageChannel targetChannel;
    private WebhookClient<Message> webhookClient;

    @Override
    public void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) throws Exception {
        String token = config.resolvedToken(System::getenv);
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);

        this.jda = JDABuilder.createLight(token, intents)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    public void onMessageReceived(MessageReceivedEvent event) {
                        handleMessage(config, inboundConsumer, event);
                    }
                })
                .build();

        this.jda.awaitReady();
        if (jda.getGuildById(config.serverId()) == null) {
            throw new IllegalStateException("Discord guild/server not found or unavailable: " + config.serverId());
        }

        this.targetChannel = jda.getChannelById(MessageChannel.class, config.channelId());
        if (targetChannel == null) {
            throw new IllegalStateException("Discord text channel not found or unavailable: " + config.channelId());
        }
        if (config.webhookDelivery()) {
            this.webhookClient = resolveWebhookClient(config);
        }
    }

    @Override
    public CompletableFuture<Void> sendMessage(String message) {
        if (targetChannel == null || message.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        targetChannel.sendMessage(message).queue(
                sent -> future.complete(null),
                error -> {
                    LOGGER.warn("Failed to send Discord bot message", error);
                    future.complete(null);
                }
        );
        return future;
    }

    @Override
    public CompletableFuture<Void> sendMinecraftChatMessage(String player, String message) {
        if (webhookClient == null || message.isBlank()) {
            return sendMessage(message);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        webhookClient.sendMessage(message)
                .setUsername(webhookUsername(player))
                .setAvatarUrl(minotarHelmAvatarUrl(player))
                .queue(
                        sent -> future.complete(null),
                        error -> {
                            LOGGER.warn("Failed to send Discord webhook message", error);
                            future.complete(null);
                        }
                );
        return future;
    }

    @Override
    public CompletableFuture<Void> updateChannelTopic(String channelId, String topic) {
        if (jda == null || !BridgeConfig.hasText(channelId)) {
            return CompletableFuture.completedFuture(null);
        }
        StandardGuildMessageChannel channel = jda.getChannelById(StandardGuildMessageChannel.class, channelId);
        if (channel == null) {
            LOGGER.warn("Discord topic channel not found or does not support topics: {}", channelId);
            return CompletableFuture.completedFuture(null);
        }

        String boundedTopic = boundedTopic(topic);
        if (boundedTopic.equals(channel.getTopic())) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.getManager().setTopic(boundedTopic).queue(
                ignored -> future.complete(null),
                error -> {
                    LOGGER.warn("Failed to update Discord channel topic", error);
                    future.complete(null);
                }
        );
        return future;
    }

    @Override
    public CompletableFuture<Void> updateChannelName(String channelId, String name) {
        if (jda == null || !BridgeConfig.hasText(channelId)) {
            return CompletableFuture.completedFuture(null);
        }
        GuildChannel channel = jda.getChannelById(GuildChannel.class, channelId);
        if (channel == null) {
            LOGGER.warn("Discord channel not found for name updater: {}", channelId);
            return CompletableFuture.completedFuture(null);
        }

        String boundedName = boundedChannelName(name);
        if (boundedName.equals(channel.getName())) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.getManager().setName(boundedName).queue(
                ignored -> future.complete(null),
                error -> {
                    LOGGER.warn("Failed to update Discord channel name", error);
                    future.complete(null);
                }
        );
        return future;
    }

    @Override
    public void close() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
            targetChannel = null;
            webhookClient = null;
        }
    }

    private WebhookClient<Message> resolveWebhookClient(BridgeConfig config) {
        if (!(targetChannel instanceof IWebhookContainer webhookContainer)) {
            throw new IllegalStateException("Discord channel does not support webhooks: " + config.channelId());
        }

        List<Webhook> webhooks = webhookContainer.retrieveWebhooks().complete();
        return webhooks.stream()
                .filter(webhook -> webhook.getType() == WebhookType.INCOMING)
                .filter(webhook -> BridgeConfig.hasText(webhook.getToken()))
                .filter(webhook -> webhook.getName().equals(config.webhookName()))
                .sorted(Comparator.comparing(Webhook::getId))
                .findFirst()
                .orElseGet(() -> webhookContainer.createWebhook(config.webhookName()).complete());
    }

    private static void handleMessage(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer, MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        if (!event.getGuild().getId().equals(config.serverId())) {
            return;
        }
        if (!event.getChannel().getId().equals(config.channelId())) {
            return;
        }
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();
        String attachments = message.getAttachments().stream()
                .map(Message.Attachment::getUrl)
                .collect(Collectors.joining(" "));
        if (!attachments.isBlank()) {
            content = content.isBlank() ? attachments : content + " " + attachments;
        }
        if (!content.isBlank()) {
            inboundConsumer.accept(new DiscordInboundMessage(event.getAuthor().getEffectiveName(), content));
        }
    }

    private static String webhookUsername(String player) {
        if (player == null || player.isBlank()) {
            return "Minecraft";
        }
        return player.length() > 80 ? player.substring(0, 80) : player;
    }

    private static String minotarHelmAvatarUrl(String player) {
        String encoded = URLEncoder.encode(webhookUsername(player), StandardCharsets.UTF_8);
        return "https://minotar.net/helm/" + encoded + ".png";
    }

    private static String boundedTopic(String topic) {
        if (topic == null) {
            return "";
        }
        int maxLength = StandardGuildMessageChannel.MAX_TOPIC_LENGTH;
        return topic.length() <= maxLength ? topic : topic.substring(0, maxLength);
    }

    private static String boundedChannelName(String name) {
        String fallback = name == null || name.isBlank() ? "ratbridge" : name.trim();
        int maxLength = net.dv8tion.jda.api.entities.channel.Channel.MAX_NAME_LENGTH;
        return fallback.length() <= maxLength ? fallback : fallback.substring(0, maxLength);
    }
}
