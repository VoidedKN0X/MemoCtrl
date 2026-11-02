# Security policy

Thank you for helping keep MemoCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/MemoCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of MemoCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your password, access token, memos or attachments leaking to anyone other than your own Memos server
- Ways around the app lock, such as widgets, shortcuts or shares showing your memos while MemoCtrl is locked, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone being able to make MemoCtrl do something you didn't ask for, such as attaching MemoCtrl's own files to a memo
- A memo or a server answer making MemoCtrl open, show or save files on the phone that it shouldn't
- Anything that lets someone run code or read files they shouldn't

Problems in Memos itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
7A:28:90:70:08:4E:0F:F1:11:A7:C2:DB:73:38:5C:EC:79:8C:4B:A3:AC:45:A5:72:3F:EB:76:E1:8F:25:66:4D
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
