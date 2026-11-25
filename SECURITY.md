# Security Policy

SerieusTracker keeps your list on your device and stores a Plex sign-in token, so security and privacy matter here. Thank you for helping to keep the project and its users safe.

## Reporting a vulnerability

**Please do not report security problems in a public issue, discussion or pull request.**

Use GitHub's private reporting instead:

1. Open the **Security** tab of this repository.
2. Choose **Report a vulnerability**.
3. Describe the problem, as in the list below.

If you cannot use that form, open an issue that only says you have a security report and ask for a private way to send it. Do not include details in the issue.

A useful report includes:

- what the problem is and what an attacker could do with it,
- the steps to reproduce it, or a proof of concept,
- the app version, the Android version and the device,
- any suggestion for a fix, if you have one.

Please **do not include real credentials**, such as your Plex token. If a token appears in a log or screenshot, hide it first and, if it was exposed, sign out of the device in your Plex account settings to invalidate it.

## What to expect

This is a volunteer-run project, so these are goals and not guarantees:

- an acknowledgement of your report within about **7 days**,
- a first assessment, and questions if something is unclear, within about **14 days**,
- a fix and a new release as soon as is reasonable, depending on how serious the problem is,
- credit in the release notes if you would like it.

Please give a reasonable time to fix a problem before you tell others about it. We will keep you informed of the progress.

## Supported versions

Only the **latest release** receives security fixes. The project has not yet reached a stable 1.0 release, and older versions are not patched.

## Scope

Problems that are relevant to report include:

- exposure or weak protection of the **Plex token** (storage, logging, backup, transmission),
- the app sending the user's list or watch history anywhere other than the user's chosen services,
- crashes, data loss, or code execution caused by a **crafted import file**,
- ways for another app on the device to read or change the app's data,
- unintended exposure of data through Android backup, logs or intents,
- vulnerable or malicious dependencies.

## Known design decisions

These are documented and are not treated as vulnerabilities, but suggestions to improve them are welcome as ordinary issues:

- **Cleartext HTTP is allowed** (`usesCleartextTraffic="true"`), so that the app can reach a Plex Media Server on the local network by IP address. Traffic to AniList and plex.tv uses HTTPS. Anyone who does not use the Plex server history import can disable cleartext traffic in `AndroidManifest.xml`.
- **The Plex token is sent as a query parameter**, which is how the Plex API works. When a user enters an `http://` server address, the token travels unencrypted on that network.
- **The list is not encrypted at rest** beyond Android's own app sandbox and device encryption. Only the Plex token is additionally encrypted with a key in the Android Keystore.
- **Android backup is enabled** for the app data except for the token, which is excluded because the Keystore key cannot be moved to another device.

## Out of scope

- Vulnerabilities in AniList, Plex or Android itself. Please report those to the respective vendors.
- Attacks that need a rooted device, physical access to an unlocked device, or malware that already has full control of it.
- Problems in a third-party fork or in a modified build of the app.
- Reports without a demonstrated security impact, such as missing hardening that has no practical attack path.
- Denial of service by importing an extremely large file on the user's own device. (Individual episode numbers are capped to avoid memory problems, but a huge file will still take a long time.)

## Handling secrets in the repository

Contributors must never commit keystores, signing passwords, API tokens or personal data. If you find a secret in the repository or its history, report it privately as described above so that it can be revoked and removed.
