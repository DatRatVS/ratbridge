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

A - Webhook delivery:
    . Messages are delivered as discord webhook messages
    (A1 - The webhook avatar must be the minecraft user's avatar with helm = `https://minotar.net/helm/[minecraft-name]`,
          Minotar supports .png `https://minotar.net/helm/[minecraft-name].png`
          Minotar supports custom image size `https://minotar.net/helm/[minecraft-name]/100` and comboes with png.
    )
    (A2 - The webhook message is just a clear mirror of the minecraft user's message, leaving it clean)
    (A3 - Webhook delivery just applies to messages, so death, leave, join or else are handled with normal messaging.)
    (A4 - STRICTLY PROHIBITED to be used with selfbot mode. FEATURE EXCLUSIVE TO DISCORD NORMAL BOT MODE.)

B - More events to listen:
    . Deaths
    . Advancements/achievenments

C - Toggles
    . Feature toggling in a config file such as:
    (C1 - Disable minecraft to discord chat but preserve other way.)
    (C2 - Disable discord to minecraft chat but preserve the other way.)
    [I think to those toggles above like, ah i want to just see the minecraft chat but not that
     the minecraft chat can see the discord chat.]

D - Channel topic changer/writer
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

E - Channel name changer/writer (similar to D)
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

F - Commands
    F1 - r!online
        . Shows online players, and deletes the message after a couple of seconds
        Message could be like:
        (Case has online players: `**%playercount% online player[s]:** player, player, player`)
        (Case no online players: `**No online players.**`)
        . These messages should be editable on messages.toml with placeholder support.
