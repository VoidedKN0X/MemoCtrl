# Changelog

What changed in each MemoCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MemoCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.8 (2026-10-21)

### Fixed

- **Clear message for an attachment without a name**: if the Memos server ever sends an attachment that has no name, MemoCtrl now says "The server sent an attachment without a name, so MemoCtrl can't show it." instead of a technical message in English only. All of MemoCtrl's text now comes from its text file, ready for translations.

### Changed

- **README**: the App lock and privacy setting paths now start at your profile picture (tap your profile picture › Settings › Privacy and security)

## v0.1.7 (2026-10-21)

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in **Settings > Privacy and security**, both off by default. **Hide in screenshots** makes screenshots and screen recordings show a blank screen (the recent-apps view too). **Hide in recent apps** only shows a blank card in the recent-apps view, so screenshots still work. They replace the single "Hide in screenshots and recent apps" switch from 0.1.6: if you had turned that on, both new switches are on after the update, so nothing changes for you.
- **Nothing changes for people who never turned it on:** MemoCtrl was only hidden from screenshots while signing in and on the lock screen, and that stays the same. With the app lock on, the recent-apps view still always shows a blank card, because a preview there would show your memos without the PIN.
- **The Memos widget's settings screen follows both switches too.**

## v0.1.6 (2026-10-21)

Settings now follow the same layout as the other Ctrl apps, and a few more places keep your memos and login private.

### New

- **Hide in screenshots and recent apps** (**Settings > Privacy and security**). When it's on, screenshots, screen recordings and the recent-apps view show a blank screen instead of MemoCtrl. It works with or without the app lock.

### Changed

- **Settings are in the same order as in every Ctrl app:** Appearance first, then Privacy and security (app lock, change app PIN, fingerprint unlock, lock again, hide in screenshots, pictures from other websites and the client certificate), then Writing and Search. Theme and wallpaper colours have icons like the other rows.
- **Plurals read correctly:** "1 change waiting to sync", "1 memo" on a tag, and the sign-out warning no longer say "1 changes" or "1 memos".

### Security

- **The sign-in screen is always kept out of screenshots and screen recordings**, not only the recent-apps view, because your password or access token is typed (and can be shown) there.
- **The lock screen and every PIN screen are kept out of screenshots and screen recordings**, so a recording can't show the PIN being typed.
- **Copied memo text is marked as sensitive:** Android's clipboard preview and keyboards with a clipboard history don't show or keep it.
- **Share to MemoCtrl only accepts content another app really shares.** File paths, and links that point back to MemoCtrl's own private files, are ignored, so another app can't make MemoCtrl attach its own files to a memo.

## v0.1.5 (2026-10-21)

### Security

- **Trips out of the app no longer skip the lock if you stay away.** Taking a photo, picking pictures or files, sharing or opening an attachment still doesn't count as leaving the app, but if you don't come back within 5 minutes (or your "Lock again" time, if that's longer), MemoCtrl locks anyway. Before, such a trip never locked, however long you were gone.

## v0.1.4 (2026-10-21)

### Fixed

- **Adding a second account on the same server no longer sends the first account's access token.** Signing in and checking a server never get another account's token attached, even when it's the same server.
- **Unsaved changes aren't lost when the app locks.** If the lock closes the editor while you're writing, a new memo is kept as your draft, and changes to an existing memo come back the next time you edit it ("Restored your unsaved changes"). With autosave on, everything was already saved.
- **Each Memory widget opens its own memo.** With more than one Memory widget, tapping any of them used to open the memo shown on the last one that refreshed.
- **Updating from the old app lock no longer gets stuck** on a phone that has no screen lock or fingerprint any more: MemoCtrl goes straight to choosing an app PIN.

## v0.1.3 (2026-10-21)

The app lock now works the same way as in every other Ctrl app, with the strongest protection of the options we compared.

### Changed

- **App lock now has its own PIN**, separate from your phone's screen lock. It needs at least 6 digits, and very obvious ones (like 111111 or 123456) are refused. Someone who knows or has seen your phone's PIN can't open MemoCtrl with it.
- **Updating with the app lock already on:** the first time you open MemoCtrl, unlock once more with your fingerprint, face or screen lock as before, then choose your app PIN. You can't skip this step.
- **Fingerprint unlock is optional** (**Settings > Privacy and security > Unlock with fingerprint**). It only accepts strong biometrics such as the fingerprint sensor, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is still needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock.
- **"Lock again" now defaults to "As soon as you leave the app"** if you never changed it. Every other choice (1, 5 or 15 minutes, 1 hour, or only when the app restarts) is still there, and the camera, gallery, file picker and share sheet still don't count as leaving the app.
- **Change app PIN** is in Settings; it asks for your current PIN first. Turning the app lock off also asks for the PIN.
- **Widgets don't sync while the app lock is on**, because the login stays encrypted until you unlock. They still show "MemoCtrl is locked".

### Security

- **Your server login is encrypted with your PIN while MemoCtrl is locked.** The key that decrypts it only exists in memory while the app is unlocked, and is wiped the moment it locks. A locked app, or a copy of its files, can't use your access token.
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying MemoCtrl's files to a computer isn't enough to try PINs there.
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait.
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN.
- **Forgot your PIN?** The lock screen offers to sign out of every account and delete the memos, pictures and drafts kept on the phone. Because the PIN encrypts your login, there's no way around it. Everything on your Memos server stays.

## v0.1.2 (2026-10-21)

### New

- **Choose when the app lock asks again.** With the app lock on, **Settings > Privacy and security > Lock again** sets how long MemoCtrl can be in the background before it asks for your fingerprint, face or screen lock again: as soon as you leave the app, after 1 minute (the default, as before), 5 minutes, 15 minutes or 1 hour away, or only when the app restarts.

### Changed

- **Taking a photo, picking pictures or files, sharing a memo and opening an attachment no longer count as leaving the app.** Coming back from the camera, gallery, file picker, share sheet or the app that opened a file doesn't ask you to unlock again, even with "As soon as you leave the app". Going to the home screen or another app yourself still does.
- **App lock description** in Settings now also mentions that the widgets hide your memos while the lock is on.

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
