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
| Rename the GitHub repo `NewPipe` to `YouFlow` | done 2026-10-17 | Jordan approved in chat. https://github.com/jordan-jakisa/YouFlow; GitHub redirects the old URL. README clone URL and the F-Droid recipe updated. |
| Set a YouFlow description on the repo | done 2026-10-17 | Jordan approved the wording in chat. |
| Set topics on the repo | done 2026-10-17 | Jordan approved in chat: android, youtube-client, newpipe, material3, jetpack-compose, kotlin, privacy, no-ads. |
| Remove the newpipe.net homepage from the repo | done 2026-10-17 | Jordan approved in chat; homepage is empty. |
| Make `youtube-only` the default branch | done 2026-10-17 | Jordan approved in chat. Visitors now see the YouFlow README. |
| README build section tested from a fresh clone (JDK, Android Studio or SDK, submodules, build, install over USB) | done 2026-10-17 | Fresh `git clone --recurse-submodules` from GitHub with an empty Gradle cache: JDK 17 alone fails (`languageVersion=21` toolchain missing), JDK 17 plus 21 builds `assembleContinuous`; APK installed over adb. Android Studio's Run path not tested (no Android Studio on this Mac). |
| Phone screenshots from a clean profile in `fastlane/.../phoneScreenshots` | done 2026-10-17 | Home, player, Shorts, download sheet. Jordan's Pixel 8a over wireless adb, separate "YouFlow Continuous" app with empty data, only Blender Studio (CC BY) on screen, status bar cropped. |
| 20 to 30 second screen recording (home, player, Shorts, downloads) | done 2026-10-17 | `docs/media/youflow-demo.mp4`, 30 s, 540 px wide, 2.6 MB. A comment preview with a stranger's avatar is visible for a few seconds. |
| README: 3 screenshots, the demo link and build-and-install steps at the top | done 2026-10-17 | |
| Bug: with the app theme set to Dark on a phone in light mode, status bar icons stayed dark on dark | done 2026-10-17 | System bar style follows the app theme. |
| Bug: feed and channel-tab video cards showed a letter instead of the channel avatar | done 2026-10-17 | Avatar filled from the subscription or the channel. |
| Bug: an unplayable Short showed a black screen; a tiny cached Shorts list was never replaced, so the pager could not swipe | done 2026-10-17 | Message "This Short can't be played"; a cached list under 6 items is replaced by the fresh one. |
| Bug: Subscriptions feed kept an unsubscribed channel's videos until pull to refresh | done 2026-10-17 | Feed reloads when the subscription list changes. Unit tests pass; not yet checked on the phone (adb dropped). |
| Shorts: replace the random `#shorts` search fallback with a "Subscribe to channels" prompt | done 2026-10-17 | Shorts come only from subscribed channels. None: empty state "No Shorts yet" with one "Find channels" button that opens search. Some: those Shorts, then a closing page "You're all caught up" with the same button. New subscriptions show up without a restart. Old cache key retired so padded lists never show again. 8 unit tests in `ShortsPagesTest`. Empty state and Find channels checked on the emulator; on Jordan's phone the feed shows only that app's subscriptions. |
| The build the README installs was named "YouFlow Continuous" | done 2026-10-17 | Now "YouFlow" (app id unchanged, `...continuous.youtubeonly`, so existing installs update in place). Checked from a fresh clone of the default branch: `./gradlew installContinuous` onto the emulator, label "YouFlow". Debug builds stay "YouFlow Debug"; the README never makes one. |
| On a fresh install Home opens on the "Live" chip (random livestreams) until there is history, likes or a subscription | open, Jordan | Intended in `HomeViewModel`: first YouTube list, and Live comes first because YouTube dropped Trending. Product choice: keep Live, start on Trending music, or start on For you with an empty-state prompt. |
| Developer verification note and user install guidance | done 2026-10-17 | [developer-verification.md](developer-verification.md). |
| Signed release APK, GitHub Release, release workflow, repo signing secrets | not planned 2026-10-17 | Jordan: source only, see Decisions. |
| Bug: Shorts kept showing an unsubscribed channel's Shorts (saved list and the list on screen) | done 2026-10-18 | Found on the phone after unsubscribing Marc Lou and Marques Brownlee in the test app. Saved and shown Shorts are now filtered to current subscriptions. 3 more tests in `ShortsPagesTest`. |
| Leave GitHub's fork network | not planned 2026-10-17 | Jordan: stays a fork, see Decisions. |
| Release keystore in `~/keys/youflow/` (made before the source-only decision) | open, Jordan | Unused. Keep or delete, Jordan's call. |
| Move Jordan's data from "YouFlow Debug" to the optimised build (now labelled "YouFlow") on his phone (Export backup, Restore backup) | open, Jordan | After the screenshots; he wants to use the optimised build. |

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

| 2026-10-17 | Shorts never fall back to a `#shorts` search; with too few Shorts from subscriptions the screen asks the user to subscribe. | Jordan ("Replace the random Shorts fallback with a 'Subscribe to channels' prompt.") | On a clean profile the fallback showed strangers' videos, including a family video with children. |
| 2026-10-17 | YouFlow stays a GitHub fork of NewPipe; it does not leave the fork network. | Jordan ("Keep YouFlow as a fork of NewPipe.") | Known effect: GitHub search hides forks by default, so discovery comes from the X post and the repo topics, not GitHub search. |

## Checks

| Date | What to look at |
|---|---|
| 2027-01-15 | Google's developer verification FAQ and timeline: the exact 2027 date, changes to the advanced flow, whether F-Droid installs are covered. Update the README. |
