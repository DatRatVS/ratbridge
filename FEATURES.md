When commiting, use conventional commits.
Do not hesitate to do multiple commits for a single addition if you find suitable,
    would be good to keep track of stuff being added.

All updates have to be widely implemented, and for this i mean
Compatible with Forge, Neoforge, and Fabric, and implemented on them 3 before calling the task finished.

When finishing an section, or item, edit this markdown file and add a checkmark after it.

### [1 - Split config files] ✅

.
├── config/
│   └── ratbridge/
|       ├── messages.toml (Complete breakdown of every type 
|       |                  of message shown by the mod, so the user is able
|       |                  to change/translate every single one of them;
|       |                  Compatible with placeholders;
|       |                  Has a toggle for each listener as well. So if someone
|       |                  doesn't want to see join game messages, just flip it to false.)
|       |
|       └── config.toml   (holds main configurations like token, mode, client)
└── ...

Thats it for now i guess.. we could split it more later.

### [2 - Additions]

A - Webhook delivery: ✅
    . Messages are delivered as discord webhook messages
    (A1 - The webhook avatar must be the minecraft user's avatar with helm = `https://minotar.net/helm/[minecraft-name]`,
          Minotar supports .png `https://minotar.net/helm/[minecraft-name].png`
          Minotar supports custom image size `https://minotar.net/helm/[minecraft-name]/100` and comboes with png.
    )
    (A2 - The webhook message is just a clear mirror of the minecraft user's message, leaving it clean)
    (A3 - Webhook delivery just applies to messages, so death, leave, join or else are handled with normal messaging.)
    (A4 - STRICTLY PROHIBITED to be used with selfbot mode. FEATURE EXCLUSIVE TO DISCORD NORMAL BOT MODE.)

B - More events to listen: ✅
    . Deaths
    . Advancements/achievenments

C - Toggles: ✅
    . Feature toggling in a config file such as:
    (C1 - Disable minecraft to discord chat but preserve other way.)
    (C2 - Disable discord to minecraft chat but preserve the other way.)
    [I think to those toggles above like, ah i want to just see the minecraft chat but not that
     the minecraft chat can see the discord chat.]

D - Channel topic changer/writer: ✅
    . Writes information to the discord channel topic:
    ```
    Available placeholders:
    # %playercount%:   current player count
    # %playermax%:     maximum player count
    # %date%:          current date
    # %totalplayers%:  total amount of players to ever join the main world
    # %uptimemins%:    amount of minutes since DiscordSRV has started
    # %uptimehours%:   amount of hours since DiscordSRV has started
    # %motd%:          motto of the day of the server
    # %serverversion%: server version such as Spigot-1.9
    # %freememory%:    free memory of the JVM in MB
    # %usedmemory%:    used memory of the JVM in MB
    # %totalmemory%:   total memory of the JVM in MB
    # %maxmemory%:     max memory of the JVM in MB
    # %freememorygb%:  free memory of the JVM in GB
    # %usedmemorygb%:  used memory of the JVM in GB
    # %totalmemorygb%: total memory of the JVM in GB
    # %maxmemorygb%:   max memory of the JVM in GB
    # %tps%:           average TPS of the server
    # %time% or %date%: current date and time
    # %serverversion%:  server version
    # %totalplayers%:   total number of players to ever join the main world
    # %timestamp%:      current unix timestamp
    ```
      If there are other placeholders that you find cool to like feel free to add it!

E - Channel name changer/writer (similar to D): ✅
    . Instead of the topic, it changes the channel's name entirely supporting placeholders
      So a discord server could have a group session with voice channels having it's names updated
      To show cool informations. 
    ```
    Available placeholders:
    # %playercount%:   current player count
    # %playermax%:     maximum player count
    # %date%:          current date
    # %totalplayers%:  total amount of players to ever join the main world
    # %uptimemins%:    amount of minutes since DiscordSRV has started
    # %uptimehours%:   amount of hours since DiscordSRV has started
    # %motd%:          motto of the day of the server
    # %serverversion%: server version such as Spigot-1.9
    # %freememory%:    free memory of the JVM in MB
    # %usedmemory%:    used memory of the JVM in MB
    # %totalmemory%:   total memory of the JVM in MB
    # %maxmemory%:     max memory of the JVM in MB
    # %freememorygb%:  free memory of the JVM in GB
    # %usedmemorygb%:  used memory of the JVM in GB
    # %totalmemorygb%: total memory of the JVM in GB
    # %maxmemorygb%:   max memory of the JVM in GB
    # %tps%:           average TPS of the server
    # %time% or %date%: current date and time
    # %serverversion%:  server version
    # %totalplayers%:   total number of players to ever join the main world
    # %timestamp%:      current unix timestamp
    ```
      Should support all the custom placeholders you added from FEATURE D too.
    . Configuration should support adding how many channels the user wants to add with simple syntax (include harmless examples):
    ```
      ChannelUpdater:
    - ChannelId: "0000000000000000"
      Message: "%playercount% players online"
      ShutdownMessage: "Server is offline"
      UpdateInterval: 10
    - ChannelId: "0000000000000000"
      Message: "Current TPS: %tps%"
      ShutdownMessage: "Server is offline"
      UpdateInterval: 10

    `#   UpdateInterval: Time in minutes to wait between updating the channel's name (minimum is 10 due to rate limits)`
    `#   ShutdownMessage: The message the channel should take when the server has shut down. Retains the last informations from placeholders,
                         time placeholders retain the last ticked information`
    ```
      The codeblock above is an example for you, do not make it exactly as that please

F - Commands: ✅
    F1 - r!online
        . Should be sent in the channel the bot has the bridge enabled
        . Has a master switch that disables the command in the config files
        . Shows online players, and deletes the message after a couple of seconds
        Message could be like:
        (Case has online players: `**%playercount% online player[s]:** %player%, %player%, %player%`)
        (Case no online players: `**No online players.**`)
        . These messages should be editable on messages.toml with placeholder support.
    F2 = r!logout

G - Discord message reply to minecraft: ✅
    . Show in discord that a message from said user was replied to
    Probably like: `[Discord] %messager% replied to %replied-messager%: %message%`
    and editable in messages.toml with placeholder support.

K - Discord bot online status and status text: ✅
  K1 - Allow the user to personalize what says in the bot's PLAYING
  (Support all the modes, PLAYING, LISTENING, WATCHING, STREAMING... etc.)
  (Allow custom changes between time to time with a simple syntax similar to ChannelUpdater in (item E))

  K2 - Allow the user to change the bot's online status
  (DND, Away, Online, Invisible etc if there are more supported by discord)
  (Allow custom changes between time to time with a simple syntax similar to ChannelUpdater in (item E))

L - Put channel and topic updaters in their own .toml files: ✅
  . Self explanatory

M - Discord driven "whitelist" or better said, authentication: ✅
  . When enabled if the user isn't already in the authenticated list, the server auto disconnects the user
  . The disconnection screen message is a message (customizable with break line support) telling the user to send a six digit code to the bot's DM
  . With that six digit code the user just sends it to the bot's DM and the bot responds with a customizable message (but you make the default one)
  . Implement F2, a logout command that you can send to the bot's DM, that will force the player to initiate in the workflow again (and may or may not use another account)
  . Caution with account duplication, a minecraft account that has been authenticated, cannot authenticate with another discord account
    A authenticated discord account cannot be used to authenticate with another minecraft account as well.
    So 1 minecraft account is limited to 1 discord account and etc
  . Make it customizable please

N - Defaultize placeholders' special character to %: ✅
  . There are some placeholders that are using {} instead of defaulting to %%
  . There are placeholders in some files that doesn't have a list of which placeholders they can use.
    Please, include at least one list in every file, and if some placeholder that shows on the top of the .toml
    file isn't compatible with one string that is located below, specify it

O - Fixes: ✅
    O1 . r!logout should instantly disconnect the player from the server ✅
    O2 . With authentication toggled on, an unauthenticated user generates a *left the server* message
         in the bridge discord chat and it should not (It does not generate a *joined the server* message)
         Instead make a toggleable and customizeable message that sends in the chat once an unauthenticated
         user tries to log into the server. ✅
    O3 . Put `authentication-users.toml` inside of /data/ folder ✅
    O4 . Include a (toggleable, like a dropdown clickable item) table on the readme.md showing features available to bot and selfbot comparing them both ✅
    O5 . Seems like the selfbot mode really doesn't need a server id, even when needing to communicate in a server, but it should need. It should only ignore serverid if the chat is a DM or group DM ✅
