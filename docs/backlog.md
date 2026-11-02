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
| On a fresh install Home opened on the "Live" chip (random livestreams) until there is history, likes or a subscription | done 2026-11-02 | Jordan: "fix everything". A fresh install opens on HN picks (curated, no strangers' livestreams); For you once there is history, a like, a subscription or imported history. If HN picks is switched off, the first YouTube list. |
| Developer verification note and user install guidance | done 2026-10-17 | [developer-verification.md](developer-verification.md). |
| Launch post on X (@heylengu) | done 2026-10-18 | https://x.com/heylengu/status/2111574501888581863, sent 2026-10-18 00:46 EAT after Jordan approved it. 18 s demo in an Easlo-style frame, from the Pixel 8a recording. Baseline at posting: 0 stars, 0 forks, GitHub traffic 20 views / 1 unique visitor. Issues or questions from launch traffic go to Jordan before anyone replies. |
| Signed release APK, GitHub Release, release workflow, repo signing secrets | not planned 2026-10-17 | Jordan: source only, see Decisions. |
| Bug: Shorts kept showing an unsubscribed channel's Shorts (saved list and the list on screen) | done 2026-10-18 | Found on the phone after unsubscribing Marc Lou and Marques Brownlee in the test app. Saved and shown Shorts are now filtered to current subscriptions. 3 more tests in `ShortsPagesTest`. |
| Leave GitHub's fork network | not planned 2026-10-17 | Jordan: stays a fork, see Decisions. |
| Release keystore in `~/keys/youflow/` (made before the source-only decision) | kept 2026-11-02 | Jordan: "Keep it." Unused; do not delete. |
| Move Jordan's data from "YouFlow Debug" to the optimised build (now labelled "YouFlow") on his phone (Export backup, Restore backup) | in progress 2026-11-02 | Jordan: "Yes please", knowing a restore replaces what YouFlow saved since 18 Oct. |

### Recommendations, the HackerTube way (started 2026-11-01)

Idea from Karpathy's HackerTube (hackertube.org, 2026-11-01): rank by what a community keeps
recommending over time, not by watch time. YouFlow earns nothing, so no demand validation; whether
the feed is better is unmeasured until Jordan has used it for a week (see Checks).

| Task | Status | Notes |
|---|---|---|
| Lasting-interest ranking in For you | done 2026-11-01 | Taste counts a topic once per day it was watched, channel affinity uses distinct days, not plays. `TasteProfile.fromDays`. |
| "Why it's here" line on For you, HN picks and topic cards | done 2026-11-01 | "Because you watched ...", "New from a channel you follow", "Recommended on Hacker News in N different months", "Related to videos from X and Y". |
| Frame thumbnails setting | done 2026-11-01 | Settings, Appearance. YouTube's automatic middle frame (hq2.jpg), falls back to the uploader's thumbnail. Off by default. |
| HN picks chip, and HN favourites mixed into For you when they match your taste | done 2026-11-01 | `assets/hn_picks.json`: 4,363 of HackerTube's 6,454 videos (news-only, music, comedy, never-recommended and duplicate uploads left out), 1.5 MB, plus "HN people also recommend". ODC-By 1.0, credited in Settings, About and the README. |
| Community topic chips: Developers, AI builders, Startups, Indie hackers, Hackers | done 2026-11-01 | `CommunityPacks.kt`. A video makes the list when 2+ pack channels point to it (related videos of their uploads), plus their own uploads. Channel ids checked on YouTube 2026-11-01. Switch topics on or off in Settings, Topics on Home. |
| Add "Boomer Ranting in the Woods" to a pack | done 2026-11-01 | Developers. Found from Jordan's own For you on the phone. |
| Add "the Vestibule" to a pack | not planned 2026-11-02 | Jordan: "Leave it." No YouTube channel by that name was found. |
| Settings placement after the UI check | done 2026-11-01 | Topics on Home and Frame thumbnails in Appearance; Import and Clear imported YouTube history in History and privacy; shorter texts; frame thumbnails also in the player and mini player when audio only. |
| Production build "YouFlow" updated on Jordan's Pixel 8a | done 2026-11-01 | `adb install -r`, data kept. Home on the phone: topic chips and reason lines show. |
| Import YouTube watch history from Google Takeout | done 2026-11-01 | Settings, History and privacy, Import YouTube history: watch-history.json, .html or the whole zip. Kept in app storage (max 5,000), read only by the recommender, not added to History; Clear imported YouTube history removes it. |
| Continue watching moved from Home to the You page | done 2026-11-01 | Jordan asked. |
| Bug: tapping a Home chip reloaded the whole page, chips included | done 2026-11-01 | Jordan found it. The chips sit above the list now and only the list reloads; a new topic starts at the top. Checked on the Pixel 8a. |
| Keep the screen on while a video plays | done 2026-11-01 | Jordan asked. Settings, Playback, "Keep screen on while playing", on by default. Off when paused or audio only; the power button still turns the screen off. Checked on the Pixel 8a: KEEP_SCREEN_ON set while playing, cleared on pause. |
| Bug: topic lists showed popular off-topic videos (a Daily Show clip and Ugandan politics on top of AI builders) | done 2026-11-02 | Found on the phone. YouTube's related lists carry whatever is popular in the region. A video from outside the topic's channels now has to share words with what those channels post about (`CommunityPacks.onTopic`, tested). Fresh AI builders list not yet seen on the phone. |
| Check on Jordan's phone: HN picks, each topic chip, Takeout import, frame thumbnails | in progress | 2026-11-02: HN picks, Developers, Startups and Indie hackers show on-topic lists with reasons. Not yet seen: AI builders after the fix, Hackers, frame thumbnails, a Takeout import. Stopped because Readback came to the front on the phone. |
| Launch results on GitHub (check due 2026-10-21) | done 2026-11-02 | 0 stars, 0 forks, 0 watchers. Views 25 / 12 unique over 14 days (17 / 5 on launch day, then 0 to 2 a day). Referrers: github.com 8, duck.ai 1; none from X. Clones 65 / 31 unique, mostly CI and bots. Issues and Discussions are switched off, so a stranger has no way to report a problem. Bad news, for Jordan to assess. |
| Turn on Issues (or Discussions) on the repo so people can report problems | not planned 2026-11-02 | Jordan: "No." Issues and Discussions stay off. |
| CI pinned to `ubuntu-24.04` | done 2026-11-02 | GitHub is moving `ubuntu-latest` to Ubuntu 26; pinned so CI does not change under us. |

### F-Droid

| Task | Status | Notes |
|---|---|---|
| Recipe, metadata and policy check | done 2026-10-04 | [fdroid/README.md](fdroid/README.md) |
| Decide whether F-Droid still fits "source only" | done 2026-11-02 | Jordan: "Yes, it should. go on f-droid." See Decisions. |
| Bring the F-Droid recipe and metadata up to date with the November work | in progress 2026-11-02 | Prepared on 2026-10-04 against v0.1.0; since then HN picks, topic chips, Takeout import and new settings. |
| Open the fdroiddata merge request | open | Needs Jordan: a GitLab account in his name, and his yes on the merge request text before it is sent. |

## Decisions

| Date | Decision | Who | Why |
|---|---|---|---|
| 2026-10-17 | YouFlow never goes on Google Play, stays open source and earns nothing. | Jordan ("this app will never go to google play it will remain open source. it will earn nothing as well that is the point.") | It is a free tool, not a business; keeps it away from the Play developer account. |
| 2026-10-17 | YouFlow is not registered with Google's Android developer verification, under any Google identity. | Jordan ("No i will not do that") | Keeps an ad-free YouTube client with downloads off his Google identity. Building and installing over ADB stays exempt in 2027. [developer-verification.md](developer-verification.md) |
| 2026-10-17 | YouFlow ships as source only: no release APK, no signing key, no repo secrets, no release workflow. People build it and install it on their own phone. | Jordan ("it's just an open source project that anyone can compile and run on their phone") | Replaces the earlier plan the same day to sign APKs on GitHub Actions ("You can compile the apk in gthub releases"), then "We will not sign it". |
| 2026-10-17 | Screenshots and the clip are made on Jordan's Pixel 8a over wireless adb. | Jordan ("You can use py androudid p[hjhone over wireless adb for screenshots and all") | Real device instead of the emulator. |

| 2026-10-17 | Shorts never fall back to a `#shorts` search; with too few Shorts from subscriptions the screen asks the user to subscribe. | Jordan ("Replace the random Shorts fallback with a 'Subscribe to channels' prompt.") | On a clean profile the fallback showed strangers' videos, including a family video with children. |
| 2026-11-01 | Adopt HackerTube's ideas: lasting-interest ranking, reason lines, frame thumbnails, bundled HN picks, community topic chips, Takeout history import; Continue watching moves to the You page. | Jordan ("Can we adopt this into the app", picked all four options, added the community lists and the Takeout import, "let's apply all of this learning") | Better recommendations without an account; everything stays on the device. |
| 2026-11-02 | YouFlow goes on F-Droid. F-Droid builds it from our source and signs it, which fits "source only": we still publish no APK and hold no signing key in the repo. | Jordan ("Yes, it should. go on f-droid.") | The only way most people will install it without building it themselves. |
| 2026-11-02 | GitHub Issues and Discussions stay off; the unused release keystore is kept; "the Vestibule" is dropped. | Jordan ("No.", "Keep it.", "Leave it.") | His call after the launch results. |
| 2026-10-17 | YouFlow stays a GitHub fork of NewPipe; it does not leave the fork network. | Jordan ("Keep YouFlow as a fork of NewPipe.") | Known effect: GitHub search hides forks by default, so discovery comes from the X post and the repo topics, not GitHub search. |

## Checks

| Date | What to look at |
|---|---|
| 2026-11-08 | After a week of Jordan using the build: does For you feel better? Which reason lines show most, which topic chips get opened, any chip that is empty or slow (over 10 s). Decide whether frame thumbnails become the default. |
| 2027-01-15 | Google's developer verification FAQ and timeline: the exact 2027 date, changes to the advanced flow, whether F-Droid installs are covered. Update the README. |
| 2027-04-01 | Ubuntu 26.04 runner on GitHub Actions: generally available? Move CI from `ubuntu-24.04` to it and check the build passes. |
