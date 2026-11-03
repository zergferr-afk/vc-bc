# My QR Card

An Android app that works as a digital business card, plus a business card scanner
that turns the cards you collect into ready-to-send intro emails in Outlook.
Everything runs on the phone. Nothing is sent to a server.

## The three tabs

**My QR codes.** Each entry gets its own QR code: your contact card (vCard), consulting
services, trainings, CV, LinkedIn, or anything else. Tap one to show it full screen
(brightness goes up and the screen stays on), and swipe between codes. Use the **⋮** menu
to edit, reorder or delete. Everything starts with placeholder details for you to replace.

- *CV and other files:* choose a PDF or Word file in the entry. It's kept on the phone,
  can be shared from the full-screen view, and can be attached to intro emails.
  A QR code can only hold a link, so to let people scan your CV, upload it to OneDrive or
  Google Drive and paste the share link into the same entry.

**Scanned cards.** Tap **Scan card**, take a photo (or pick one from the gallery). The app reads
the name, job title, company, email, phone and website. Check the fields, fix anything it
misread, and save. From a saved card you can send an intro email or save the person to your
phone's contacts.

**Templates.** One intro email per product or service. It starts with Consulting, Trainings
and Pumpsim / Ventsim / CONTROL. Replace the [bracketed] text with your own wording.
Placeholders such as `{firstName}`, `{company}` and `{myName}` are filled in automatically,
and `{firstName|Sir or Madam}` uses the fallback when the card had no name. Your own details
come from your contact card on the first tab. A template can attach your CV.

**Sending an intro email:** tap the email icon on a scanned card (or *Send intro email* on its
page), pick a template, check the message, and tap *Open in Outlook*. Outlook opens with the
recipient, subject, message and attachment filled in, so you only tap Send.

## Building

Requirements: Android Studio (Ladybug or newer) or JDK 17 + Android SDK 35.

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It is signed with the debug key so it
installs directly; use your own keystore before publishing to Google Play.

Every push to GitHub also builds the APK (`.github/workflows/build.yml`). Download it from
the run's **Artifacts** section.
