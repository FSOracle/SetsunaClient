# Setsuna

This directory contains the Setsuna open-source release.

The distributed copy is an ordinary Fabric client source tree. Account
verification, device binding, release-integrity checks, PhantomShield hooks,
and the server-side authentication project are intentionally absent. The
client initializes its features locally on startup.

## Build

Use JDK 25 and run:

```powershell
.\gradlew.bat clean build
```

The default ClickGUI mode is `Pop`. The remaining optional `Drop` mode can be
selected in the ClickGUI settings.

Bundled mini-games remain available for local play. Network invitations and
online mini-game sessions are not included in this release.
