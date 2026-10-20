# Security Policy

## Supported versions

Only the latest release of TidyDial receives security fixes. Please check that you can reproduce
the problem with the latest release (or with `main`) before you report it.

## Reporting a vulnerability

Please **do not** report security problems in public issues, pull requests or discussions.

Report them privately through GitHub's private vulnerability reporting instead: open the
repository's **Security** tab and choose **Report a vulnerability**, or go straight to
<https://github.com/bapirex/tidydial/security/advisories/new>. Only the maintainers can see the
report.

Please include:

- what the problem is and what an attacker could do with it;
- the steps to reproduce it, or a proof of concept;
- the TidyDial version (shown in the About sheet), your Android version and device;
- whether the problem is already public anywhere.

Use fake phone numbers and fake contacts in your report. Never send real contact data.

## What to expect

TidyDial is maintained by volunteers, so responses are best effort. We aim to acknowledge a
report within a week, keep you updated while we work on it, and credit you in the advisory and
release notes if you would like that. Please give us a reasonable amount of time to release a fix
before you disclose the problem publicly.

## Scope

TidyDial has no internet permission and does not connect to any server, so there is no backend
or account to attack. The issues we care most about are:

- **Contact data integrity**: anything that makes TidyDial change, lose or corrupt contact data
  the user did not confirm, or breaks undo.
- **Permission misuse**: anything that lets another app use TidyDial to read or write contacts,
  or that gives TidyDial access it should not have (including network access added by a
  dependency).
- **Malicious contact data**: crafted names, numbers or account data in the contacts database that
  crash the app, cause wrong writes or leak data.
- **Data at rest**: problems with the settings or the undo journal kept in the app's private
  storage, including backups.

Please report problems in Android itself or in a dependency to the project concerned. If such a
problem affects TidyDial, let us know as well.
