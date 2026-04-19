# Android VPN Library — Extended Edition

This repository contains an Android OpenVPN client library with significant modifications that I made several years ago to reach feature‑parity with commercial VPN apps.  It builds on the OpenVPN/ICS‑OpenVPN stack and introduces a kill‑switch, improved notification handling, and other usability refinements.

## ✨ Key Enhancements

### Kill‑Switch Support
A new `KillswitchService` and related hooks were added to enforce a “no‑leak” policy.  When the user enables the kill‑switch preference, the library starts a small dummy VPN (the kill‑switch) whenever the main VPN process ends unexpectedly.  This dummy VPN blocks network traffic until the real VPN reconnects, preventing accidental traffic leaks.

### Dynamic Notification & Control Changes
Notifications were overhauled to give clearer status information and better Android‑O integration.  The code now uses `NotificationChannel` with a descriptive name (“VPN Status”) and proper importance levels.  Pause/resume actions were removed in favour of a simpler “disconnect” control, and the disconnect intent now launches the app instead of a separate `DisconnectVPNActivity`.

### Removal of Timeouts and Static Profile IDs
The upstream project supported per‑profile timeouts and profile IDs; these were stripped away to streamline the API.  Methods like `OpenVpnApi.startVpn` no longer accept timeout or profile‑ID arguments, and the associated fields were removed from `VpnProfile`.  Connection state is managed via shared preferences rather than a timeout thread.

### Updated Broadcast & Status Handling
The fork relies on `LocalBroadcastManager` to broadcast connection state changes directly.  It removes legacy calls that wrote status into “flutter_openvpn” preferences and instead sends real broadcasts so the app can react promptly.

### Branding & String Updates
Strings were updated from the upstream names (“VPNex”) to a new project name (“CentriVPN”) in notifications and session strings to reflect the customised app branding.

### General Code Clean‑ups
- Hard‑coded server/profile handling was removed in favour of cleaner state management.
- Several deprecated methods and unused fields were deleted.
- Minor bug fixes and spelling corrections (e.g., “Copyroight” → “Copyright”).

## 🛠 Project Goals

This fork began as a learning project but quickly evolved into a feature‑complete Android VPN client.  My goal was to replicate the kill‑switch and notification behaviours found in commercial VPN apps while keeping everything open source.  The changes here demonstrate how to extend the open‑source OpenVPN client to enforce traffic‑leak prevention, improve user notifications, and simplify profile handling.

## 📜 Licensing

The original OpenVPN code is under GPL v2 with additional terms; my modifications remain under the same license.  Please review the upstream license for details.

---
