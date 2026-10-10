# Routine

Small actions, recorded simply. Routine is a local habit and to-do tracker for
Light Phone III. Choose a manageable action for your goal, add it as a habit,
and record it when it happens.

## First use

New installations show one short introduction. **Add a habit** opens the
editor without creating a task; **Skip** opens the empty home. Once you
choose either action, the introduction does not repeat. Closing the app
before choosing leaves it available next time. Existing installations
continue directly to their records when updating.

Tap **Help** at the top right of the home to read the introduction again
and see more details about records, gold, rewards and data. English and
Chinese follow the system language.

The introduction uses concrete examples. These are explanations only;
Routine does not create example habits or records for you.

| Example | Type | `[+]` records | `[−]` records |
| --- | --- | --- | --- |
| Strength training | Habit + | A completed session | No negative button |
| No alcohol | Habit ± | A day without alcohol | Drinking alcohol |
| No food after 8pm | Habit ± | Keeping the rule | Eating after 8pm |

For goals judged by the day, record once at the end of that day. Routine does
not automatically mark successful days or enforce one entry per day. A minus
records the action you want to reduce happening; it does not mean achieving
the "No…" goal. For negative-only tracking, choose **Habit −** and name the
action, for example **Drinking alcohol** or **Eating after 8pm**. The editor
explains each type directly below the type selector.

## About and feedback

Open **Help → About** for the app version, author / X account
**[@BitVendetta](https://x.com/BitVendetta)**, feedback email
**yang.yang.pro@gmail.com**, and the public source repository. The phone
displays these as plain text; it does not launch X, send email, or request
network access. Use the contact details from another device if you wish.

## Daily use

- **Habits / To-dos** switch between separate lists. Each tab shows its item
  count in square brackets and keeps its scroll position. A thin right-hand
  indicator appears when the selected list is longer than the screen. **New**
  creates an item in the selected category.
- **Habit +** records an action you want to build. Each tap adds up to 5, 10,
  or 20 gold, according to its difficulty.
- **Habit −** records an action you want to reduce. Each tap deducts up to 10
  gold; the balance never goes below zero.
- **Habit ±** offers both buttons for a habit where either makes sense.
- **To-do** is a one-time task. Completing it adds up to 10 gold once.
- Tap an item's name to edit it. Choose this week, month, or year for its
  displayed counts. The all-time totals remain separate.
- **Rewards** lets you define a personal reward and its gold cost. Gold has
  no monetary value; rewards are something you arrange for yourself.

There are no reminders, streak penalties, feeds, or account sign-ups. The
interface follows the system language: English, Simplified Chinese, or
Traditional Chinese. Task names stay exactly as you entered them.

## Habit difficulty — 1.2.0

In a **Habit +** or **Habit ±** editor, tap **Difficulty** and choose **Easy +5**,
**Medium +10**, or **Hard +20**, then save the task. New and existing habits
default to Medium. Choose a level that reflects your own effort; difficulty
does not change automatically.

Each positive tap still counts as one record on the home screen. Changing
difficulty only affects future positive records; the existing gold balance,
historical deltas, and counts are not recalculated. Cancelling the editor
leaves the saved difficulty unchanged. Negative records still deduct up to
10, and completing a to-do still adds up to 10 once. Credits are limited by
the maximum gold balance of 2,147,483,647.

## Rewards — 1.1.0

Open **Rewards** to see your available gold, reward names, and prices. Tap a
name to edit it, or **New reward** to add one. **Redeem** is disabled when the
balance is insufficient. Rewards remain available for repeated redemption.

After redeeming, a result page shows the reward, gold spent, remaining balance,
and recorded redemption count. **Done** returns to the list; **Undo** returns
the original cost exactly once. Undo is available on this result page until
you leave it. It does not change your habit counts. An undo remains visible in
history and is excluded from the reward's redemption count.

**History** lists redemptions with their dates and original prices. New entries
keep the reward name as it was when redeemed, even after renaming or deleting
the reward. Older versions did not save reward identity, so their entries are
shown as **Past reward**, with the amount and date preserved. Recorded counts
include only identifiable redemptions and exclude undone purchases.

CSV exports include reward names/IDs, event IDs, undo links, and the actual
gold change for each record. Full JSON backups in Routine 1.2.0 use format
version 3 and include habit difficulty; formats 1 and 2 remain importable,
with old habits defaulting to Medium. Older app versions cannot read the new
backup format or database. Historical difficulty is not inferred from a
habit's current setting.
The File Manager transfer limitation below still applies.

## Export and backup — 1.0.2

Routine 1.0.2 can create files locally on the phone. On the tested Light Phone
III running **LightOS 582-release-lp3**, File Manager exposes only Photos and
Tool Inbox. It does not support third-party tool directories, so **Routine's
CSV/JSON files cannot be downloaded, and backups cannot be uploaded through
File Manager on that build**. A **Saved** message confirms local file creation,
not a separate backup on your computer.

The transfer and restore instructions below require a LightOS build with
third-party Tool Manager provider support. A compatible real-phone version has
not yet been verified. Download, upload, and restore have been tested on an
isolated SDK emulator; off-device backup acceptance on the real phone remains
blocked by the tested firmware's File Manager limitation.

1. In Routine, open **Data**.
2. Choose **Export records CSV** for a spreadsheet of events, or **Create full
   backup** for a restorable JSON copy of tasks, rewards, gold, and history.
3. On a compatible LightOS build, open the phone's File Manager / Tool Manager
   on your computer using its pairing link. Select **Routine → Downloads**
   and download the file. These directories are unavailable on the tested
   582-release-lp3 build.

Creating the file only saves it on the phone. Download it to keep a separate
copy. CSV is an event report, not a restorable backup; full JSON backups also
include completed and deleted items retained in history. Files are unencrypted
and may contain private habit names and timestamps.

## Restore a full backup

1. On a compatible LightOS build, upload the JSON file in File Manager to
   **Routine → Restore Inbox**. This upload path is unavailable on the tested
   582-release-lp3 build.
2. On the phone, open **Data → Refresh files**, then select the uploaded file.
3. Review its creation date, task/reward counts, records, and gold balance.
4. Choose **Restore this backup** only if you want it to replace all current
   data. **Cancel** leaves your data unchanged.

Upload and preview do not restore anything automatically. Before replacement,
Routine writes a `routine-before-restore-…json` copy to Downloads. If that copy
cannot be written, restoration stops. Database replacement is one transaction:
if it fails, the current database is preserved. To undo a successful restore,
download that safety copy, upload it to Restore Inbox, and restore it normally.

Backups must use Routine's supported format and fit its limits: 16,777,216
characters, 10,000 tasks, 10,000 rewards, and 250,000 events. Files that are
damaged or use a different format/version are rejected. File names differ on
each export, so older copies are retained until you delete them in File Manager.

## Data retention

Deleting an item hides it; its history remains in the database and full backups.
There is no individual-history erasure command in this version. Uninstalling
Routine removes its app data; downloaded copies must be deleted separately.
Keep a verified backup outside the phone before any uninstall, device reset,
or signing-key transition. The tested 582-release-lp3 build cannot provide that
copy through Routine's File Manager integration; a local **Saved** message is
insufficient. A local development APK and a future Light-signed APK may not be
interchangeable as an in-place update.

See [Privacy](PRIVACY.md) for storage, file-sharing, and SDK details.
