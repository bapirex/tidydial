# Privacy Policy

_Last updated: October 31, 2026_

TidyDial is an open-source app that formats the phone numbers in your Android contacts. It was
designed so that it never sends your data anywhere: it has no internet access, and the only copies
of your data it saves outside the app are the optional backup files you choose to keep, in a
folder you pick.

## Summary

- TidyDial **does not collect, share, sell or transmit** any personal data.
- TidyDial **cannot connect to the internet**. The app has no internet permission.
- TidyDial has **no ads, trackers, analytics or crash reporting**.

## What TidyDial accesses and why

| Access | Why |
| :--- | :--- |
| **Contacts (read)** | To list the phone numbers in your contacts, with each contact's name and the account that stores the number, and to show how each number would look in the format you choose. When you tap a number's call button, TidyDial also reads the call and chat entries other apps (such as WhatsApp or Signal) added to that contact, to offer calling through them. |
| **Contacts (write)** | To save the new format for the numbers you select and confirm, to restore the old numbers when you tap Undo, and to restore numbers from a backup file you pick. Only the phone number field is changed. |
| **Make phone calls** | Only requested when you choose to call through another app such as WhatsApp, because those apps only accept calls from apps that hold this permission. TidyDial never places a call itself: regular calls open your phone app with the number filled in. If you decline, TidyDial opens your chat with the contact in that app instead, when it has one. |
| **Apps that can make calls (package visibility)** | To find whether your phone app and the apps that added a call to a contact can start that call or open your chat, and to show the name and icon of each app you can call with. TidyDial only sees apps that can open a phone number or a contact entry, not every installed app. This is checked on your device when you tap call, and the list of apps is never stored or sent anywhere. |
| **SIM and network country** | To suggest your country, so that numbers without a country code are read correctly. Only the two-letter country code is read. No phone number, SIM serial or other identifier is read. |

TidyDial asks for contacts access only after showing you what it will do, and for the call
permission only when you choose to call through an app that needs it. You can revoke either at
any time in Android Settings.

## What TidyDial stores on your device

- **Your settings**: country, number format, extension delimiter, short-number protection and
  the accounts to skip.
- **The undo journal**: after an update, the old and new form of each changed number (with
  Android's internal row ID) is saved so the update can be undone, even after a restart. It is
  replaced by the next update and cleared once the update has been undone.
- **Your backup choice**: whether backups are on, a link to the backup folder you picked and its
  name, and which folder the latest snapshot was saved in. This is kept out of cloud backups and
  device-to-device transfer, because access to the folder does not carry over to a new install.

This data is stored in the app's private storage and is never sent anywhere. The undo journal
contains phone numbers, so it is excluded from cloud backups (and from all backups on Android 11
and lower). Uninstalling the app or clearing its storage deletes everything except backup files
you saved in a folder of your choice.

## Optional backup files

Backups are off until you turn them on. On the first update TidyDial asks whether to keep a
backup, and you can change this at any time in Format options.

- **What they contain**: contact names, each phone number before and after the update, the
  account type and account name (for a Google account, this is usually its email address),
  Android's internal row and contact IDs, the contact's lookup key and the ID its sync account
  (for example Google) gives it, the country setting and the TidyDial version. The first backup
  in a folder comes with a snapshot of every phone number in every account, including accounts
  that are skipped, as it was when the snapshot was taken. If you updated numbers before turning
  backups on, the snapshot already contains those changes.
- **Where they are stored**: only in the folder you pick in Android's file picker, such as
  Documents or a folder inside Downloads, or a folder of a cloud storage app that offers its
  folders there. TidyDial can only access that folder, and needs no permission for it. The files are never part of TidyDial's Android backup, and TidyDial never
  sends them anywhere. If you pick a folder of a cloud storage app, that app syncs the files like
  any other file, under its own privacy policy.
- **Uninstalling**: backup files are not deleted when you uninstall TidyDial. Delete them
  yourself when you no longer need them.
- **Restoring**: TidyDial only reads the backup file you pick, and only changes numbers that
  still look the way the backup expects. With backups on, it first saves the numbers a restore
  replaces in a new backup file.

## Third parties

TidyDial does not include third-party services. Its open-source libraries (AndroidX and Jetpack
Compose, Material 3, Kotlin, libphonenumber and the others listed in
[NOTICE](NOTICE)) run entirely on your device. Phone numbers are
parsed and formatted locally with Google's libphonenumber library, which needs no network access.

Opening the source code link from the About screen hands the link to your web browser. TidyDial
itself does not make the request.

Tapping a number's call button hands the number to your phone app, which opens with it filled in,
or, if you pick one, to the app you chose to call with. TidyDial does not place the call itself.
Other apps are only opened when you tap call.

## Children

TidyDial does not knowingly collect information from anyone, including children, because it
collects no information at all.

## Changes

Any change to this policy will be published in this file in the
[TidyDial repository](https://github.com/bapirex/tidydial/blob/main/PRIVACY.md).

## Contact

For privacy questions, email umbrauniversum@gmail.com. For anything else, please open an issue at
<https://github.com/bapirex/tidydial/issues>.
