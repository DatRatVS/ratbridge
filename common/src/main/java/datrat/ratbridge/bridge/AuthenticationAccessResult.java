package datrat.ratbridge.bridge;

public record AuthenticationAccessResult(boolean allowed, String denyMessage) {
    public static AuthenticationAccessResult allow() {
        return new AuthenticationAccessResult(true, "");
    }

    public static AuthenticationAccessResult deny(String denyMessage) {
        return new AuthenticationAccessResult(false, denyMessage == null ? "" : denyMessage);
    }
}
