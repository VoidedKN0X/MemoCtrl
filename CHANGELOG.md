# Changelog

What changed in each MemoCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MemoCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.1 (2026-10-21)

### New

- **Sign in with your username and password.** The sign-in screen now has two options: **Password** (the default) and **Access token**. With a password, MemoCtrl signs in once and asks your server to make an access token just for the app, named after your phone (for example "MemoCtrl on OPPO …"); from then on it only uses that token. Works with Memos 0.21 and 0.27 to 0.31. **Access token** is still there for accounts that sign in with SSO, or if you'd rather make the token yourself.

### Security

- **Your password is never kept.** It's only sent to your server for that one sign-in, held in memory while the sign-in screen is open, and never written to the phone. The short sign-in session is ended right after the app's token is made.
- **Signing out revokes the app's token.** For accounts signed in with a password, signing out (from the You page or **Servers and accounts**) also deletes MemoCtrl's access token on your server, when the server can be reached. You can also see and revoke it yourself in Memos under **Settings > My account > Access tokens**.

### Changed

- **README**: Getting started describes signing in with your username and password, with the access token as the alternative.

## v0.1.0 (2026-10-20)

The first release of MemoCtrl: a client for your self-hosted Memos server, in the Ctrl series design.

### New

- **Sign in with an access token** to your Memos server (Memos 0.21, and 0.27 to 0.31). The address can be typed with or without `https://`; plain HTTP only works after a clear "unencrypted" warning. Servers newer than MemoCtrl knows ask before you continue. Several servers and accounts can be signed in at once, and you switch between them under **Servers and accounts**.
- **Memos tab**: your timeline, pinned memos first and outlined in the accent colour. Long memos are shortened with **Show more**. Pull down to sync. The **New memo** button writes a new one.
- **Works offline.** Every memo is kept on the phone. Changes you make are saved there first and sent to your server right away, or as soon as it can be reached. When a memo was changed both here and on the server, the server's version is kept and yours is saved as a new memo, so nothing is lost. The sync button at the top shows when changes are waiting or the last sync failed.
- **Editor** with a formatting bar (headings, bold, italic, strikethrough, code, quotes, bulleted and numbered lists), tasks (`- [ ]`), tags (pick one you used before or start a new one), pictures from your gallery, photos straight from the camera, any file as an attachment, a location, and who can see the memo (private, signed-in users of your server, or public). Pressing Enter on a list item starts the next one.
- **Drafts**: a new memo you close without posting can be kept as a draft and opens again next time.
- **Autosave** (Settings): every change is saved as you type and synced in the background, instead of when you tap Post.
- **Memo actions**: pin and unpin, edit, share the text, copy the text, share a link to the memo on your server, archive, and delete (it asks first).
- **Tick tasks** right in the timeline or a memo's page; only that checkbox changes in the memo.
- **Memo page** with the whole text (you can select and copy parts of it), pictures, files, location, the full date and who can see it.
- **Markdown** the way Memos shows it: headings, lists, tasks, quotes, code blocks, tables, links and pictures. Single line breaks are kept.
- **Tags tab**: how many memos and tags you have and how many days you've used Memos, a heatmap of the days you wrote (darker means more memos), and every tag with its number of memos. Tapping a #tag anywhere shows the memos with that tag and its sub-tags; **New memo** there starts with the tag already filled in.
- **Explore tab**: memos people on your server shared publicly or with signed-in users, with who wrote them. It loads more as you scroll.
- **Search tab**: finds memos on the phone by their text, so it works offline. Archived memos can be included (Settings).
- **Archive** (on the You page): restore archived memos to the timeline, or delete them.
- **Attachments** (on the You page): every picture and file in your memos. Pictures open full screen: swipe between them, pinch or double-tap to zoom, and share them. Files open in the app you choose and are kept on the phone after the first time, so they open offline too.
- **Locations**: add where a memo was written, typed in or from the phone's GPS (Memos 0.27 and newer). Tap it to open it in a map app, or OpenStreetMap in the browser.
- **Share to MemoCtrl** from other apps: text, links and pictures start a new memo.
- **Home screen widgets**: **Memos** shows your latest memos (pinned first), optionally only one tag or only pinned ones, with buttons to sync and to write a new memo. **Memory** shows a random memo, a different one each time it refreshes. Tapping a memo opens it (or the editor, if you open memos with a tap).
- **Launcher shortcut** "New memo" (long-press the app icon).
- **Edit gesture** (Settings): open memos in the editor with a tap, a double-tap or a long-press, or only from the menu.
- **Themes**: follow the system, dark, light or pure black, and optionally your wallpaper colours.
- **Tablets and foldables** get a side rail instead of the bottom bar.

### Security

- **App lock** (Settings): asks for your fingerprint, face or screen lock when MemoCtrl opens and after a minute away. It's confirmed before it turns on, the recent-apps screen shows a blank card, and the widgets show "MemoCtrl is locked" instead of your memos.
- **Access tokens are encrypted** on the phone with a key held in the Android Keystore, and are only ever sent to their own server. Pictures and files of private memos are fetched with the token, but only from your server's own address.
- **Nothing leaves through redirects**: the token, cookies and the text or files you send are never passed on when a server redirects to another address, and an HTTPS connection is never redirected to plain HTTP.
- **Client certificates** (mutual TLS) for servers behind a proxy that asks for one. The certificate stays in Android's own certificate store; MemoCtrl only remembers which one you picked.
- **Pictures from other websites aren't loaded** unless you turn it on in Settings. They show as a link instead, so those sites can't see when you read your memos.
- **Location uses GPS only**, never network location (which can send nearby Wi-Fi networks and cell towers to an outside service), and is only asked for when you tap "Use current location".
- **No backups** of the app's data to the cloud or to a new phone: your memos live on your own server.
- **Home network permission** (Android 17 and newer) is only asked for when your server's address is on your home network.

### Privacy

- **Telemetry-free, Google-free and de-Googled.** No analytics, crash reporting, usage stats or tracking of any kind, and no Google Play services or Firebase. MemoCtrl only talks to your own Memos server (and, if you allow it, sites that pictures in your memos link to).
