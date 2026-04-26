# NeoChat

NeoChat is an advanced chat plugin for Paper-compatible Minecraft servers focused on rich formatting, interactive placeholders, private messages, moderation tools, and Folia-aware execution.

Download plugin: https://modrinth.com/plugin/neochat

## Features

- Rich chat formatting powered by Adventure and MiniMessage
- Mention system with `@player` ping highlighting and configurable sound notifications
- Clickable link formatting
- Interactive chat placeholders such as `[item]`, `[inv]`, `[ender]`, `[shulker]`, `[ping]`, `[pos]`, `[money]`, and `[playtime]`
- Private messages with `/msg`, `/reply`, `/togglemsg`, and `/socialspy`
- Ignore system with per-player ignore and ignore-all mode
- Automatic announcements with chat, titles, action bars, boss bars, sounds, and optional vanilla toasts
- Global chat mute commands
- Built-in format debugging with `/neochat debug <player>`
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
4. Edit `config.yml`, `messages.yml`, `formats.yml`, and `announcements.yml` as needed.
5. Reload plugin.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/neochat reload` | Reloads plugin configuration and messages | `neochat.admin.reload` |
| `/neochat debug <player>` | Shows the selected format, resolved placeholders, and LuckPerms meta for a player | `neochat.admin.debug` |
| `/chatmute` | Mutes global chat | `neochat.admin.mute` |
| `/chatunmute` | Unmutes global chat | `neochat.admin.mute` |
| `/msg <player> <message>` | Sends a private message | `neochat.pm.use` |
| `/reply <message>` | Replies to the last private message | `neochat.pm.use` |
| `/togglemsg` | Toggles private messages on or off | `neochat.pm.toggle` |
| `/socialspy` | Toggles SocialSpy | `neochat.admin.spy` |
| `/announcer toggle [on\|off]` | Toggles automatic announcements for yourself | `neochat.announcer.toggle` |
| `/announcer status` | Shows your current announcement status | `neochat.announcer.toggle` |
| `/announcer list` | Lists loaded announcements | `neochat.announcer.use` |
| `/announcer preview <id>` | Previews a configured announcement | `neochat.announcer.preview` |
| `/announcer send <id> [player]` | Sends a configured announcement manually | `neochat.announcer.send` |
| `/ignore <player>` | Ignores or unignores a player | `neochat.ignore.use` |
| `/ignoreall` | Toggles ignore-all mode | `neochat.ignore.ignoreall` |
| `/tc [message]` | Toggles Towny chat or sends a town message | `neochat.command.townychat` |

## Main Permissions

| Permission | Description |
| --- | --- |
| `neochat.admin.reload` | Allows reloading NeoChat |
| `neochat.admin.mute` | Allows muting and unmuting global chat |
| `neochat.admin.spy` | Allows using SocialSpy |
| `neochat.admin.debug` | Allows inspecting a player's chat format resolution |
| `neochat.pm.use` | Allows private messaging |
| `neochat.pm.toggle` | Allows toggling private messages |
| `neochat.pm.bypass` | Bypasses private-message blocks on targets |
| `neochat.announcer.use` | Allows viewing the announcement list |
| `neochat.announcer.toggle` | Allows toggling automatic announcements for yourself |
| `neochat.announcer.preview` | Allows previewing a configured announcement |
| `neochat.announcer.send` | Allows manually sending a configured announcement |
| `neochat.announcer.receive` | Allows receiving automatic announcements |
| `neochat.announcer.force_receive` | Forces announcement delivery even if the player opted out |
| `neochat.ignore.use` | Allows ignore commands |
| `neochat.ignore.ignoreall` | Allows ignore-all mode |
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
| `neochat.chat.click` | Allows click events in chat formatting |
| `neochat.chat.hover` | Allows hover events in chat formatting |
| `neochat.chat.font` | Allows custom font tags in chat formatting |
| `neochat.chat.link` | Allows links to be formatted |

## Configuration

NeoChat generates these main files on first startup:

- `plugins/NeoChat/config.yml`
- `plugins/NeoChat/messages.yml`
- `plugins/NeoChat/formats.yml`
- `plugins/NeoChat/announcements.yml`

Notable configurable areas include:

- chat format and hover format
- ping mention prefix, sound, and rendering
- clickable link transformation
- placeholder definitions
- anti-spam and moderation rules
- Discord webhook output
- Towny chat format
- automatic announcements with world and permission filters
- optional vanilla toast notifications configured per announcement
- update checker behavior

Custom format permissions are driven by `formats.yml`, so servers can define their own `neochat.format.*` nodes per format entry.

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
- If a LuckPerms suffix contains Nexo glyphs, keep that suffix outside `<gradient>` or similar styling blocks to avoid rendering the glyph tag literally.
- The announcer currently supports chat, title, action bar, boss bar, sound, vanilla advancement toasts, per-player opt-out, and permission/world filters.
- Toast notifications do not require PacketEvents, but they use vanilla advancement popups, so toast text should be treated as static rather than per-player dynamic content.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
