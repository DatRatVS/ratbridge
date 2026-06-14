package datrat.ratbridge.bridge;

public record AuthenticationDecision(boolean allowed, String disconnectMessage) {
    public static AuthenticationDecision allow() {
        return new AuthenticationDecision(true, "");
    }

    public static AuthenticationDecision deny(String disconnectMessage) {
        return new AuthenticationDecision(false, disconnectMessage);
    }
}
