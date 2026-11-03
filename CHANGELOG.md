# Changelog

What changed in each MemoCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MemoCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Project updates (2026-11-03)

### Project

- **README:** the Ctrl series list now links to ChatCtrl, the Matrix chat app in the series. No app changes, so no new version.
- **README:** the Ctrl series list now includes SumCtrl, the offline calculator, unit converter and work-tools app. No app changes, so no new version.

## v0.4.4 (2026-11-02)

Safer handling of pictures and attachments from the server, and a friendlier GitHub page.

### Security

- **Pictures in a memo are only loaded from web addresses.** A picture in the memo text that points to a file on the phone (a `file:` or `content:` address) or any other non-web address is no longer loaded; it shows as a link instead, like pictures from other websites.
- **Attachment links from the server must be web addresses.** An attachment whose external link isn't an `http://` or `https://` address is now treated as a normal file on your Memos server, so a memo can never make MemoCtrl open, share or show a file from the phone.
- **Attachment file names are cleaned in full** when an attachment is downloaded to open it in another app, so an odd name sent by the server can't end up outside MemoCtrl's own folder.
- **Only MemoCtrl's own attachment files are kept during sync.** When syncing, an attachment's saved copy on the phone is only reused, opened or uploaded if it's inside MemoCtrl's private attachments folder. A file address left behind by an older version that points anywhere else is ignored, and the attachment is downloaded from your server again when needed.

### Project

- New **SECURITY.md**: how to report a security problem privately, and the release key's fingerprint.
- New **issue forms** for bug reports and feature requests, with a reminder to remove server addresses, tokens and personal info.
- **README**: clearer install steps for people new to GitHub, a new "Is it safe to install?" section with every permission explained, "How MemoCtrl is made", the full Ctrl series list, and the empty Screenshots section removed.

## v0.4.3 (2026-11-02)

### Fixed

- **Bottom sheets no longer show a stray grey box or outline.** Sheets that slide up from the bottom had a thin grey rectangle drawn partway down the open sheet, sometimes cutting across the first row, plus a stray rounded outline near the top of the screen. The outline was drawn at the sheet's starting position rather than where the sheet actually is. Sheets are now a plain surface with rounded top corners and no outline; the dimmed background already sets them apart. This fixes every bottom sheet in MemoCtrl:
  - the ⋮ menu of a memo (pin, edit, share, copy, archive, delete…)
  - choice sheets in Settings (Lock again, Edit gesture) and in the widget setup (tag, number of memos)
  - the visibility picker in the editor (Private, Protected, Public)
- Dialogs keep their thin border, and popup menus keep the flat look from 0.4.2. Nothing else about how sheets work has changed.

## v0.4.2 (2026-11-02)

### Fixed

- **Popup menus no longer show a grey box.** The tag picker in the editor (the **#** button: "New tag" and your existing tags) had a grey, square-ish shadow edge around it, most visible in light mode. It's now a flat Signal surface (white in light mode, graphite in dark mode) with 16dp rounded corners, a thin card border and no shadow.
- **Menu text and icons follow the theme**: menu entries use the normal text colour, icons the grey secondary colour, and disabled entries are greyed out, in both light and dark mode.
- All popup menus in MemoCtrl now share this one style, so any menu added later looks the same. Nothing else about how the menus work has changed.

## v0.4.1 (2026-10-25)

### Fixed

- **Moving memos to a server can no longer lose pictures or files.** Attachments are now copied to the server account first and the originals on the phone are only removed once every memo has moved; a file that couldn't be copied stays where it is, and its memo keeps showing it. Before, a file could disappear if the move stopped halfway. If the move fails, "On this phone" stays as it was.
- **An unposted memo of "On this phone" moves along** as the draft of the server account you move to (unless that account already has a draft), instead of being thrown away.
- **Files of memos you deleted on "On this phone" are no longer copied** when moving memos to a server.
- **No crash when you leave or close MemoCtrl with the app lock on "As soon as you leave"**: pressing Back on the first screen or swiping MemoCtrl away from recent apps could make the lock screen or fingerprint prompt open on a screen that was already closing. MemoCtrl still locks, and opens on the lock screen next time.
- **No crash when leaving the app while writing a very long memo**: Android refuses to keep very large texts when it puts the editor away, which closed the app. Long texts are still kept by the draft or autosave.
- **Turning the app lock on or off, and "Forgot PIN?", always finish**, even when the PIN screen closes halfway; before, the logins could stay half encrypted until the next unlock.
- **Syncing by hand keeps going when you switch tabs** instead of stopping halfway, which could post a memo twice.
- **"Forgot PIN?" also deletes unsaved edits** kept from an editor the app lock closed, and now says that memos of "On this phone" are deleted for good too.
- **Unexpected errors in background work (sync, widgets, saving drafts) no longer crash the app**; they show in the sync status as before. A broken share from another app is ignored instead of crashing MemoCtrl.

### Security

- Two parts WorkManager adds to every app are switched off: its debugging receiver and the permission to run as a foreground service. Background sync uses neither.
- Photos taken for a memo and attachments opened in another app are copies in MemoCtrl's cache; copies older than a day are now cleared at start-up (the memo keeps its own copy).

## v0.4.0 (2026-10-25)

### New

- **Rename and delete tags.** Long-press a tag on the Tags tab for **Show memos**, **Rename** and **Delete tag**. Before anything changes, MemoCtrl tells you how many memos have the tag and asks you to confirm.
  - **Rename** changes `#old` to `#new` in the text of every memo that has it, archived ones included, and renames its sub-tags too (`#work/meetings` becomes `#job/meetings`). Tags inside code or links are left alone, the same way Memos ignores them.
  - **Delete tag** takes the tag (and its sub-tags) out of the text of every memo that has it, along with the space next to it. The memos themselves stay.
  - **Works offline:** the memos are changed on the phone straight away and sync to your server like any other edit, now or when it can be reached. Memos 0.27 and newer have no tags of their own to rename, so MemoCtrl always rewrites each memo there. On Memos 0.21 MemoCtrl uses the server's own rename when you're online and none of those memos have unsynced changes, then fetches the result; deleting a tag there also takes it off the server's tag list.
  - A new tag name can't be empty or contain spaces or `#`.
- **Background sync.** Under your profile picture › **Settings > Background sync**, choose **Off** (the default), **Every 15 minutes**, **Every 30 minutes**, **Every hour** or **Every 6 hours**. It's the same sync as pull to refresh, for the account you're using, and the home-screen widgets update after it.
  - **Only on Wi-Fi** waits for Wi-Fi or another unmetered network; **Only while charging** waits until the phone is plugged in. Without either it still only runs when the phone has a connection.
  - **Tell me when it fails** (off by default) shows a quiet "Background sync failed" notification that never contains memo text, an account or a server name. Turning it on asks for permission to show notifications (Android 13+); it disappears after the next sync that works.
  - Android picks the exact moment so it saves battery. Background sync skips a server whose Memos version needs confirming (sync by hand to confirm it), and doesn't run while MemoCtrl is locked with the app lock, because your login stays encrypted until you unlock.
  - MemoCtrl still syncs every time you open it, as before.
- **Use without a server (local-only mode).** The first screen now has **Use without a server**, and **Servers and accounts** has it as an account of its own ("On this phone"). Memos you write there are kept only on the phone, with their pictures and files in MemoCtrl's private storage, and nothing is ever sent anywhere.
  - Everything that doesn't need a server works: the editor (formatting, tasks, tags, pictures, camera, files, location), pin, archive, edit, share, copy and delete, the Tags tab with the heatmap and the new rename and delete, offline search, the Archive and Attachments pages, drafts and autosave, and the **Memos** and **Memory** widgets.
  - Things that need a server are hidden for this account: the **Explore** tab, the sync button and sync status, who can see a memo, **Share link**, the server details and the client certificate, and the Background sync settings.
  - Deleting a memo there deletes it (and its files) right away, since there's no server to tell.
  - **Move memos to a server:** add a server account, then tap **Move memos to a server** on the You page of "On this phone" (or under **Servers and accounts**) and pick the server account. Every memo, with its pictures and files, becomes a new memo there, keeping its date, pinned and archived state (and its location on Memos 0.27 and newer), and is uploaded with the next sync (it waits on the phone if you're offline). "On this phone" is removed afterwards. Without a server account yet, it offers to add one first.
  - **Delete local memos** (instead of Sign out) removes "On this phone" and everything in it for good, after asking.
  - The app lock, Hide in screenshots and Hide in recent apps work the same with it.

### Changed

- The README no longer lists the local-only mode under "Planned".
- MemoCtrl now uses WorkManager (AndroidX, open source, no Google services) to run the background sync.

## v0.3.0 (2026-10-25)

### New

- **Light mode that follows the device theme.** MemoCtrl now switches between the dark Ctrl / Signal look and a new light one ("Signal Light") on its own, following your phone's dark theme setting (and its schedule, if you use one). It changes straight away, even while the app is open. There's still no theme setting in the app, and no Material You colours: it just follows the phone.

### Changed

- **Signal Light colours**: a warm off-white background (#F5F5F1), white cards and sheets with soft grey borders, near-black text and grey secondary text.
- **Accent in light mode**: hero cards, the main buttons (Post, Save, New memo, Sign in, Support MemoCtrl), the selected tab, switches that are on, the PIN dots and the lock badge keep the same lilac fill (#B48CFF) with dark content. Text and icons in the accent colour use a deeper violet (#6A3BDB) on light backgrounds so they stay readable: the slash in MEMO/CTRL, #tag chips, @authors, the pinned icon and pinned-memo border, links and #tags in memo text, the visibility button in the editor, focused text-field borders and cursors, spinners, the outlined fingerprint key and the accent icons on empty screens. In dark mode these stay #B48CFF as before.
- **Switches in light mode**: grey (#DCDCD5) with a darker grey knob when off, lilac with a dark knob when on.
- **Memo heatmap on the Tags tab** uses shades that show on both backgrounds: in light mode it goes from pale lilac (one memo) to deep violet (five or more), and empty days are light grey.
- **Lock screen in light mode**: white keys with a thin grey border, empty PIN squares with a grey outline, the lilac lock badge and PIN dots.
- **Floating tab bar in light mode**: white at 92% with a thin grey border; the selected tab stays a lilac block.
- **Errors in light mode** use a deeper red (#D93636) so they're readable on white.
- **Status and navigation bar icons** switch with the theme: light icons in dark mode, dark icons in light mode, always on the app's own background.
- **Home-screen widgets** follow the phone's theme too: white cards on a light background in light mode, the dark look in dark mode, with violet or lilac accents to match.
- **The full-screen picture viewer stays black in both themes**, so photos are shown on a neutral dark background.
- Every screen, dialog, menu and sheet now picks its colours from the current theme (internally: two palettes, `CtrlColors.Dark` and `CtrlColors.Light`, with the accent still defined in one place).

## v0.2.0 (2026-10-25)

MemoCtrl gets the new **Ctrl / Signal** look shared by the whole Ctrl series. Only the look changes: every screen, feature, setting and gesture works as before.

### Changed

- **New dark look everywhere**: a near-black background (#08080A), graphite cards (#141417) with thin borders, light text and one lilac accent colour (#B48CFF) instead of the marigold yellow. Buttons, switches, the selected tab, links, #tags, the memo heatmap and the lock screen all use that accent.
- **New fonts, built into the app**: Space Grotesk for all text and headings, and JetBrains Mono for times, counts, labels and code. They replace Manrope. Both are free fonts (SIL Open Font License 1.1, licence files included) and ship inside the app, so nothing is downloaded.
- **New top bar**: the main tabs show the MEMO/CTRL wordmark (the slash in the accent colour) with your profile picture on the right, then the tab's name in big type. Your profile picture is now a rounded square with your initials and a small accent square in its corner while the server is reachable (the last sync didn't fail). Tapping it still opens the You page and settings.
- **Other screens** (a memo, a tag, the editor, You, Settings, Accounts, Archive, Attachments) get a square back button next to the title instead of the plain arrow bar.
- **Memo cards**: graphite cards with a thin border, the time in mono capitals, and the memo's #tags as small chips underneath (tap one to open that tag, same as tapping the #tag in the text). Pinned memos keep an accent border. Explore cards and the memo page show the tag chips too.
- **New memo button**: an accent rounded rectangle instead of a round pill.
- **Editor**: the Post / Save button is an accent button in the new top bar (the close button is a square X). The text sits in a bordered card, the H1/H2/H3 buttons use the mono font, and the visibility button shows its name in accent mono capitals.
- **Tags tab**: your number of memos is now a big accent card at the top, with the number of tags and days in two cards next to each other below it, and the heatmap in its own card in shades of the accent. Each tag shows its memo count on the right in mono.
- **Bottom tabs**: a floating bar with a thin border and square corners. The selected tab is an accent block with its icon and name; the others show just their icon. Tablets keep the side rail, in the new colours.
- **Switches**: a new rounded-square switch (accent with a dark knob when on, grey when off). They still work with TalkBack as switches.
- **Settings and You**: grouped settings sit in bordered cards with small mono section labels and thin lines between rows; row icons are outlined and grey. The You page shows a large accent square with your initials, your name and the server address in mono. **Support MemoCtrl** is now an accent button at the bottom, followed by the version and a "GOOGLE-FREE · TELEMETRY-FREE · ARM64" footer.
- **Lock screen**: an accent square with a lock, "VKNXWORKS · CTRL" above a big "Locked", square PIN dots, and a keypad of rounded keys with mono digits. The fingerprint key (outlined in accent) sits left of 0 when fingerprint unlock is on; when a longer PIN or a new PIN needs confirming, that spot becomes the accent OK / Next / Done key. The lock itself, the PIN rules, fingerprint unlock and "Forgot PIN?" work exactly as before.
- **Sign-in, search, dialogs and menus**: text fields are bordered cards whose border turns accent while you type; the password/token choice uses accent segments; dialogs and bottom menus use the graphite surface with rounded corners and a thin border.
- **Empty and error screens** show their icon in the accent colour on a bordered square, and the Retry button is a bordered button.
- **Home-screen widgets** use the new colours and corner shapes too.
- **Status and navigation bars** always use light icons on the app's dark background, edge to edge.

### Removed

- **Theme and Material You colour settings**: Ctrl / Signal is dark only, so **Settings > Appearance** (Theme: System / Light / Dark / Black, and Material You colours) is gone. If you had picked Light, System or Black, or turned Material You on, MemoCtrl now shows the dark look anyway; the old choice is kept but does nothing.
- **Frosted-glass blur behind the tab bar**: the bar is now a solid, slightly see-through graphite. The blur library (Haze) is no longer part of the app.

## v0.1.10 (2026-10-21)

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left MemoCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** did nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while the app was still coming back on screen, and the phone dropped it without answering, which left MemoCtrl waiting for an answer that never came. Now the prompt only opens once MemoCtrl is fully back on screen, a prompt that never answered can't block the next one, and the lock screen offers your fingerprint again every time you come back to it, not just the first time after it locked. The same applies to the one-time check with your phone's screen lock for people updating from the old app lock.

## v0.1.9 (2026-10-21)

### Security

- **Attached pictures from other websites now follow "Load pictures from other websites" too.** A memo's attachment can be just a link to a picture on another website. Those pictures were loaded straight away in the memo list, the Attachments page, Explore and the full-screen viewer, even with **Load pictures from other websites** off, so that website could see when you read the memo. Now, with the switch off (the default), such a picture shows as a tile with its name and the website's address instead; tapping it opens the picture in your browser. Pictures stored on your Memos server and pictures on your phone still show as before.
- **Shared files can't point back to MemoCtrl's own files any more.** A share from another app with an address like "content://0@…" (with a user number in front) slipped past the check that stops other apps from making MemoCtrl attach its own private files to a memo. That form is now recognised and refused too.
- **Server logins are always sealed with your app PIN when the app lock is on.** If MemoCtrl was closed at exactly the wrong moment while you turned the app lock on, your saved logins could stay protected by the phone's keystore only, without the extra encryption from your PIN. MemoCtrl now checks this every time you unlock and seals any login that isn't sealed yet.

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
