# Mor Ink Shop Soundtrack — App Rebrand & Setup Roadmap

Status: first implementation is in `input/MorInkSoundtrackAndroid_Source-v0.2.0.zip` on `soundtrack-branding-setup`. The source has not yet been built or device-verified.

## Product goal

Turn the existing Mor Ink Shop Soundtrack Android helper into a polished, clearly branded app that can be installed independently and configured by other shops without requiring Claude or a Wix account.

## Visual direction

- Default to a premium dark theme.
- Use Mor Ink's own logo where available; do not substitute a generic music logo.
- Use the studio palette: near-black surfaces, warm ivory text, muted stone-grey secondary text, and restrained antique-gold accents.
- Keep screens uncluttered, with clear hierarchy, comfortable touch targets, and responsive layouts.
- Provide an optional light theme, but retain dark as the default.
- Use record/vinyl-inspired details sparingly; avoid making the interface look like a generic music player.

## Core behavior to preserve

- Listen for Android Now Playing notifications only after the user explicitly grants notification access.
- Show a clear connection/status state and recent captured songs.
- Allow the user to configure a website receiver URL and a shared secret.
- Keep local song history usable even if the website endpoint is unavailable.
- Surface sync errors in understandable language and allow retrying.
- Do not silently enable notification access, change Android system settings, or promise that an app can bypass Android permission prompts. Android requires the user to grant sensitive permissions themselves.

## Setup experience

1. Welcome screen explains what the app does.
2. Permission screen explains why notification access is needed and opens Android Settings when the user taps the button.
3. Connection screen accepts a receiver URL and secret.
4. A test-connection action confirms whether the endpoint can be reached before the user starts.
5. A status screen shows permission state, sync state, last captured song, and recent songs.
6. Include an optional guided setup checklist for Pixel / Android. The app must not claim it can automatically enable Now Playing or notification access on the user's behalf.

## Website integration kit

Provide copy-ready embed examples and configuration instructions for:
- Wix (including the relevant custom-code / embed placement guidance).
- Other website builders that support HTML embeds.
- A generic receiver API contract, so non-Wix users are not blocked.

Keep the website receiver endpoint and secret configurable. Never hard-code a shop's secret into public HTML or a shared template. Document that the receiver secret must be kept private and rotated if exposed.

## Optional AI-assisted setup

Add a **Get AI Help** section that:
- Explains that it is optional.
- Offers a copyable prompt the user can paste into Claude or another AI assistant.
- Asks the AI assistant to identify the user's website platform first and give platform-specific steps.
- Does not require a Wix account.
- Never requests passwords, GitHub tokens, API keys, or receiver secrets in the prompt.

## Release and quality gates

Before publishing an updated APK:
- Inspect the actual Android source archive and make changes to source files, not only this plan.
- Confirm existing Now Playing capture and sync behavior remains intact.
- Verify permission-denied, offline, invalid-URL, invalid-secret, and failed-sync states.
- Build a debug APK through AndroidForge and inspect the build logs.
- Install and test on a real Android / Pixel device.
- Do not merge to `main` until the build and basic device checks pass.

## Important current limitation

The Android app source currently lives in ZIP archives under `input/` in the AndroidForge repository. AndroidForge is the build system, not the app's source tree. Actual UI implementation requires extracting and modifying the correct source archive, then rebuilding and testing it. A planning document alone does not implement the requested features.


## 2026-10-12 implementation update

Version 0.2.0 adds a dark Mor Ink branded setup screen, explicit notification-access guidance, Always On / Scheduled / Location Only / Off capture modes, weekday and local-time schedule controls, a manual capture override, studio name and coordinates, secure HTTPS receiver URL validation, recent-song status, event identifiers and queue deduplication, and a safe copyable AI setup prompt. Location Only uses Android's last-known location on notification events and a 250 m radius; it is not a continuous geofence and needs real-device validation.

Website-side Wix receiver setup and multi-studio routing are not implemented by this Android-only source change. A successful CI build and device checks are still required before describing an APK as ready to install.
