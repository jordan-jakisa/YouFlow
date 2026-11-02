# Publishing YouFlow on F-Droid

Status: Jordan decided on 2026-11-02 that YouFlow goes on F-Droid. The repository is prepared for
v0.2.0; nothing has been submitted. Checked against the official pages on 2026-10-04 and again on
2026-11-02: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/),
[Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/),
[Anti-Features](https://f-droid.org/docs/Anti-Features/),
[Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/),
[Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/),
[Quick Start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

## Policy checklist

| Rule | Status |
|---|---|
| FLOSS licence | GPL-3.0-or-later, `LICENSE` in the repo. |
| Public source, built with a 100% FLOSS toolchain (no Oracle JDK) | Public GitHub repo; builds with OpenJDK. |
| Dependencies are FLOSS from allowed repos (Maven Central, Google Maven, JitPack, Clojars) | Yes: `settings.gradle.kts` uses only those four. |
| No Play Services, Firebase, Crashlytics, proprietary ad or tracking SDKs | None in `releaseRuntimeClasspath`. LeakCanary is debug only. |
| No downloading of executables without opt-in | No update checker, no add-ons. The PoToken page (`assets/po_token.html`) runs in a WebView and loads YouTube's own script, as NewPipe does. |
| No JDK auto-download in the build | The foojay toolchain plugin was removed; the extractor asks for JDK 17, which the F-Droid build server has. |
| Fork needs a new application id, name, icon and strings | `dev.jordanempire.youflow`, name YouFlow, own icon, own strings. |
| Not a mere rebrand; useful and maintained | The whole UI and player are rewritten. |
| Must not infringe third-party trademarks | See risk 1 below. |
| Author consent | You are the author of the fork. |
| Anti-features | Declare `NonFreeNet`; NewPipe is listed with it ("Depends on Youtube for videos"). |
| Bundled data has a licence that allows redistribution | `assets/hn_picks.json` is HackerTube's data under ODC-By 1.0, credited in Settings, About, the README and the store description. |
| Actively developed for at least 3 months ("should", unless it has proven users, the author maintains other F-Droid apps, or it is for an event) | **Not yet.** YouFlow's own work started on 2026-10-03, so 3 months is 2027-01-03. None of the exceptions apply (0 stars, no other F-Droid apps). |

## Already in the repo

- NewPipeExtractor is a git submodule pinned to the commit in `gradle/libs.versions.toml`
  (`submodules: true` in the recipe).
- `dependenciesInfo` is off, release lint is off (it crashes on JDK 17), the Gradle wrapper has a
  checksum.
- The build needs JDK 17 (NewPipeExtractor) and JDK 21 (the app). F-Droid's build server runs Debian
  trixie, which only has 21, so the recipe installs 17 from bookworm, as NewPipe's recipe does.
- Version 0.2.0 (code 2), changelog `changelogs/2.txt`, updated `full_description.txt`.
- `fastlane/metadata/android/en-US` has title, descriptions and changelog.
- Draft recipe: `docs/fdroid/dev.jordanempire.youflow.yml` (uses `subdir: app` like NewPipe's).
- `./gradlew assembleRelease` makes an unsigned, minified APK that launches without crashes.

## Done

- Phone screenshots in `images/phoneScreenshots/` (2026-10-17, clean profile, Blender Studio CC BY videos).
- 512x512 icon in `fastlane/metadata/android/en-US/images/icon.png`.
- Tag `v0.1.0` and the `youtube-only` branch are pushed to GitHub. Tag `v0.2.0` exists locally and its
  hash is in the recipe; it is pushed with the branch once Jordan says push.
- Icon changed from saturated red to the soft rose of the app palette, so it does not read as YouTube's red play button.
- Recipe passes `fdroid lint` and `fdroid rewritemeta` (fdroidserver 2.4.5 with fdroiddata's current
  config, 2026-11-02). `IssueTracker` is left out because Issues are off on the repo (Jordan, 2026-11-02).
  `UpdateCheckData` reads the version from `buildSrc/.../ProjectConfig.kt`, as NewPipe's recipe does.
- A fresh `git clone --recurse-submodules` builds `assembleRelease` to the same APK.

## Still to do

1. Push the branch and tag `v0.2.0` (Jordan says push).
2. Phone screenshots: the ones from 2026-10-17 show the old Home. Retake Home with the topic chips
   and reason lines from a clean profile before the merge request.
3. From 2027-01-03 (3 months of development): Jordan forks https://gitlab.com/fdroid/fdroiddata with
   his own GitLab account, adds `metadata/dev.jordanempire.youflow.yml` (this recipe without the
   comment lines), commits `New App: dev.jordanempire.youflow` and opens the merge request with the
   `New App` label. Its CI runs the real `fdroid build`. Reviewers' questions are answered quickly in
   that thread. The listing appears about 24 to 48 hours after merge.

## Risks

1. Trademark: the name "YouFlow" and the "YouTube" wording in the description. NewPipe and other
   YouTube clients use "YouTube" descriptively and are listed, but the name begins with "You" and the
   icon is a play symbol. A reviewer could ask for a rename. Avoid anything that looks like YouTube's
   red play button or logo.
2. F-Droid signs the APK itself. A build signed with another key (a debug build) cannot be updated
   over by the F-Droid one. Optional: reproducible builds plus `AllowedAPKSigningKeys` would let
   F-Droid publish your own signature.
3. YouTube changes break the extractor from time to time. Updating means bumping the submodule commit
   and `teamnewpipe-newpipe-extractor` in `gradle/libs.versions.toml` together.
4. The Compose Material 3 alpha (`1.11.0-alpha07`) is a pre-release library. It resolves from Maven
   Central, so it is allowed, but pin a stable version when one exists.
5. Publishing a service that scrapes YouTube may breach YouTube's Terms of Service. F-Droid lists
   such clients, but that is a separate matter from F-Droid's rules.
