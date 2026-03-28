# NeoChat

NeoChat is an advanced chat plugin for Paper-compatible Minecraft servers focused on rich formatting, interactive placeholders, private messages, moderation tools, and Folia-aware execution.

## Features

- Rich chat formatting powered by Adventure and MiniMessage
- Mention system with `@player` ping highlighting and configurable sound notifications
- Clickable link formatting
- Interactive chat placeholders such as `[item]`, `[inv]`, `[ender]`, `[shulker]`, `[ping]`, `[pos]`, `[money]`, and `[playtime]`
- Private messages with `/msg`, `/reply`, `/togglemsg`, and `/socialspy`
- Ignore system with per-player ignore and ignore-all mode
- Global chat mute commands
- Optional Towny chat integration
- Optional Discord webhook relay
- Anti-spam, anti-flood, anti-caps, anti-similarity, anti-swear, and character filtering
- Folia-aware scheduling for chat, PMs, sounds, inventory viewers, Towny chat, and update checks

## Requirements

- Java 17+
- A Paper-compatible server
- Built against `paper-api 1.19.4-R0.1-SNAPSHOT`

Optional integrations:

- PlaceholderAPI
- ItemsAdder
- Towny

## Installation

1. Download the latest NeoChat jar from your release page.
2. Place it in your server's `plugins/` folder.
3. Start the server once to generate the configuration files.
4. Edit `config.yml`, `messages.yml`, and `formats.yml` as needed.
5. Reload plugin.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/neochat reload` | Reloads plugin configuration and messages | `neochat.admin.reload` |
| `/chatmute` | Mutes global chat | `neochat.admin.mute` |
| `/chatunmute` | Unmutes global chat | `neochat.admin.mute` |
| `/msg <player> <message>` | Sends a private message | `neochat.pm.use` |
| `/reply <message>` | Replies to the last private message | `neochat.pm.use` |
| `/togglemsg` | Toggles private messages on or off | `neochat.pm.toggle` |
| `/socialspy` | Toggles SocialSpy | `neochat.admin.spy` |
| `/ignore <player>` | Ignores or unignores a player | `neochat.ignore.use` |
| `/ignoreall` | Toggles ignore-all mode | `neochat.ignore.use` |
| `/tc [message]` | Toggles Towny chat or sends a town message | `neochat.command.townychat` |

## Main Permissions

| Permission | Description |
| --- | --- |
| `neochat.admin.reload` | Allows reloading NeoChat |
| `neochat.admin.mute` | Allows muting and unmuting global chat |
| `neochat.admin.spy` | Allows using SocialSpy |
| `neochat.pm.use` | Allows private messaging |
| `neochat.pm.toggle` | Allows toggling private messages |
| `neochat.pm.bypass` | Bypasses private-message blocks on targets |
| `neochat.ignore.use` | Allows ignore commands |
| `neochat.ignore.bypass` | Prevents being ignored |
| `neochat.command.townychat` | Allows Towny chat command usage |
| `neochat.bypass.mute` | Bypasses global chat mute |
| `neochat.bypass.cooldown` | Bypasses chat cooldown |
| `neochat.bypass.flood` | Bypasses anti-flood |
| `neochat.bypass.similarity` | Bypasses similarity checks |
| `neochat.bypass.caps` | Bypasses anti-caps |
| `neochat.bypass.swear` | Bypasses swear filtering |
| `neochat.bypass.alphanumeric` | Bypasses character filtering |
| `neochat.chat.color` | Allows MiniMessage colors in chat |
| `neochat.chat.decoration` | Allows text decorations in chat |
| `neochat.chat.gradient` | Allows gradients in chat |
| `neochat.chat.rainbow` | Allows rainbow formatting in chat |
| `neochat.chat.reset` | Allows reset tags in chat |
| `neochat.chat.link` | Allows links to be formatted |

## Configuration

NeoChat generates these main files on first startup:

- `plugins/NeoChat/config.yml`
- `plugins/NeoChat/messages.yml`
- `plugins/NeoChat/formats.yml`

Notable configurable areas include:

- chat format and hover format
- ping mention prefix, sound, and rendering
- clickable link transformation
- placeholder definitions
- anti-spam and moderation rules
- Discord webhook output
- Towny chat format
- update checker behavior

## Building

This project uses Gradle.

```bash
./gradlew shadowJar
```

On Windows:

```powershell
.\gradlew.bat shadowJar
```

The built jar will be generated in:

```text
build/libs/
```

## Notes

- NeoChat currently loads as a classic plugin through `plugin.yml`.
- The project avoids NMS, which helps compatibility across newer Paper-compatible server versions.
- If you run very new server versions, also verify your PlaceholderAPI, Towny, and ItemsAdder builds.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
