# Routine privacy information

Applies to Routine 1.3.1 (version code 8). Last reviewed: 2026-10-10.

## What Routine stores

Routine stores task and habit names, settings including difficulty and
completion state, reward names and costs, a gold balance, and timestamped
activity records in an app-private database on your phone. Deleted tasks and rewards are hidden
from the active lists but retained with their history. Gold is a local counter,
not a currency or financial account.

A first-use introduction preference is stored privately through the SDK
DataStore. It remembers whether the guide was dismissed; it is not tracking
and is not included in Routine CSV reports or JSON record backups.

New reward redemptions retain the reward's name and cost at the time of
redemption. Undo records link to the original redemption and record its refund;
both entries are retained in history and included in exports and backups.

Routine's application code has no developer backend, advertising, analytics,
tracking, account system, or automatic cloud sync. It does not upload your
habit records to the developer. Template registration/push logging was removed.

Help → About displays author-approved public contact details as static text.
Opening it does not contact X, send email, or transmit records. If you email
feedback from another device, you choose what information to send.

## Files you choose to share

When you create a CSV or JSON backup, Routine places it in a shared Downloads
folder that LightOS can expose through its File Manager / Tool Manager.
Uploading a JSON file places it in Restore Inbox. Upload does not authorize
restoration; you must review and confirm it on the phone.

These files are **not encrypted by Routine**. They can contain private names,
timestamps, and deleted/completed items. Anyone you give access to your phone's
File Manager session may be able to download them. A downloaded file is also
subject to your computer's storage and backup settings. Delete unwanted copies
from the phone's File Manager and from any computers where you saved them.

Restoration creates a separate backup of the previous database before replacing
it. This safety copy remains in Downloads until you delete it. Removing an item
in Routine does not remove it from existing exports or backups.

## LightOS and SDK services

Routine uses the official Light SDK and its system integration. The final APK
inherits permissions and components from SDK dependencies, including Internet,
network state, camera, vibration, wake lock, boot reception, and foreground
services. Routine declares no additional permissions and its own code does not
request camera access or use camera, microphone, contacts, or location data.
An empty `permissions` list in tool metadata does not mean an APK has no
permissions.

The SDK's shared-file provider is exported for system integration. The bundled
Tool Manager client 0.0.13 checks the caller's UID and limits content operations
to Android's system UID 1000. Emulator checks confirmed rejection of an ordinary
shell caller and access by the system caller. Routine shares its export/import
folders, not its live database file.

This review covers Routine's source, its release manifest, and the provider's
access checks. It is not a network-traffic audit of every SDK dependency.
LightOS's own networking, diagnostics, device security, and backup behavior are
governed by the platform. Routine's generated manifest does not explicitly
disable Android system backup; no claim is made that platform-managed backups
are disabled.

## Deletion and questions

Uninstalling Routine removes its app-private data. Downloaded or platform-backed
copies must be managed separately. For a bug report, email
**yang.yang.pro@gmail.com** or use the
[source repository](https://github.com/yang44yang/light-sdk); do not attach a
personal backup, File Manager pairing token, or screenshots of private records
to a public issue.
