# Publishing YouFlow on F-Droid

Status: the repository is prepared; nothing has been submitted. Checked against the official pages on
2026-10-04: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/),
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

## Already in the repo

- NewPipeExtractor is a git submodule pinned to the commit in `gradle/libs.versions.toml`
  (`submodules: true` in the recipe).
- `dependenciesInfo` is off, release lint is off (it crashes on JDK 17), the Gradle wrapper has a
  checksum.
- `fastlane/metadata/android/en-US` has title, descriptions and changelog.
- Draft recipe: `docs/fdroid/dev.jordanempire.youflow.yml` (uses `subdir: app` like NewPipe's).
- `./gradlew assembleRelease` makes an unsigned, minified APK that launches without crashes.

## Done

- Phone screenshots in `images/phoneScreenshots/` (2026-10-17, clean profile, Blender Studio CC BY videos).
- 512x512 icon in `fastlane/metadata/android/en-US/images/icon.png`.
- Tag `v0.1.0` and the `youtube-only` branch are pushed to GitHub; the tag's hash is in the recipe.
- Icon changed from saturated red to the soft rose of the app palette, so it does not read as YouTube's red play button.
- Recipe passes `fdroid lint` and `fdroid rewritemeta` against a current fdroiddata checkout.
- A fresh `git clone --recurse-submodules` builds `assembleRelease` to the same APK.

## Still to do

1. Fork https://gitlab.com/fdroid/fdroiddata, add `metadata/dev.jordanempire.youflow.yml`, commit as
   `New App: dev.jordanempire.youflow`, open a merge request. Its CI runs the real `fdroid build`; a
   local `fdroid build -l` only works inside F-Droid's build server image (it needs their
   `gradlew-fdroid`), so it was not run. Listing appears about 24 to 48 hours after merge.

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
