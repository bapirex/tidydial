# Privacy Policy

_Last updated: October 30, 2026_

TidyDial is an open-source app that formats the phone numbers in your Android contacts. It was
designed so that your data never leaves your device.

## Summary

- TidyDial **does not collect, share, sell or transmit** any personal data.
- TidyDial **cannot connect to the internet**. The app has no internet permission.
- TidyDial has **no ads, trackers, analytics or crash reporting**.

## What TidyDial accesses and why

| Access | Why |
| :--- | :--- |
| **Contacts (read)** | To list the phone numbers in your contacts, with each contact's name and the account that stores the number, and to show how each number would look in the format you choose. When you tap a number's call button, TidyDial also reads the call and chat entries other apps (such as WhatsApp or Signal) added to that contact, to offer calling through them. |
| **Contacts (write)** | To save the new format for the numbers you select and confirm, and to restore the old numbers when you tap Undo. Only the phone number field is changed. |
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

This data is stored in the app's private storage and is never sent anywhere. The undo journal
contains phone numbers, so it is excluded from cloud backups (and from all backups on Android 11
and lower). Uninstalling the app or clearing its storage deletes everything.

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
