package datrat.simplebridge.platform.forge;

import datrat.simplebridge.bridge.BridgeConfig;
import net.minecraftforge.common.ForgeConfigSpec;

public final class SimpleBridgeForgeConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> CLIENT;
    private static final ForgeConfigSpec.ConfigValue<String> MODE;
    private static final ForgeConfigSpec.ConfigValue<String> TOKEN;
    private static final ForgeConfigSpec.ConfigValue<String> TOKEN_ENV;
    private static final ForgeConfigSpec.ConfigValue<String> SERVER_ID;
    private static final ForgeConfigSpec.ConfigValue<String> CHANNEL_ID;
    private static final ForgeConfigSpec.BooleanValue ENABLE_SELF_BOT;
    private static final ForgeConfigSpec.BooleanValue SYNC_CHAT;
    private static final ForgeConfigSpec.BooleanValue SYNC_PLAYER_JOIN;
    private static final ForgeConfigSpec.BooleanValue SYNC_PLAYER_LEAVE;
    private static final ForgeConfigSpec.BooleanValue SYNC_SERVER_START;
    private static final ForgeConfigSpec.BooleanValue SYNC_SERVER_STOP;
    private static final ForgeConfigSpec.ConfigValue<String> MINECRAFT_TO_DISCORD_FORMAT;
    private static final ForgeConfigSpec.ConfigValue<String> DISCORD_TO_MINECRAFT_FORMAT;
    private static final ForgeConfigSpec.ConfigValue<String> EVENT_FORMAT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        ENABLED = builder.comment("Enable or disable Simple Bridge without removing the mod.").define("enabled", true);
        CLIENT = builder.comment("Client to bridge with. Initial supported value: discord.").define("client", "discord");
        MODE = builder.comment("Discord mode: bot or selfbot. Selfbot can violate Discord terms and can get the account banned.").define("mode", "bot");
        TOKEN = builder.comment("Discord token. Prefer tokenEnv on shared servers.").define("token", "");
        TOKEN_ENV = builder.comment("Environment variable used when token is empty.").define("tokenEnv", "SIMPLEBRIDGE_DISCORD_TOKEN");
        SERVER_ID = builder.comment("Discord guild/server ID. Required in bot mode.").define("serverId", "");
        CHANNEL_ID = builder.comment("Discord channel ID. Required in bot mode and selfbot mode. Selfbot supports DM and Group DM channel IDs.").define("channelId", "");
        ENABLE_SELF_BOT = builder.comment("Explicit opt-in for selfbot mode. Discord forbids selfbots and the account can be banned.").define("enableSelfbot", false);

        SYNC_CHAT = builder.define("syncChat", true);
        SYNC_PLAYER_JOIN = builder.define("syncPlayerJoin", true);
        SYNC_PLAYER_LEAVE = builder.define("syncPlayerLeave", true);
        SYNC_SERVER_START = builder.define("syncServerStart", true);
        SYNC_SERVER_STOP = builder.define("syncServerStop", true);

        MINECRAFT_TO_DISCORD_FORMAT = builder.define("minecraftToDiscordFormat", "[MC] <{player}> {message}");
        DISCORD_TO_MINECRAFT_FORMAT = builder.define("discordToMinecraftFormat", "[Discord] <{author}> {message}");
        EVENT_FORMAT = builder.define("eventFormat", "[MC] {message}");

        SPEC = builder.build();
    }

    private SimpleBridgeForgeConfig() {
    }

    public static BridgeConfig snapshot() {
        return new BridgeConfig(
                ENABLED.get(),
                CLIENT.get(),
                MODE.get(),
                TOKEN.get(),
                TOKEN_ENV.get(),
                SERVER_ID.get(),
                CHANNEL_ID.get(),
                ENABLE_SELF_BOT.get(),
                SYNC_CHAT.get(),
                SYNC_PLAYER_JOIN.get(),
                SYNC_PLAYER_LEAVE.get(),
                SYNC_SERVER_START.get(),
                SYNC_SERVER_STOP.get(),
                MINECRAFT_TO_DISCORD_FORMAT.get(),
                DISCORD_TO_MINECRAFT_FORMAT.get(),
                EVENT_FORMAT.get()
        );
    }
}
