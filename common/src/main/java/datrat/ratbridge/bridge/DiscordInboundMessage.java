package datrat.ratbridge.bridge;

public record DiscordInboundMessage(String author, String content, String replyAuthor) {
    public DiscordInboundMessage(String author, String content) {
        this(author, content, "");
    }

    public boolean hasReplyAuthor() {
        return BridgeConfig.hasText(replyAuthor);
    }
}
