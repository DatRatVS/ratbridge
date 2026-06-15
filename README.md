<pre align="center">
   ___       __  ___       __    __      _                       ___
  / _ \___ _/ /_/ _ \___ _/ /_  / /_____(_)__ ___   _______  ___/ (_)__  ___ _
 / // / _ `/ __/ , _/ _ `/ __/ / __/ __/ / -_|_-<  / __/ _ \/ _  / / _ \/ _ `/
/____/\_,_/\__/_/|_|\_,_/\__/  \__/_/ /_/\__/___/  \__/\___/\_,_/_/_//_/\_, /
                                                                       /___/
</pre>

<p align="center">
  <img alt="Forge" src="https://img.shields.io/badge/Forge-555?style=for-the-badge">
  <img alt="Fabric" src="https://img.shields.io/badge/Fabric-555?style=for-the-badge">
  <img alt="NeoForge" src="https://img.shields.io/badge/NeoForge-555?style=for-the-badge">
  <img alt="1.20.x" src="https://img.shields.io/badge/1.20.x-555?style=for-the-badge">
  <img alt="Discord" src="https://img.shields.io/badge/Discord-555?style=for-the-badge">
</p>

<p align="center">
  <a href="#features">Features</a> ·
  <a href="#discord-bridge">Discord Bridge</a> ·
  <a href="#config">Config</a> ·
  <a href="#commands">Commands</a> ·
  <a href="#limits">Limits</a> ·
  <a href="#build-instructions">Build Instructions</a>
</p>

# RatBridge

A Minecraft `1.20.x` server-side bridge for synchronizing Minecraft server chat and lifecycle events with Discord.

## Features

- **Server-Side Mod**: Runs on dedicated servers without requiring clients to install the mod.
- **Multi-Loader Targets**: Builds for Forge `1.20.1`, Fabric `1.20.1`, and NeoForge `1.20.2`.
- **Directional Chat Sync**: Separately toggles Minecraft -> Discord and Discord -> Minecraft chat sync.
- **Player Event Sync**: Sends player join, leave, death, and advancement events to Discord.
- **Server Lifecycle Sync**: Sends server start and shutdown messages to Discord.
- **Channel Topic Updater**: Updates a Discord channel topic with server status placeholders.
- **Channel Name Updaters**: Updates one or more Discord channel names with server status placeholders.
- **Bot Presence Rotation**: Updates Discord bot online status and activity text with server status placeholders.
- **Discord Commands**: Handles `r!online` in the bridge channel and temporary online-player responses.
- **Bot Mode**: Uses a normal Discord bot token through JDA.
- **Selfbot Mode**: Optional DM and Group DM polling mode using a user token and channel id.
- **Runtime Reload**: Adds `/ratbridge reload` for OPs to reload config and reconnect Discord without restarting the server.
- **Relocated Discord Runtime**: Bundles Discord dependencies under RatBridge's own package namespace to avoid common modpack module conflicts.

## Discord Bridge

RatBridge currently supports one bridge client:

```text
client = "discord"
```

Discord has two modes:

```text
mode = "bot"
mode = "selfbot"
```

Bot mode requires a Discord bot token, a guild/server id, and a channel id. The bot ignores messages from other bots to prevent loops.

Selfbot mode supports DMs and Group DMs through a channel id. It is disabled unless `enableSelfbot = true`.

Discord forbids automated normal user accounts/selfbots. Using selfbot mode can get the Discord account banned. RatBridge does not include bypass, evasion, anti-detection, or spam behavior.

## Config

RatBridge generates/uses:

```text
config/ratbridge/config.toml
config/ratbridge/messages.toml
config/ratbridge/topic-updater.toml
config/ratbridge/channel-updaters.toml
config/ratbridge/bot-presence.toml
config/ratbridge/authentication.toml
<world>/data/ratbridge/authentication-users.toml
```

`config.toml` holds connection/client settings:

```toml
# Master switch. Set false to keep the mod installed but disable bridge activity.
enabled = true

# External client. Currently supported: "discord".
client = "discord"

# Discord mode: "bot" for a normal Discord bot, "selfbot" for DM/Group DM polling.
# Selfbot mode can get the Discord account banned.
mode = "bot"

# Paste a token here, or leave empty and use tokenEnv.
token = ""

# Environment variable name used when token is empty.
tokenEnv = "RATBRIDGE_DISCORD_TOKEN"

# Required in bot mode. Copy it from Discord developer mode.
serverId = ""

# Required in bot and selfbot mode. Bot mode uses a server text channel; selfbot uses DM/Group DM channel IDs.
channelId = ""

# Required safety gate for selfbot mode.
enableSelfbot = false

# Selfbot polling delay in milliseconds. Minimum: 500.
selfbotPollIntervalMillis = 750

# Send Minecraft player chat through a Discord webhook.
# Requires mode = "bot" and Discord Manage Webhooks permission.
# Strictly unavailable in selfbot mode.
webhookDelivery = false

# Webhook name RatBridge creates/reuses in the Discord channel.
webhookName = "RatBridge"
```

`topic-updater.toml` holds the optional Discord channel topic updater:

```toml
# Discord channel topic updater. Bot mode only; requires Manage Channels permission.
topicUpdaterEnabled = false

# Leave empty to update the same Discord channel used by channelId.
topicUpdaterChannelId = ""

# Online topic text. Available placeholders include:
# %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %tps%, %date%, %time%, %datetime%, %timestamp%
# Memory placeholders: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%, %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%
topicUpdaterMessage = "Players: %playercount%/%playermax% | TPS: %tps% | Uptime: %uptimemins%m"

# Topic applied when the server shuts down.
topicUpdaterShutdownMessage = "Server is offline"

# Minutes between topic updates. Minimum: 5; 6+ is recommended to avoid Discord rate limits.
topicUpdaterIntervalMinutes = 6
```

`channel-updaters.toml` holds optional Discord channel name updaters:

```toml
# Discord channel name updaters. Bot mode only; requires Manage Channels permission.
# Master switch. Set to true to enable every [[ChannelUpdater]] block below.
channelNameUpdatersEnabled = false

# Add one [[ChannelUpdater]] block for each Discord channel name RatBridge should update.
# Available placeholders include:
# %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %tps%, %date%, %time%, %datetime%, %timestamp%
# Memory placeholders: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%, %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%
# Minimum update interval is 5 minutes; 6+ is recommended because Discord rate-limits channel renames.
# ChannelId: Discord channel ID to rename.
# Message: Channel name while the Minecraft server is online.
# ShutdownMessage: Channel name applied while the Minecraft server is stopping.
# UpdateInterval: Minutes between channel name updates.

# Example:
# [[ChannelUpdater]]
# ChannelId = "000000000000000000"
# Message = "%playercount% players online"
# ShutdownMessage = "Server is offline"
# UpdateInterval = 6
#
# [[ChannelUpdater]]
# ChannelId = "000000000000000000"
# Message = "TPS %tps%"
# ShutdownMessage = "Server is offline"
# UpdateInterval = 6
```

`bot-presence.toml` holds optional Discord bot status and activity rotation:

```toml
# Discord bot presence updater. Bot mode only.
# Add one [[Presence]] block for each status/activity RatBridge should rotate through.
# Master switch. Set to true to enable every [[Presence]] block below.
botPresenceEnabled = false

# If this file has no [[Presence]] blocks, bot presence updates are disabled.
# OnlineStatus: ONLINE, IDLE, AWAY, DND, DO_NOT_DISTURB, or INVISIBLE.
# ActivityType: PLAYING, LISTENING, WATCHING, STREAMING, COMPETING, or CUSTOM.
# Activity: text shown in the bot activity. Supports the same placeholders as topic/channel updaters.
# Available placeholders include:
# %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %tps%, %date%, %time%, %datetime%, %timestamp%
# Memory placeholders: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%, %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%
# StreamUrl: required only when ActivityType = STREAMING.
# UpdateInterval: seconds before RatBridge moves to the next block. Minimum: 30.

# Example:
# [[Presence]]
# OnlineStatus = "ONLINE"
# ActivityType = "PLAYING"
# Activity = "%playercount%/%playermax% players"
# UpdateInterval = 60
#
# [[Presence]]
# OnlineStatus = "DND"
# ActivityType = "WATCHING"
# Activity = "TPS %tps%"
# UpdateInterval = 60
```

`authentication.toml` controls optional Discord-driven Minecraft authentication:

```toml
# Master switch for Discord-driven authentication.
authenticationEnabled = false

# Minutes before an unused join code expires.
authenticationCodeTtlMinutes = 10

# Sends a Discord event when an unauthenticated player attempts to join.
authenticationUnauthenticatedLoginMessageEnabled = true

# Discord event text for unauthenticated join attempts.
# Placeholders: %player%, %uuid%
authenticationUnauthenticatedLoginMessage = "%player% tried to join but is not authenticated yet."

# Minecraft disconnect screen text. Use \n for line breaks.
# Placeholders: %player%, %uuid%, %code%, %logoutcommand%
authenticationKickMessage = "This server requires Discord authentication.\nSend code %code% to the RatBridge bot DM to authenticate %player%."

# Discord DM responses.
authenticationSuccessMessage = "Authenticated %player%. You can now join the server."
authenticationInvalidCodeMessage = "Invalid or expired authentication code."
authenticationAlreadyLinkedMessage = "That Minecraft or Discord account is already linked to another account."

# Discord DM command that removes the current link.
authenticationLogoutCommand = "r!logout"
authenticationLogoutSuccessMessage = "Your Minecraft account link was removed. Join the server again to get a new code."
authenticationLogoutNotLinkedMessage = "Your Discord account is not linked to any Minecraft account."
```

When authentication is enabled, RatBridge stores accepted links in `<world>/data/ratbridge/authentication-users.toml`. That file is managed by the mod. Existing `config/ratbridge/authentication-users.toml` files are migrated automatically when possible. One Minecraft account can link to one Discord account, and one Discord account can link to one Minecraft account.

`messages.toml` holds listener toggles and editable message text:

RatBridge defaults to `%placeholder%` syntax. Existing configs that still use `{placeholder}` remain supported for message formats.

```toml
# Toggles for each synced listener/event type.
# syncChat is the legacy master chat switch; false disables both chat directions.
syncChat = true

# Directional chat switches. These let you keep one side readable while muting the other side.
syncMinecraftToDiscordChat = true
syncDiscordToMinecraftChat = true
syncPlayerJoin = true
syncPlayerLeave = true
syncPlayerDeath = true
syncPlayerAdvancement = true
syncServerStart = true
syncServerStop = true

# Discord text commands handled in the configured bridge channel.
commandsEnabled = true
onlineCommand = "r!online"
onlineCommandDeleteAfterSeconds = 10
onlinePlayersMessage = "**%playercount% online player%playerplural%:** %players%"
onlineNoPlayersMessage = "**No online players.**"

# Minecraft -> Discord chat. Placeholders: %player%, %message%
minecraftToDiscordFormat = "[MC] <%player%> %message%"

# Discord -> Minecraft chat. Placeholders: %author%, %message%
discordToMinecraftFormat = "[Discord] <%author%> %message%"

# Discord reply -> Minecraft chat. Placeholders: %author%, %replyauthor%, %message%
discordReplyToMinecraftFormat = "[Discord] <%author%> replied to <%replyauthor%>: %message%"

# Wrapper for event messages. Placeholder: %message%
eventFormat = "[MC] %message%"

# Join/leave event text. Placeholder: %player%
playerJoinMessage = "%player% joined the game"
playerLeaveMessage = "%player% left the game"

# Death event text. Placeholders: %player%, %message%
playerDeathMessage = "%message%"

# Advancement event text. Placeholders: %player%, %advancement%, %description%
playerAdvancementMessage = "%player% has made the advancement [%advancement%]"

# Server lifecycle event text.
serverStartMessage = "Server started"
serverStopMessage = "Server stopping"
```

If an old `config/ratbridge.toml` exists and the split files do not, RatBridge seeds the new files from the legacy values.

For bot mode:

- `token` or `tokenEnv` is required.
- `serverId` is required.
- `channelId` is required.

For selfbot mode:

- `enableSelfbot = true` is required.
- `token` or `tokenEnv` is required.
- `channelId` is required.
- `selfbotPollIntervalMillis` controls DM/Group DM polling delay. The minimum is `500`.

For webhook delivery:

- `webhookDelivery = true` only works in normal bot mode.
- The bot needs permission to manage webhooks in the configured channel.
- Minecraft player chat is mirrored cleanly through the webhook using the player name and Minotar helm avatar.
- Join, leave, start, and stop events still use normal bot messages.
- Selfbot mode is explicitly blocked from webhook delivery.

Invalid config disables the bridge and logs a clear error without crashing the Minecraft server.

## Commands

RatBridge registers one Minecraft server command:

```text
/ratbridge reload
```

The command requires permission level `2`.

Reload behavior:

- Stops the active bridge.
- Reloads the RatBridge split config files, including `authentication.toml`.
- Validates the new config.
- Reconnects Discord asynchronously so the server thread does not wait on Discord login.

RatBridge also handles Discord commands:

- `r!online` must be sent in the configured bridge channel. It responds with the current online Minecraft players and deletes the response after `onlineCommandDeleteAfterSeconds`.
- `r!logout` must be sent in the bot DM. It removes the current Discord account link when authentication is enabled.

## Limits

- RatBridge currently targets Forge `1.20.1`, Fabric `1.20.1`, and NeoForge `1.20.2`.
- The NeoForge build avoids NeoForm in this branch and compiles against the existing Mojmap Minecraft classpath because NeoGradle was producing empty Mojang jars in this environment.
- Newer Minecraft versions are intended to live as small platform modules on top of the shared `common` code.
- Selfbot mode uses polling. Lower polling intervals reduce delay but can hit Discord rate limits faster.
- Discord attachments are forwarded as URLs appended to the text message.
- Minecraft messages sent from Discord are server system messages, not fake player chat packets.

## Build Instructions

Prefer targeted builds on low-memory desktops:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --no-daemon --max-workers=1 --configure-on-demand :common:test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --no-daemon --max-workers=1 --configure-on-demand :forge-1.20.1:reobfShadowJar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --no-daemon --max-workers=1 --configure-on-demand :fabric-1.20.1:remapJar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --no-daemon --max-workers=1 --configure-on-demand :neoforge-1.20.2:shadowJar
```

The built JAR will be located at:

```text
forge-1.20.1/build/libs/ratbridge-forge-1.20.1-0.1.16.jar
fabric-1.20.1/build/libs/ratbridge-fabric-1.20.1-0.1.16.jar
neoforge-1.20.2/build/libs/ratbridge-neoforge-1.20.2-0.1.16.jar
```

Do not use the `-thin.jar` artifact on a server. It does not include the Discord runtime.
