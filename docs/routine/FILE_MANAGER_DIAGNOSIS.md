# LP3 v582 File Manager discovery investigation

Status: Routine 1.0.2 runs on the phone and reports a JSON backup saved. The
phone's File Manager does not expose Routine, so no off-device backup has been
verified. No user database was restored, cleared, or uninstalled during diagnosis.

## Findings verified on 2026-10-09

| Check | Result |
| --- | --- |
| New phone File Manager session, authenticated `/api/root` | HTTP 200; Photos and Tool Inbox only |
| Known root `/api/meta/Tool%20Inbox` | HTTP 200; `readOnly: false` |
| Legacy file listing with valid pagination | HTTP 200 |
| `/api/files/Tool%20Inbox?refresh=true`, then root | HTTP 200; still the same two roots |
| Routine metadata/listing with valid parameters | HTTP 500; server cannot serialize its `ErrorResponse` |
| Actual installed-candidate APK manifest | SDK 0.2.0, host `com.lightos`, provider capability and discovery marker present |
| Actual provider authority in that APK | `io.github.yang44yang.habits.lightfileprovider` |
| Same release on isolated emulator user | System-UID caller can fetch manifest, list/read/write files; normal shell is rejected |
| Physical USB connection to Mac | Light Phone III enumerates, with one PTP interface (class/subclass/protocol 6/1/1); no ADB interface |
| ADB after USB connection | Only the emulator is listed; real-phone package data and logs remain unavailable |
| User's later Debug menu photos | Android Dev Mode is visible; File Manager displays "File manager has stopped running" |
| User confirmation after Service Menu / reboot | Menu items disappeared, but existing Routine records remain present |
| Authorized real-phone ADB connection | Routine 1.0.2 / versionCode 3; LightOS 582-release-lp3 / versionCode 3146310; Android 14 |
| Actual LightOS process identity | System shared UID 1000, matching Routine's provider caller restriction |
| Real v582 APK root builder | Fixed Photos and Tool Inbox map; no external-tool discovery in that builder |
| Network after user's mode change/reboot | Wi-Fi disabled; active default network CELLULAR |
| Network after enabling Wi-Fi over ADB | Wi-Fi enabled; active default network WIFI |
| Reopened File Manager on Wi-Fi | Runtime log says server running; authenticated root returns HTTP 200 with Photos and Tool Inbox |
| Current LightOS mode | `light_mode = 1`; Routine is visible after scrolling the tools menu and can be launched from it |
| Routine real-phone editor check | 16 type transitions and 6 period transitions passed; cancelled draft and visible homepage data remained identical |
| Missing native tools, Dashboard comparison | Dashboard still lists Music, Podcasts, Calendar, Notes, Timer and Weather; the phone menu does not |
| Phone Account & Info during menu investigation | Displays `No Light Account, Create/Login` |
| Debug environment settings | LightWeb Dev Env, Beta OS and Beta Firmware toggles are off |
| LightOS interface restart on Wi-Fi | Completed without clearing storage; missing tools did not return |
| After user completed the existing-account login | Music, Podcasts and Routine are visible again; Routine opens with identical visible homepage text and gold compared with the earlier baseline |
| 2026-10-10 stability follow-up | 3 background returns and a Routine cold start preserve visible homepage data; a named draft passes 16 type and 6 period transitions and is cancelled without saving; no new ANR exit-history entry |

The Root endpoint and known-root metadata succeed, so this is not simply a bad
pairing token or inability to reach the phone. The forced refresh comes from
the legacy server's own documented query handling; repeating export on the
phone does not add a missing server root.

### Correction to earlier diagnostics

Our original local `status` helper requested `page=0`. The legacy API is
**one-based** (`page >= 1`); its default is page 1. That bad parameter triggered
an error response, which the phone then failed to serialize. The helper now
uses page 1 and the known-root listing works. Earlier Tool Inbox HTTP 500
results must not be used as evidence that all file listing is broken.

The separate Routine metadata/listing failure persists with valid parameters.
`ErrorResponse` belongs to the File Manager server, not Routine's JSON backup
model. The HTTP 500 does not demonstrate a Routine backup serialization defect.

## Source comparison

The client served by this phone identifies its models under
`com.thelightphone.filemanager` and uses `/api/root`, bearer authentication,
and `DirectoryMeta(readOnly)`. These match the legacy implementation in
[Tool Manager commit e58c23565a](https://github.com/lightphone/light-tool-manager/tree/e58c23565a).

In that source, `RootDataProvider` receives a host-supplied map of directory
providers. `getRoot()` returns that map's keys; an absent key fails with
`invalid root`. The `refresh=true` file-list option rebuilds the map. The
legacy `ContentResolverDataProvider` can route file operations when the host
supplies it, but that does not establish that this firmware discovers tools
automatically or uses the current marker.

Automatic external-tool discovery was added in
[commit ed71e4cee3](https://github.com/lightphone/light-tool-manager/commit/ed71e4cee3a26fcd8046b8195ad7d0eb9f54c1e4).
It scans providers with the `com.thelightphone.toolmanager.TOOL_MANAGER_PROVIDER`
metadata marker, calls `get_manifest`, and builds the tool's directory tree.
The SDK integration was merged in
[SDK PR 184](https://github.com/lightphone/light-sdk/pull/184).
Routine's built APK contains that marker and responds to the handshake in
the emulator.

There is also a concrete legacy address difference: the older SDK declared
`com.thelightphone.sdk.LightFileProvider` at `${applicationId}.lightfiles`;
the current SDK declares `com.thelightphone.toolmanager.LightFileProvider`
at `${applicationId}.lightfileprovider`. The old declaration is visible in
[SDK commit 9d9eff5](https://github.com/lightphone/light-sdk/blob/9d9eff5/sdk/client/src/main/AndroidManifest.xml).
We do not yet know which discovery rule v582's closed LightOS host uses.

## Real-phone code audit and conclusion

After the user enabled USB debugging, the actual installed LightOS APK was
read from the phone without modifying it. Its identity is `com.lightos`,
`582-release-lp3`, versionCode `3146310`. A local bytecode audit of
`LightOSFileManagerProviderKt.rootFileManagerDataProvider` confirms that its
provider callback constructs only a fixed Photos and Tool Inbox map. It does
not discover third-party file providers. This is the decisive explanation for
Routine's missing directory in this exact build. Refreshing or changing
Routine's authority cannot add an entry to that map.

The actual `FileManagerServiceAndroid.start()` also requires its active default
network to be Wi-Fi or Ethernet. A failed start causes the lifecycle wrapper
to stop itself; losing that local network stops an already-running server.
ADB reported Wi-Fi disabled with CELLULAR as the default network after the
user's mode change/reboot. After enabling Wi-Fi, the default network became
WIFI. Reopening File Manager through its visible Debug menu entry restored
the server: its runtime log reported it running, and the authenticated root
request returned HTTP 200. These checks support the network explanation for
the stopped-running screen. A USB cable and mobile data do not meet this
build's File Manager network predicate.

The installed Routine provider is present at the expected current authority.
LightOS uses system shared UID 1000, so the app's UID restriction matches the
host. No evidence supports modifying that restriction or adding a speculative
legacy authority. Keep closed-source APK/decompiled output and raw device logs
in the ignored private diagnostics directory, not the submission repository.

The user confirms that menu items changed after Service Menu/reboot but
Routine's records remained present. The actual Service Menu code calls
`prepareAndroidForDeveloper`, which switches `light_mode` to 0; a mode change
can reboot into Android Settings. Returning to LightOS restores light mode 1
and can reboot again. The Android Dev Mode toggle separately controls both
Android developer options and USB debugging. These system-mode changes explain
the Android settings/lock-screen transition; they do not establish data loss.
The audited mode-change code does not directly disable Wi-Fi, so the reason
Wi-Fi was off is not proven.

Routine was subsequently exercised on the real phone: an empty new-task draft
cycled 16 times across Habit +, Habit −, Habit ± and To-do, and its period
cycled 6 times across week/month/year. Each resulting label was verified before
the next action. Cancelling the draft returned an identical visible homepage,
including its displayed counts and gold. No task was saved, scored or deleted.
This is a responsive-editor check, not proof that all possible ANRs are absent.

### Separate missing-toolbox investigation

The user's Dashboard retains the configured Music, Podcasts, Calendar, Notes,
Timer and Weather tools, while the phone shows a smaller menu. The phone's
Account & Info page explicitly displays `No Light Account, Create/Login`.
This account UI state is a concrete recovery lead. It does not establish that
the Dashboard device was unlinked, that media was deleted, or that Service Menu
itself changed the account flag. LightWeb Dev Env is off, so switching that
environment is not supported by the observed settings. Restarting only the
LightOS interface on Wi-Fi did not restore the missing tools.

Native GetDevice jobs can succeed and emit `deviceDidUpdate` even while the
account UI reports no Light Account. Their success alone must not be treated
as verification that the frontend account state or Toolbox menu is correct.
The user completed the existing-account consent/login. A subsequent real-phone
check confirms Music and Podcasts are visible in the menu again, and Routine
remains visible and opens normally. Its visible homepage text, including the
gold counter, matches the earlier baseline. This supports account reconnection
as the recovery for the missing menu, without proving what originally changed
the account state. No media contents were inspected. Do not remove/re-add Music
or Podcasts, clear application
storage, create a replacement account, or factory-reset the phone as a
speculative fix. No such actions were taken during this investigation.

Android's package-specific exit history confirms an earlier Routine ANR with
an input-dispatch timeout waiting 5000 ms for a touch event, followed later by
a package-update exit. The associated historical stack file cannot be read by
the normal shell. The exact old blocking call therefore remains unidentified;
do not retroactively claim that the statistics fix proved that ANR's cause.

On 2026-10-10, a fresh physical-phone check captured a current homepage baseline,
returned from the background three times, and force-stopped/restarted only
Routine. Each return showed identical homepage text, including the gold counter.
A new draft was given a test name through the SDK keyboard, then its type was
cycled 16 times and its period 6 times. The draft was cancelled without saving;
the resulting homepage again matched the fresh baseline. Package-specific exit
history before and after the check contained no new ANR entry. The check took
78.1 seconds, performed no scoring, purchases, deletions or restores, and did
not modify the user's visible data. This covers the reported editor sequence
and app lifecycle checks; it is not evidence of long-term or universal stability.

In [SDK issue 61](https://github.com/lightphone/light-sdk/issues/61), another
developer reported the same PTP-only USB enumeration. The maintainer referred
them to the [July 2026 announcement](https://github.com/orgs/lightphone/discussions/70),
which stated that there was then no officially supported method for enabling
Android developer access and that undocumented methods could change. This is
historical guidance. The user's photos and actual device connection now
establish a working Android Dev Mode / USB debugging path on this phone.
Do not require resetting/unlocking it or undocumented boot-key sequences.

Do not add a speculative legacy provider to the submitted app: the official
builder generates the manifest and discards local SDK/plugin modifications.
A workaround that only works in our checkout would not survive Light's build.

## Upstream question draft (not sent)

> On LP3 LightOS 582, Settings → Debug → File Manager serves the legacy
> `/api/root` API and lists only Photos and Tool Inbox, even after a provider
> refresh. A tool built with Light SDK 0.2.0 declares `tool-manager-provider`,
> has the generated discovery marker, and exposes a working `get_manifest`
> and shared-file provider under `<applicationId>.lightfileprovider` in the
> Android emulator. On the phone, its UI reports local backup creation, but
> File Manager never exposes the tool. Does 582 support discovery of these
> providers, expect the old `.lightfiles` authority, or require a newer
> LightOS build? Which firmware version should be used for acceptance?

Private phone photos, pairing tokens, file names from unrelated tools, and
actual user backups are excluded from this report and from the repository.
