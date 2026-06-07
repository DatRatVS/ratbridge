<pre align="center">
   ___       __  ___       __    __      _                       ___
  / _ \___ _/ /_/ _ \___ _/ /_  / /_____(_)__ ___   _______  ___/ (_)__  ___ _
 / // / _ `/ __/ , _/ _ `/ __/ / __/ __/ / -_|_-<  / __/ _ \/ _  / / _ \/ _ `/
/____/\_,_/\__/_/|_|\_,_/\__/  \__/_/ /_/\__/___/  \__/\___/\_,_/_/_//_/\_, /
                                                                       /___/
</pre>

<p align="center">
  <img alt="Forge" src="https://img.shields.io/badge/Forge-555?style=for-the-badge">
  <img alt="1.20.1" src="https://img.shields.io/badge/1.20.1-555?style=for-the-badge">
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

A Minecraft `1.20.1` Forge server-side bridge for synchronizing Minecraft server chat and lifecycle events with Discord.

## Features

- **Server-Side Forge Mod**: Runs on dedicated Forge servers without requiring clients to install the mod.
- **Minecraft to Discord Chat**: Sends player chat messages from Minecraft to a configured Discord channel.
- **Discord to Minecraft Chat**: Broadcasts Discord messages back into Minecraft as server system messages.
- **Player Event Sync**: Sends player join and leave events to Discord.
- **Server Lifecycle Sync**: Sends server start and shutdown messages to Discord.
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

RatBridge generates:

```text
config/ratbridge.toml
```

Default config shape:

```toml
enabled = true
client = "discord"
mode = "bot"

token = ""
tokenEnv = "RATBRIDGE_DISCORD_TOKEN"

serverId = ""
channelId = ""

enableSelfbot = false
selfbotPollIntervalMillis = 750

syncChat = true
syncPlayerJoin = true
syncPlayerLeave = true
syncServerStart = true
syncServerStop = true

minecraftToDiscordFormat = "[MC] <{player}> {message}"
discordToMinecraftFormat = "[Discord] <{author}> {message}"
eventFormat = "[MC] {message}"
```

For bot mode:

- `token` or `tokenEnv` is required.
- `serverId` is required.
- `channelId` is required.

For selfbot mode:

- `enableSelfbot = true` is required.
- `token` or `tokenEnv` is required.
- `channelId` is required.
- `selfbotPollIntervalMillis` controls DM/Group DM polling delay. The minimum is `500`.

Invalid config disables the bridge and logs a clear error without crashing the Minecraft server.

## Commands

RatBridge registers one command:

```text
/ratbridge reload
```

The command requires permission level `2`.

Reload behavior:

- Stops the active bridge.
- Reloads `config/ratbridge.toml`.
- Validates the new config.
- Reconnects Discord asynchronously so the server thread does not wait on Discord login.

## Limits

- RatBridge currently targets Minecraft `1.20.1` and Forge `47.x`.
- Fabric, NeoForge, and newer Minecraft versions are planned as future ports, but the current branch is Forge-only.
- Selfbot mode uses polling. Lower polling intervals reduce delay but can hit Discord rate limits faster.
- Discord attachments are forwarded as URLs appended to the text message.
- Minecraft messages sent from Discord are server system messages, not fake player chat packets.

## Build Instructions

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --no-daemon clean build
```

The built JAR will be located at:

```text
build/libs/ratbridge-0.1.1.jar
```

Do not use the `-thin.jar` artifact on a server. It does not include the Discord runtime.
