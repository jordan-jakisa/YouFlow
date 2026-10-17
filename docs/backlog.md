# YouFlow backlog

The single tracker for YouFlow: tasks with status, dated decisions, and dated checks. Detail lives in
the linked docs.

## Tasks

### Launch on X (started 2026-10-17)

Goal: a stranger who taps the link in the launch post lands on a page that says YouFlow, sees the
UI, and can build and install it on their phone by following the README.

| Task | Status | Notes |
|---|---|---|
| Push the 5 unpushed commits on `youtube-only` after build and unit tests pass | done 2026-10-17 | `assembleRelease assembleContinuous testDebugUnitTest`: 101 tests, 0 failures. |
| Fix CI: it failed on every push since 2026-10-03 (NewPipeExtractor submodule not checked out) | done 2026-10-17 | `submodules: true` in `ci.yml`. |
| Continuous build on a feature branch was labelled "YouFlow Debug" | done 2026-10-17 | Now "YouFlow Continuous". |
| Rename the GitHub repo `NewPipe` to `YouFlow` | open, waiting on Jordan | GitHub redirects the old URL. Show, then ask. |
| Set a YouFlow description and topics on the repo | open, waiting on Jordan | Current description is NewPipe's. |
| Remove the newpipe.net homepage from the repo | open, waiting on Jordan | Contradicts the README line about not using NewPipe's name. |
| Make `youtube-only` the default branch | open, waiting on Jordan | Default is `dev`, which shows NewPipe's README. |
| README build section tested from a fresh clone (JDK, Android Studio or SDK, submodules, build, install over USB) | in progress | |
| Phone screenshots from a clean profile in `fastlane/.../phoneScreenshots` | in progress | Jordan's Pixel 8a over wireless adb, separate "YouFlow Continuous" app with empty data. No YouTube logo; Creative Commons videos on screen. |
| 20 to 30 second screen recording (home, player, Shorts, downloads) | in progress | |
| README: 2 or 3 screenshots at the top | in progress | |
| Developer verification note and user install guidance | done 2026-10-17 | [developer-verification.md](developer-verification.md). |
| Signed release APK, GitHub Release, release workflow, repo signing secrets | not planned 2026-10-17 | Jordan: source only, see Decisions. |
| Release keystore in `~/keys/youflow/` (made before the source-only decision) | open, Jordan | Unused. Keep or delete, Jordan's call. |
| Move Jordan's data from "YouFlow Debug" to "YouFlow Continuous" on his phone (Export backup, Restore backup) | open, Jordan | After the screenshots; he wants to use the optimised build. |

### F-Droid

| Task | Status | Notes |
|---|---|---|
| Recipe, metadata and policy check | done 2026-10-04 | [fdroid/README.md](fdroid/README.md) |
| Decide whether F-Droid still fits "source only" | open, Jordan | F-Droid builds from source and signs itself; no APK from us. |
| Open the fdroiddata merge request | open | After that decision. |

## Decisions

| Date | Decision | Who | Why |
|---|---|---|---|
| 2026-10-17 | YouFlow never goes on Google Play, stays open source and earns nothing. | Jordan ("this app will never go to google play it will remain open source. it will earn nothing as well that is the point.") | It is a free tool, not a business; keeps it away from the Play developer account. |
| 2026-10-17 | YouFlow is not registered with Google's Android developer verification, under any Google identity. | Jordan ("No i will not do that") | Keeps an ad-free YouTube client with downloads off his Google identity. Building and installing over ADB stays exempt in 2027. [developer-verification.md](developer-verification.md) |
| 2026-10-17 | YouFlow ships as source only: no release APK, no signing key, no repo secrets, no release workflow. People build it and install it on their own phone. | Jordan ("it's just an open source project that anyone can compile and run on their phone") | Replaces the earlier plan the same day to sign APKs on GitHub Actions ("You can compile the apk in gthub releases"), then "We will not sign it". |
| 2026-10-17 | Screenshots and the clip are made on Jordan's Pixel 8a over wireless adb. | Jordan ("You can use py androudid p[hjhone over wireless adb for screenshots and all") | Real device instead of the emulator. |

## Checks

| Date | What to look at |
|---|---|
| 2027-01-15 | Google's developer verification FAQ and timeline: the exact 2027 date, changes to the advanced flow, whether F-Droid installs are covered. Update the README. |
