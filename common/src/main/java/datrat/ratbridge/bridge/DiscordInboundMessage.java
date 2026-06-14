package datrat.ratbridge.bridge;

public record DiscordInboundMessage(
        String author,
        String content,
        String replyAuthor,
        String authorId,
        String channelId,
        boolean privateMessage,
        boolean bridgeToMinecraft
) {
    public DiscordInboundMessage(String author, String content) {
        this(author, content, "");
    }

    public DiscordInboundMessage(String author, String content, String replyAuthor) {
        this(author, content, replyAuthor, "", "", false, true);
    }

    public DiscordInboundMessage(
            String author,
            String content,
            String replyAuthor,
            String authorId,
            String channelId,
            boolean privateMessage,
            boolean bridgeToMinecraft
    ) {
        this.author = author == null ? "" : author;
        this.content = content == null ? "" : content;
        this.replyAuthor = replyAuthor == null ? "" : replyAuthor;
        this.authorId = authorId == null ? "" : authorId;
        this.channelId = channelId == null ? "" : channelId;
        this.privateMessage = privateMessage;
        this.bridgeToMinecraft = bridgeToMinecraft;
    }

    public boolean hasReplyAuthor() {
        return BridgeConfig.hasText(replyAuthor);
    }
}
