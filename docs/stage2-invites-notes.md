# Stage 2 (not implemented) – meeting invites

Trigger: only an explicit phrase – "תזמין את תמר", "תשלח לה/לו זימון", "עם זימון לתמר". "פגישה עם תמר" alone never invites.

* OAuth: current scope `https://www.googleapis.com/auth/calendar.events` already allows `attendees[]`
  on events.insert/patch and `?sendUpdates=all` (Google sends the email). No new Google scope needed.
* Contacts: `READ_CONTACTS` (manifest + runtime request the first time an invite is asked for).
  Query `ContactsContract.CommonDataKinds.Email` by DISPLAY_NAME (prefix/contains, Hebrew + nikud-insensitive).
* Disambiguation – no memory: every invite looks the name up again.
  - 0 matches: card says "לא נמצא איש קשר בשם תמר" → event saved without invite (or type an email).
  - 1 match: auto-selected, card shows "✉️ זימון ל: תמר לוי · tamar@…".
  - 2+: picker listing ALL matches with full name + email each time; nothing is remembered,
    so a new "Tamar" tomorrow simply appears as another row.
* Send only after the 5s undo window (attendees added in the commit request); Undo = nothing sent.
* Parser: new field `invite: ["תמר"]`; invite words are removed from the title.
