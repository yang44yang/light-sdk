# Routine development and verification

Routine is the habit tracker in `tool/`. Its display name is `Routine`; the
application ID remains `io.github.yang44yang.habits` so existing app data and
package identity are preserved.

Current candidate: **1.3.1 / version code 8**. See the
[user guide](USER_GUIDE.md), [privacy information](PRIVACY.md), and
[submission draft and checklist](SUBMISSION.md). Verification sections below
record successive development stages; later acceptance sections specify which
earlier limitations have been resolved.

## Habit examples and author contact — 1.3.1

The introduction uses Strength training (Habit +), No alcohol (Habit ±), and
No food after 8pm (Habit ±) to explain each button. Help explains how to record
daily goals manually and how to name an unwanted action for negative-only
tracking. Every editor type shows a short contextual explanation.

**Help → About** displays the version, author / X account
**[@BitVendetta](https://x.com/BitVendetta)**, feedback email
**yang.yang.pro@gmail.com**, and the public source repository. These are
author-approved, static public contact details, with no external navigation
or networking. They are also included in the local submission draft.

The update does not seed examples, change scoring, rename existing tasks, or
change the database / backup format. First-use dismissal remains unchanged.

## First use — 1.3.0

A new installation opens a short, skippable introduction to adding, recording,
and optional personal rewards. **Add a habit** opens an unsaved editor;
**Skip** opens the empty home. Dismissal is stored locally through the SDK
DataStore. Closing before dismissal leaves the introduction available on the
next launch. Existing installations go straight to their records.

**Help** at the top right of the home reopens the guide, with details about
counts versus gold, one-time to-dos, reward undo, and backup compatibility.
The Data screen now explicitly explains that LightOS 582 does not expose
Routine files and that Saved only confirms a local file. The database and
backup format remain version 3.

## SDK baseline

- Light SDK: 0.2.0, upstream commit `311aa2d` (2026-10-08).
- Previous checkout: `67a3502`, retained on
  `backup/habits-before-routine-2026-10-09`.
- Integration branch: `codex/routine-sdk-baseline`.
- JDK 17, Android SDK platform 36; minimum and target Android API 34 match
  the upstream SDK settings.

Use `sdk.dir` in an ignored `local.properties`, or set `ANDROID_HOME` to your
Android SDK directory. Select JDK 17 with `JAVA_HOME` if necessary.

## Device builds

Run from the repository root:

```sh
./gradlew :tool:assembleDebug :tool:lintDebug -DlightSdk.toolOnly=true
```

The APK is `tool/build/outputs/apk/debug/tool-debug.apk`. This is a local
development build, signed with the SDK's bundled development key. It is not
a Light-approved release.

`tool/lighttool.toml` defaults to `serverPackage = "com.lightos"`, as required
by the submission builder. The initial baseline used `1.0.0` / code `1`;
subsequent versions increment the version code.

## Emulator builds

For the standalone Routine UI preview, build the normal debug APK above and
install it on the Android emulator. Open the tool itself, not the Android home
screen. This mode uses the Light SDK UI and embedded keyboard without a running
LightOS host; host-dependent features require the integration setup below.

The existing local AVD `Medium_Phone` is now displayed as **Light Phone III**,
with a physical virtual display of 1080 x 1240 at 420 dpi. Its data partition is
preserved. It is visible in Android Studio's Running Devices panel. To reopen
Routine on the running emulator after a build:

```sh
adb -s emulator-5554 install -r tool/build/outputs/apk/debug/tool-debug.apk
adb -s emulator-5554 shell am start -W \
  -n io.github.yang44yang.habits/com.thelightphone.sdk.LightActivity
```

For integration with the **LightOS emulator host** (a separate application),
follow `docs/system_app/README.md` to install and configure that host first.
Use a separate local copy of the SDK with the same Routine `tool/` sources.
In that copy only, change the active `serverPackage` to
`"com.thelightphone.sdk.emulator"`, then run:

```sh
./gradlew :tool:assembleDebug -DlightSdk.toolOnly=true \
  -DlightSdk.allowAltServerPackage=true
```

The flag permits an alternative host; it does not change the package by
itself. Keep the committed metadata targeting `com.lightos`. Emulator and
device APKs have the same application ID and therefore replace each other
when installed on the same device.

## Submission compatibility check

The official `builder/lightbuilder/extract.py` copies `tool/build.gradle.kts`,
`tool/lighttool.toml`, and permitted files under `tool/src/main/` into a clean
SDK checkout. Do not depend on custom root build changes or extra modules.

After extraction, build that clean checkout with:

```sh
./gradlew :tool:assembleRelease -DlightSdk.toolOnly=true \
  -DlightSdk.unsigned=true -DlightSdk.abiFilters=arm64-v8a
```

This checks source isolation and release compilation on the local machine.
It does not reproduce Light's Linux container, Maven proxy, signing service,
or approval process. The resulting unsigned APK cannot be installed directly.

## SDK baseline verification on 2026-10-09

- Device debug build and `lintDebug`: passed, 0 errors / 1 warning. The warning
  is `MissingApplicationIcon` in the SDK-generated manifest.
- Official source extraction: passed (8 tool files).
- Clean upstream SDK, arm64 unsigned release build: passed, including R8 and
  resource shrinking. R8 emitted Kotlin metadata warnings; these remain visible
  in the build log rather than being suppressed.
- Separate emulator-host debug build: passed.
- Both manifests: verified display name, original application ID, SDK 0.2.0,
  and the correct device/emulator server package.
- The optimized release APK was signed with the bundled development key only
  for a local smoke test. Both it and the emulator debug APK installed and
  displayed the home screen on a read-only Android API 34 emulator at
  1080 x 1240 / 420 dpi. No crash-buffer entries were observed. After replacing
  the release APK with the debug APK, a cold restart was used to verify the UI.
- This was a standalone Android emulator without the LightOS system host.
  Host services, real-device installation, keyboard flows, and full functional
  acceptance are not covered by this startup check.

Logs, manifests, APKs, and startup screenshots are retained locally under the
ignored `local/routine-baseline/` directory. The committed tool remains
configured for `com.lightos`.

## Stability fixes on 2026-10-09

- Habit scoring, todo completion, reward purchases, and their event logs now
  commit in one Room transaction. Failed log writes roll back the whole action.
- Completing the same todo concurrently credits it once. Purchases recheck the
  current price and balance; repeated taps while a purchase is pending are ignored.
- Wallet updates clamp to the non-negative Int range and log the actual change,
  including a partial deduction or zero change at the limit.
- Deleted tasks/rewards cannot be used through stale UI objects. Editing updates
  only editable columns, preserving completion/deletion state. Initial examples
  are seeded once, including when every example has subsequently been deleted.
- Per-habit week/month/year counts refresh at local midnight and on return to the
  home screen. Clock/timezone changes are detected within a minute while visible.
  Wallet balance, lifetime counts, and historical events are retained.
- Task/reward editors return only after saving succeeds, preventing navigation
  from cancelling a pending save. Invalid or overflowing prices show an error
  instead of becoming free rewards.
- The database filename (`habit_v2.db`), schema version, and application ID are
  unchanged; this update requires no data migration.

The optional `verification` Android module compiles the production tool sources
directly and tests them against real in-memory Room databases. It is excluded
from ordinary builds and is not extracted into the submission build. Run on a
connected Android API 34+ emulator/device:

```sh
./gradlew :verification:connectedDebugAndroidTest \
  -DlightSdk.toolOnly=true -ProutineVerification=true
```

Results on Medium Phone / Android API 34 (`emulator-5554`):

- 18 tests passed: concurrent scoring (100 operations), todo completion and
  purchases (40 each), rollback on write failure, stale records, wallet limits,
  initialization, editor persistence, invalid prices, and calendar boundaries.
- Device debug build and lint passed: 0 errors / the existing 1 SDK manifest
  icon warning. Official extraction passed (10 tool files), and the extracted
  sources built successfully as an arm64 unsigned release on clean SDK `311aa2d`.
- The updated debug APK installed over the existing emulator app successfully;
  its previous databases were backed up locally before installation.
- Manual UI acceptance was initially blocked: computer control could read the embedded
  emulator but coordinate actions repeatedly failed with `noWindowsAvailable`,
  including after reconnecting. Automated tests cover repository, ViewModel,
  and date-flow behavior; they do not establish keyboard/touch UI acceptance or
  LightOS host integration. The UI acceptance below subsequently used ADB with
  the user's explicit authorization.

Reports: `verification/build/reports/androidTests/connected/debug/index.html`.
Build logs, extraction output, APKs, and the emulator database backup are in the
ignored `local/routine-stability/` directory. Backups must remain local.

## Public UI and acceptance on 2026-10-09

- App-owned UI strings use Android resources: English fallback, Simplified
  Chinese, and Traditional Chinese (Taiwan), following the system locale.
  Saved task/reward names remain user content and are never translated.
- New installations start with an empty task list and zero gold, with short
  empty-state instructions. Initialization only ensures the wallet exists;
  existing tasks, completion history, and balances are preserved.
- The home actions have equal-width slots with at least 48 dp touch areas.
  Task/reward titles are limited to two lines in lists, and editor forms scroll.
  The embedded keyboard uses a localized Done label and single-line input.
- Export failures now show a localized message; successful export explicitly
  says the CSV was saved locally. Tool Manager download is still pending.

Verified on the visible Android API 34 emulator at LP3 resolution:

- Enter a reward name through the SDK editor, save a price of 10, buy it, and
  verify zero balance prevents a second purchase.
- Create a todo, complete it, and verify the balance increases by 10.
- Create a long-name bidirectional habit; positive and negative scoring each
  update the count and balance. Change its period to a month, restart/update
  the app, and verify the saved period and wallet persist.
- Delete the QA habit and reward through their editors; existing personal
  items remain. QA events and the completed QA todo remain in local history.
- Check English, Simplified Chinese, and Traditional Chinese screens using
  Android app-locale overrides; clear the override afterward to follow the
  system again. Inspect the English home/rewards screen and Chinese editor.
- Cold-boot the resized AVD and open Routine again; physical display reports
  1080 x 1240, without a window-manager size override.
- Rerun all 18 regression tests: passed, including empty initialization and
  preservation of existing user data. Debug + lint passed (0 errors, the same
  SDK icon warning). Official extraction passed (15 tool files); the clean SDK
  arm64 unsigned release build passed.

Screenshots, logs, extracted sources, APKs, and local backups are under ignored
`local/routine-acceptance/`. Personal-data screenshots are development records,
not public submission artwork. This verifies the standalone SDK tool; LightOS
host services, real LP3 hardware, and Tool Manager export remain unverified.

## Release tracking

Current remaining work is in [SUBMISSION.md](SUBMISSION.md). The initial
app-private CSV exporter is replaced by the 1.0.2 Data screen described below.
Do not uninstall an existing installation to resolve signing differences
without first protecting its data.

## Home tab alignment and counts — 1.2.1 (2026-10-10)

Habits and To-dos labels and their selection underlines now start at the left
edge of their respective half of the row. Item counts use square brackets in
English, Simplified Chinese and Traditional Chinese. The category labels keep
their existing text sizes and full-half touch targets. Storage and scoring are
unchanged from 1.2.0.

The official extractor copied 21 files, all matching the final sources; the
clean upstream SDK built the optimized arm64 release. Release lint passed with
0 errors and the same 2 SDK manifest warnings. Installed UI checks on isolated
emulator user 10 confirmed each label's left edge, category switching and all
three locales. All displayed task names, counts, category sizes, gold and
all-time totals matched the pre-update baseline; no scoring was performed.
The public home screenshots have been refreshed from that build.

Local artifact: `local/routine-acceptance/tabs-1.2.1/Routine-1.2.1-local.apk`.
SHA-256: `b0c775c1c624988e3c1fcbe811c8a66b3e937ab27f10dd9e3c094b6d5138b9c6`.
It is non-debuggable and signed with the same SDK development certificate.
Private acceptance evidence stays under ignored
`local/routine-acceptance/tabs-1.2.1/`.

The same optimized APK was installed in place on the physical LP3, retaining
the signing identity. Its package reports 1.2.1 / code 6. All visible task
names, counts, category sizes, gold and all-time totals matched the pre-update
baseline. Label bounds and the selected underline confirm left alignment on
the actual phone. Category switching works; full JSON creation reports Saved
before and after the update. No scoring was performed and no new ANR exit
entry appeared during this check. The temporary USB wake setting was restored.

## Home categories and habit difficulty — 1.2.0 (2026-10-10)

Habits and pending To-dos now have separate tabs with item counts. New creates
an item in the selected category. Each list keeps its position across tab
switches and visits to other screens, and shows a thin right-hand position
indicator only when it overflows. Rows remain lazy and support two-line names.

Positive habits and Habit ± offer Easy +5, Medium +10, or Hard +20 in the
editor. Negative records remain up to −10; to-dos remain up to +10 exactly
once. Each habit tap still counts as one record. Difficulty changes are saved
with the task and apply only to future positive records; scoring reads the
current database difficulty even if its caller has an older task object.

Database schema 3 adds a MEDIUM default through the generated 2→3 migration;
the SDK's default builder supports both 1→3 and 2→3 without a destructive
migration. JSON format 3 preserves difficulty and imports formats 1/2 with
Medium defaults. CSV retains each event's original actual gold delta rather
than inferring its past difficulty from the current setting.

Verification: 48 instrumentation tests passed. New coverage includes all three
credits, unchanged past scores after editing, fixed negative/to-do scoring,
concurrent hard records, transactional rollback, upper-balance clamping,
cancelled drafts, older JSON imports, difficulty backup round trips, and the
2→3 migration retaining existing reward receipts and undo behavior. Debug
build and lint passed with 0 errors and the same 2 SDK warnings.

The actual installed QA database was compared before and after 2→3: every
old wallet, task, reward and event field matched, and every existing task
defaulted to Medium. UI checks on isolated user 10 covered short/long lists,
category separation, scroll position after editing and visiting Rewards,
To-do-context New, saved and cancelled difficulty changes, Easy/Hard credits
with one record per tap, and English/Simplified/Traditional Chinese. The
official extractor copied 21 files, all matching the final sources, and the
clean upstream SDK built the optimized arm64 release. That APK passed a
separate installed UI check; its locale override was cleared afterward.

Local artifact: `local/routine-acceptance/categories-1.2.0/Routine-1.2.0-local.apk`.
SHA-256: `2106aac30c449b5e97adea516b252e5b31b9a1cf3b3ea0a718256a51faca5e65`.
This non-debuggable APK uses the existing SDK development certificate; it is
not a Light-signed or approved build. Private QA evidence stays under ignored
`local/routine-acceptance/categories-1.2.0/`. File Manager compatibility and
the hosted Light build remain separate gates in [SUBMISSION.md](SUBMISSION.md).

The same optimized APK was installed in place on the physical LP3. Its package
reports 1.2.0 / code 5 with the same signing identity. Task names and displayed
counts across all captured old home pages match the new category lists; gold
and all-time totals remain unchanged. The phone opens To-do-context New and
the difficulty chooser. An existing positive habit defaults to Medium; a Hard
draft is cancelled and reopening confirms Medium. Local full JSON creation
reports Saved before and after the update. No habit was scored, task saved,
or reward redeemed; no new ANR exit entry appeared during the check. The
temporary USB wake setting was restored. Private baselines, old APK and receipt
remain in `local/routine-acceptance/real-device/categories-update-1.2.0/`.

## Direct redemption UI — 1.1.0 (2026-10-10)

- Keeps the Light SDK list layout: available gold, reward name/price, Redeem,
  History, and New reward. English and Simplified/Traditional Chinese use the
  system locale. Editing and repeated redemption remain available.
- A committed redemption opens a receipt with its original name/cost, balance,
  and recorded redemption count. Undo refunds the original cost once in the
  same transaction as its audit record. Receipts block repeated taps until
  dismissed. History marks undone entries and supports loading earlier rows.
- Database schema 2 adds nullable reward snapshots and reversal links; Room's
  generated 1→2 migration works through the SDK's default database builder.
  Schema history is included in allowlisted assets. No destructive migration,
  database renaming, or package ID change is used. Unidentified legacy purchases
  retain their amounts/dates and display an explicit missing-name label.
- JSON format 2 includes these fields and undo links; version 1 backups remain
  importable. CSV appends reward ID/name and event/reversal IDs to its existing
  columns. The v582 File Manager transfer limitation is unchanged.

Verification: 37 instrumentation tests passed, including concurrent refunds,
rollback on failed undo writes, snapshots after rename/delete, old JSON import,
CSV escaping, and migration via an unconfigured Room builder. Debug build and
lint passed (0 errors, the same 2 SDK manifest/provider warnings). Official source
extraction and the clean upstream SDK arm64 optimized release build passed.

On isolated emulator user 10 at 1080×1240, every pre-existing database row was
compared before/after the installed migration and matched. UI acceptance covered
creating rewards, insufficient gold, repeated redemptions, exact undo, history
after a cold restart, and English/Simplified/Traditional Chinese; the locale
override was cleared afterward. The original emulator databases and APK are
archived locally. The development-signed optimized APK was also installed on
that test user; redemption, exact undo, history, and the existing balance passed
a cold-start smoke check. Neutral UI captures are in [screenshots](screenshots/README.md).
On 2026-10-10, the user requested the update on their physical LP3. A full JSON
backup was saved on the phone before `adb install -r`; version 1.1.0 / code 4
and matching signing identity were checked. All visible homepage text/counts,
period, gold, and the existing visible reward name/price match their pre-update
baselines. The new Rewards and History screens open, and the back action works.
No actual reward was redeemed during this check. Private evidence and the old
APK are in `local/routine-acceptance/real-device/rewards-update-1.1.0/`.
Physical-phone receipt/undo acceptance and Light's hosted build remain
pending. Emulator logs, test receipts, and the local development-signed APK are in ignored
`local/routine-acceptance/rewards-1.1.0/`.

## LP3 installation and ANR investigation on 2026-10-09

The user installed the 1.0.0 debug APK on an LP3 running LightOS v582 through
the File Manager Tool Inbox. Upload and notification returned HTTP 200. The user
confirmed that Routine opened and that two habits could be created. They then
reported one "Not responding" dialog when changing a new habit's type to
negative; reopening Routine allowed the operation to succeed. No device ANR
trace is available, so the process/thread responsible is **not yet established**.

Investigation and mitigation:

- `EditViewModel.cycleType()` only updates in-memory state. It performs no I/O,
  database transaction, network operation, or blocking wait. A regression test
  holds a Room transaction open while changing the new habit's type and period;
  the controls still update, and the negative/monthly values subsequently save.
- Home statistics previously ran on the main dispatcher and scanned the entire
  event history separately for each habit. In 1.0.1 they run on
  `Dispatchers.Default` and aggregate the history in a single pass. Period
  boundaries and lifetime totals (including archived habits) are preserved.
  This prevents a growing-history performance risk; it is not evidence of the
  cause of the reported incident with only two new habits.
- Both the original debug APK and the optimized release APK passed a manual UI
  script on the LP3-sized Android emulator: enter a name with the SDK keyboard,
  switch types 13 times, save a negative habit, reopen and verify it, and delete
  the dedicated QA item. Neither run reproduced an ANR. The emulator does not
  have the real LightOS host, so this does not rule out device/SDK integration.
- All 20 regression tests passed. Release assembly and lint passed (0 errors,
  the existing missing-application-icon warning). The inherited AGP 8.12.3 /
  Kotlin 2.3.20 combination emitted R8 Kotlin metadata warnings. The local build
  and SDK Gradle plugin compile classpath now use AGP 8.13.2, whose R8 8.13.19
  [supports Kotlin 2.3](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
  Assembly, lint and all 20 tests passed again, without the R8 metadata warnings.
  This build compatibility fix is not a diagnosed cause of the debug APK's ANR.
- The final AGP 8.13.2 artifact was installed over the earlier emulator build,
  cold-launched, and passed another full type cycle plus negative save/reopen
  (5 switches). The dedicated QA item was deleted afterward. Existing habits
  and the wallet remained present.

The local 1.0.1 APK has versionCode 2, is not debuggable, and is signed with the
same development certificate as the installed 1.0.0 APK. Its emulator update
retained the existing habits and wallet. Database filename/schema are unchanged.
Artifact: `local/routine-acceptance/Routine-1.0.1-device-release.apk` (SHA-256
`fcb03ee0b833d1675c878b49ee11cc46477d5089df6f4e3f5c86ff59bab0dd48`).
It is a locally signed development release, not a Light-approved public build.

Light's submission builder supplies its own SDK/build-tool versions and does
not extract our root version catalog or Gradle plugin build file. Confirm that
service's toolchain compatibility separately before public submission.

The 1.0.1 release APK was subsequently uploaded to the user's LP3 through a new
File Manager session; upload and installation notification both returned HTTP
200. The user then reported that it was now smooth. This is positive real-device
feedback following the update, not evidence identifying the original ANR's
cause or establishing long-term stability. No real-device ANR trace has been
collected. The user did not separately report an installed-version check or a
full data-integrity check.

## Backup and submission preparation — 1.0.2, 2026-10-09

- Data replaces the old Export action. CSV reports and versioned JSON backups
  are written through `LightFileShare` into Routine → Downloads. The tool
  declares `tool-manager-provider` and supplies a manifest with Downloads and
  Restore Inbox; it does not automatically process uploaded files.
- JSON includes wallet, tasks (including completed/deleted), rewards, and all
  events. Input validation checks format/schema, unique IDs, enum values,
  references, numeric bounds, and size limits before a restore is offered.
- Restoration requires a phone-side preview and explicit confirmation. A
  safety copy is written before any database replacement; all four tables are
  replaced in one transaction. File work runs on `Dispatchers.IO`; the busy
  state prevents repeat actions and leaving the screen during the operation.
- CSV uses stable English headers, UTF-8 BOM, quoted user text, offset-aware
  timestamps and an explicit timezone column. Spreadsheet formula prefixes
  in user-entered names are neutralized. The database schema and filename are
  unchanged.
- Removed the template entry point's logging of SDK registration and push data.
  Audited the merged permissions and the Tool Manager 0.0.13 provider's caller
  check. [Privacy](PRIVACY.md) documents SDK permissions, unencrypted files,
  deleted-item retention, and the limits of this audit.

Verification:

- All 27 Android regression tests passed. The seven new backup tests cover
  full table round trips, upload/preview without mutation, failed safety-copy
  writes, database rollback after an insert error, invalid/oversized backups,
  CSV escaping, and empty installations.
- Optimized release assembly and lint passed: 0 errors, 2 SDK manifest
  warnings (missing application icon and exported provider). The provider
  enforces system UID 1000 at runtime: an actual shell caller was rejected,
  while the system caller could retrieve the manifest and transfer files.
- Official allowlist extraction copied 17 files. The unmodified upstream
  `311aa2d` SDK built the extracted tool as an arm64 unsigned release. That
  upstream AGP/R8 emitted its known Kotlin metadata warnings; the local
  AGP 8.13.2 device artifact built without those warnings. Neither check is
  a substitute for the hosted Light build/signing service.
- An isolated emulator user (`Routine-QA`, ID 10) exercised invalid backup
  rejection, preview cancellation, confirmed restore, and recovery-file
  creation. Release-generated JSON was downloaded through the provider,
  uploaded again, and restored; task/reward/event counts and wallet matched.
  Downloaded CSV rows and the pre-restore safety copy were independently read.
- English, Simplified Chinese, and Traditional Chinese Data pages were checked
  at 1080 × 1240. Public screenshots use only neutral QA data. An incomplete
  emulator capture was replaced with a visually reviewed complete recapture;
  real-device rendering remains part of the pending phone acceptance.
- The emulator returned to its original owner profile. The existing habits
  and gold balance 60 remained visible after the in-place update. All restore
  testing was restricted to the isolated QA user, not the owner's data.

Local installable artifact: `local/routine-acceptance/Routine-1.0.2-device-release.apk`.
SHA-256: `619c53a65fe8d90ee39380e635fb6d5e99a1971ab829b0b605b85e8649f087df`.
It is non-debuggable and uses the same SDK development certificate as 1.0.1.
This is not a Light-signed or approved release.

The phone installation and browser/LightOS network checks described below are
now complete. The remaining file-transfer gate requires a real LightOS build
that supports third-party file directories; the tested 582-release-lp3 does
not. Downloading and previewing a phone backup remain unverified.
Source publication and hosted submission remain pending. See
[SUBMISSION.md](SUBMISSION.md) for prepared listing text and exact next steps.

### Subsequent LP3 v582 acceptance

The 1.0.2 APK upload and Tool Inbox notification both returned HTTP 200. The
user then supplied a photo of the real phone showing the new Data & backup
screen and `Saved` with a generated `routine-backup-…json` filename. This
confirms the updated UI is running and the local JSON write reported success;
it does not verify the backup contents, a CSV export, or off-device storage.

After the user reopened File Manager and supplied a fresh session QR,
`GET /api/root` still returned only Photos and Tool Inbox. The phone's client
asset uses the legacy `com.thelightphone.filemanager` API and `/api/root`,
whereas the current SDK Tool Manager exposes a different tree API. A request
to `/api/meta/Routine` returned HTTP 500 with a missing `ErrorResponse`
serializer, concealing the underlying lookup error. Routine was not listed
and no real-phone backup was downloaded or restored.

The subsequent authorized USB investigation confirmed the exact installed
versions: Routine 1.0.2 / versionCode 3 and LightOS 582-release-lp3 /
versionCode 3146310. The installed provider is present, and LightOS uses
system UID 1000, matching the provider's access check. A local audit of the
actual LightOS APK confirms that its File Manager root builder returns only
Photos and Tool Inbox and has no third-party-tool discovery. This settles the
missing-directory cause for this exact host build; changing Routine's provider
authority cannot add a root to that fixed map.

The user also confirmed Routine records remained present after opening Service
Menu and rebooting. The actual system-mode code can reboot into Android
Settings, and returning to LightOS can reboot back to light mode. A read-only
check after recovery confirmed light mode 1. The reason Wi-Fi had become
disabled is not established by that mode-change code.

File Manager's stopped-running screen was investigated separately. The phone
had Wi-Fi disabled and a CELLULAR default network; the actual server requires
WIFI or ETHERNET as its default network. Wi-Fi was enabled, the phone rejoined
its saved network, and File Manager was reopened through Settings → Debug.
Its runtime log reported the server running and authenticated `/api/root`
returned HTTP 200, still with the two expected roots.

Real-phone Routine editor acceptance also passed: 16 type transitions covered
Habit +, Habit −, Habit ± and To-do; 6 period transitions covered week, month
and year. Every expected label was checked. The draft was cancelled, and the
visible homepage, displayed counts and gold were identical to the baseline.
Routine was located by scrolling the normal LightOS tools menu and launched
from that menu successfully. No personal task was saved, scored or deleted.

Package-specific Android exit history confirms the earlier 5000 ms
input-dispatch ANR. Its saved stack is protected from the normal ADB shell, so
the precise old blocking call has not been identified. This editor check did
not reproduce the ANR; it does not guarantee that every path is free of ANRs.

The phone has not been backed up to a computer. A newer compatible firmware
version has not been verified. Phone photos, connection credentials, raw
device logs, closed-source APK/decompiled output, and any eventual personal
backups stay out of Git.

Further source-level diagnosis and a forced legacy provider refresh are recorded
in [FILE_MANAGER_DIAGNOSIS.md](FILE_MANAGER_DIAGNOSIS.md). This investigation
also corrected our earlier Tool Inbox query: pagination starts at 1, not 0.
The valid query succeeds; the missing Routine root persists after refresh.


## First-use acceptance — 1.3.0

The official extractor copied 23 permitted source files into the clean SDK,
and all copies match the candidate. The optimized arm64 release build and
release lint passed with zero errors; two existing SDK/manifest warnings
remain (`ExportedContentProvider`, `MissingApplicationIcon`).

All 52 instrumentation tests passed, including four new real-DataStore tests
covering interrupted first-run restart, persistent dismissal, upgrade with
existing records/gold, and upgrade of an existing empty installation.

The final optimized build was checked in an isolated fresh emulator user.
English, Simplified Chinese and Traditional Chinese guide content and both
actions fit at LP3 density 480. The default 420 dpi preview was also checked.
An interrupted guide resumed; Skip stayed dismissed after relaunch; Help
reopened and scrolled to its data section. Add a habit opened an unsaved
editor, and cancelling created no task. Temporary users were removed and
display density and USB wake settings restored.

The user's physical LP3 was updated from 1.2.1 to **1.3.0 / code 7** with the
same development signing identity. Names, displayed counts, category sizes,
gold and all-time totals match across all collected home pages. The guide
did not interrupt the existing installation; Help and Data opened. Local
JSON creation reported Saved before and after. No personal record was scored
or task saved, and no new ANR exit entry appeared during this check. This
does not establish long-term stability or resolve the LightOS 582 off-device
backup limitation. Database and JSON backup format remain version 3.

Final local APK SHA-256:
`ef78d2007fcb4e7371824b42cf1e13b4f7564a940c9c1a0c69041018580435e3`.
The APK and private receipts remain under ignored `local/`; only neutral
screenshot samples are suitable for the public listing.

## Example guide and author acceptance — 1.3.1

The official allowlist extractor copied 24 final tool files; their bytes match
the candidate sources. The clean SDK's optimized arm64 build and release lint
passed with zero errors and the same two existing manifest warnings. All 52
existing instrumentation tests passed against the final production sources.

An isolated fresh emulator user verified all three guide examples, empty lists
after skipping, persistent guide dismissal, the four editor type explanations,
Help → About navigation and the preserved Help reading position. English,
Simplified Chinese and Traditional Chinese guide content is fully visible at
480 dpi. About shows the exact public handle, email and source address. At
font scale 1.3, scrolling keeps About text and editor Save reachable. No task
was saved and no scores were recorded. The temporary user was removed, and
the original user, density and wake settings were restored.

The physical LP3 received a same-certificate update from 1.3.0 to **1.3.1 /
code 8**. Visible names, category sizes, counts, gold and all-time totals match
before and after, including after cancelling a draft with all four types.
About contacts were checked on the phone; local JSON creation reported Saved
before and after. No new ANR exit entry appeared during this short check.
Database and backup format remain version 3. This update does not resolve the
tested LightOS 582 off-device file-transfer limitation.

Final local APK SHA-256:
`45a82e6746dcef88e2851c91a336b194538d01abf4b75cd12c560acbd521be1e`.
APKs and private receipts stay under ignored `local/`. Public guide and About
screenshots contain only neutral examples and author-approved contact details.

## Physical-phone reward acceptance and public source — 2026-10-10

Routine 1.3.1 / code 8 was checked on the physical LP3 in a temporary isolated
Android test user. A test habit supplied 10 gold; a test reward cost 6.
Redemption showed Spent 6 / Left 4; Undo restored exactly 10. A repeated
redemption correctly restarted the non-undone count at 1, and reward history
retained both the undone and completed entries after force-stop / cold start.

The temporary test user was removed. The original user's collected visible task
names, per-item counts, category sizes, gold and lifetime totals matched the
pre-test baseline, and no new ANR exit-history entry was observed during this
short check. The phone was returned to its original user and normal home.
No original-user reward was redeemed. Private receipts remain in ignored local/.

The public GitHub fork now uses routine as its default branch. Its source starts
from official SDK commit 311aa2d and includes the reviewed Routine files rather
than the private development history. All 24 production files match the
previously verified clean-SDK build. The upstream AGP toolchain is preserved in
that public branch; local development overrides remain in the working checkout.
