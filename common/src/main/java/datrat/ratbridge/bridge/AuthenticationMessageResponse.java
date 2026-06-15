package datrat.ratbridge.bridge;

import java.util.Optional;

public record AuthenticationMessageResponse(String replyMessage, Optional<AuthenticationLogout> logout) {
    public static AuthenticationMessageResponse reply(String replyMessage) {
        return new AuthenticationMessageResponse(replyMessage, Optional.empty());
    }

    public static AuthenticationMessageResponse logout(String replyMessage, AuthenticationLogout logout) {
        return new AuthenticationMessageResponse(replyMessage, Optional.of(logout));
    }
}
