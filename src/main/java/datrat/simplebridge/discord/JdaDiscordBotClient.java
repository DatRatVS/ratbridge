package datrat.simplebridge.discord;

import datrat.simplebridge.SimpleBridge;
import datrat.simplebridge.bridge.BridgeConfig;
import datrat.simplebridge.bridge.DiscordBridgeClient;
import datrat.simplebridge.bridge.DiscordInboundMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class JdaDiscordBotClient implements DiscordBridgeClient {
    private JDA jda;
    private MessageChannel targetChannel;

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
                    SimpleBridge.LOGGER.warn("Failed to send Discord bot message", error);
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
        }
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
}
