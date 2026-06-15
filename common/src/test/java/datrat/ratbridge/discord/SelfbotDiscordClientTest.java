package datrat.ratbridge.discord;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SelfbotDiscordClientTest {
    @Test
    void channelWithoutGuildIdIsPrivateContext() {
        SelfbotDiscordClient.ChannelContext context = SelfbotDiscordClient.channelContextFrom(
                JsonParser.parseString("""
                        {
                          "id": "dm-channel",
                          "type": 1
                        }
                        """).getAsJsonObject()
        );

        assertTrue(context.privateMessage());
        assertEquals("", context.guildId());
    }

    @Test
    void channelWithGuildIdRequiresGuildContext() {
        SelfbotDiscordClient.ChannelContext context = SelfbotDiscordClient.channelContextFrom(
                JsonParser.parseString("""
                        {
                          "id": "guild-channel",
                          "type": 0,
                          "guild_id": "guild-1"
                        }
                        """).getAsJsonObject()
        );

        assertFalse(context.privateMessage());
        assertEquals("guild-1", context.guildId());
    }
}
