# Google's Android developer verification and YouFlow

Checked against Google's official pages on 2026-10-17:
[overview and timeline](https://developer.android.com/developer-verification/guides) (updated
2026-08-18), [FAQ](https://developer.android.com/developer-verification/guides/faq) (updated
2026-09-30), [open-source apps](https://developer.android.com/developer-verification/guides/open-source-app-registration)
(updated 2026-09-30), [limited distribution](https://developer.android.com/developer-verification/guides/limited-distribution)
(updated 2026-09-30).

## Decisions

- 2026-10-17, Jordan: YouFlow will **not** be registered with Google's developer verification ("No i
  will not do that"). Nothing has been registered or verified, under any Google identity.
- 2026-10-17, Jordan: YouFlow will never be on Google Play and earns nothing.
- 2026-10-17, Jordan: YouFlow ships as source only, no release APK ("it's just an open source
  project that anyone can compile and run on their phone"). People build it and install it on
  their own phone.

## What Google's rules say today

- **Since 2026-09-30, in Brazil, Indonesia, Singapore and Thailand,** certified devices check the
  developer only for installs from participating stores: Google Play, HONOR App Market, OPPO App
  Market, Galaxy Store, Palm Store, V-Appstore and Xiaomi GetApps. The FAQ: if users sideload an app
  directly, "these new verification requirements won't apply to your app yet." So a YouFlow build
  installs as before in every country today.
- **In 2027** Google expands this "globally for all apps on certified Android devices" (Android 7
  and newer). No exact 2027 date is published. From then, installing YouFlow on a certified phone
  needs either ADB (which is how Android Studio installs) or the advanced flow. This also covers
  F-Droid installs: F-Droid is not a participating store, and Google says verification "will soon
  be expanded to all third-party Android app stores."
- **Advanced flow** (launched August 2026), one time per phone:
  1. Turn on developer options.
  2. Answer a check that nobody is talking you into it.
  3. Restart the phone and unlock it again.
  4. Wait one day, then confirm with fingerprint, face or PIN.
  5. Allow installs from unverified developers for 7 days or indefinitely. Each install still shows
     a warning with "Install Anyway".
- **Updates:** unregistered apps "can only be installed or updated when the advanced flow is enabled
  or by using ADB". If a user turns the flow off (or picked 7 days and it ran out), YouFlow updates
  fail until they turn it on again.
- **ADB** installs stay exempt.

## What this means for people who build YouFlow

| How they install | Now | From the 2027 rollout |
|---|---|---|
| Run from Android Studio, or `adb install` / `./gradlew installContinuous` over USB | Works | Works: ADB installs are exempt, per the FAQ. |
| Copy the built APK to the phone and tap it | Works | Needs the one-time advanced flow (one-day wait), then "Install Anyway" on each install and update. |
| Phones without Google certification (GrapheneOS, LineageOS without Google apps) | Works | Not affected, as far as Google's pages say. |

So the README tells people to install over USB (Android Studio or adb), which keeps working in
2027, and mentions the advanced flow for anyone who copies the APK over instead.

## Dated check

- **2027-01-15**: reread the FAQ and timeline pages for the exact 2027 date, any change to the
  advanced flow, and whether F-Droid installs are covered; update the README.
