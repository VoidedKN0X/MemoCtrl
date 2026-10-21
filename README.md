# MemoCtrl

**Your self-hosted Memos, on your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/MemoCtrl?label=download)](https://github.com/VoidedKN0X/MemoCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

MemoCtrl is an Android app for self-hosted [Memos](https://usememos.com/) servers: capture thoughts, links and to-dos like tweeting to yourself.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/MemoCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Timeline** of your memos, pinned ones first, with pull to sync
- **Works offline**: memos are kept on the phone and changes sync when your server can be reached, without losing anything when both sides changed
- **Editor** with formatting, tasks, tags, pictures, the camera, file attachments, a location and who can see the memo
- **Markdown** the way Memos shows it, with tappable tasks and #tags
- **Drafts** for unposted memos, or **autosave** while you type
- **Pin, edit, share, copy, archive and delete** memos
- **Tags** with a heatmap of the days you wrote and stats
- **Explore** what others shared on your server
- **Search** that works offline, optionally in your archive too
- **Archive** and **attachments** pages, and a full-screen picture viewer
- **Share to MemoCtrl** from any app, a **New memo** launcher shortcut, and **Memos** and **Memory** home screen widgets
- **App lock** with its own PIN and optional fingerprint unlock; your server login stays encrypted while the app is locked (see [App lock](#app-lock))
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, on their own or with the app lock
- **Client certificates** (mutual TLS) for servers behind a proxy that asks for one
- **Sign in with your password** (MemoCtrl makes its own access token and never keeps the password) or an access token
- **Several servers and accounts**

## Planned

Not in the app yet: a local-only mode without a server.

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A Memos server: Memos 0.21, or Memos 0.27 to 0.31 (newer versions ask before you continue)
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/MemoCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds MemoCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/MemoCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/MemoCtrl` in Obtainium by hand.

## Getting started

1. Enter your Memos address, e.g. `https://memos.example.com`.
2. Sign in with your Memos username and password. MemoCtrl asks your server for an access token of its own and never keeps your password.
3. Tap **New memo** and write something.

Accounts that sign in with SSO can use **Access token** instead: make one in Memos under **Settings > My account > Access tokens** (one that never expires) and paste it in.

Settings, the archive, attachments, accounts and sign out are behind your profile picture at the top right.

## Tips and troubleshooting

- **Plain HTTP** works, but the app warns you first because your token would be sent unencrypted.
- **"Your access token no longer works"**: the token expired or was deleted in Memos. Sign out and sign in again.
- **Changes waiting to sync** keep a cloud icon on the memo; tap the sync button at the top to try again.
- **Location** needs Memos 0.27 or newer, and uses the phone's GPS only.
- **Pictures from other websites** in memos show as a link until you turn on **Load pictures from other websites** (your profile picture › **Settings > Privacy and security**).

## App lock

Turn it on under your profile picture › **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your server login is encrypted with the PIN while MemoCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Taking a photo, picking pictures or files, sharing and opening an attachment don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your login. The lock screen offers to sign out of every account and delete what MemoCtrl keeps on the phone; everything on your Memos server stays.
- **With the app lock on**, the recent-apps screen always shows a blank card, and the widgets show "MemoCtrl is locked" instead of your memos and don't sync, because your login stays encrypted.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of MemoCtrl. Screenshots still work.

The sign-in screen and the lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say.

## Privacy

**Telemetry-free.** MemoCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to your own Memos server. Your password is never stored; the app's access token is stored encrypted on the phone and is only sent to that server.

- **Servers:** only the Memos servers you sign in to. Pictures that memos link from other websites are only loaded after you turn on **Load pictures from other websites** (your profile picture › **Settings > Privacy and security**); until then they show as a link.
- **Links:** links in memos, a memo's location (OpenStreetMap) and the Support, Report a bug, Source code and Licence rows in About open in your browser only when you tap them; the app itself doesn't contact those sites.
- **Location** comes from the phone's GPS only, and only when you tap **Use current location** on a memo.
- **No backups:** Android doesn't back up MemoCtrl's data or move it to a new phone. Your memos live on your own server.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/MemoCtrl.git
   cd MemoCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

MemoCtrl is free and open source. If it's useful to you, tap **Support MemoCtrl** under **About** on the You page (tap your profile picture) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/MemoCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| **MemoCtrl** (this app) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

MemoCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
