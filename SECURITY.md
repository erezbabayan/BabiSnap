# אבטחה – BabiSnap

## דיווח על חולשה
**אל תפתחו Issue ציבורי.** דווחו בפרטיות דרך GitHub:
**Security ← Report a vulnerability** (Private vulnerability reporting / Security Advisory) בריפו הזה.

כדאי לצרף בדיווח:
- גרסה (מופיעה בתחתית ההגדרות)
- דגם המכשיר וגרסת Android
- צעדים לשחזור
- מה ההשפעה

נאשר שקיבלנו את הדיווח ונעדכן כשיש תיקון. אנחנו מבקשים לא לפרסם את החולשה לפני שיצא תיקון.

## גרסאות נתמכות
רק הגרסה האחרונה ב־[Releases](https://github.com/erezbabayan/BabiSnap/releases/latest) מקבלת תיקוני אבטחה. מגרסה 2.40 האפליקציה בודקת לבד אם יש עדכון.

## מה עושים כדי לשמור על המשתמשים
- **חתימה קבועה:** כל APK רשמי חתום באותה תעודה. ‏SHA-1: `E7:BE:4B:D3:84:3E:BE:5C:2B:8F:1F:5A:D5:49:2E:AF:24:A4:1C:6B`. אפשר לבדוק עם `apksigner verify --print-certs`.
- **סודות:** טוקן BabiTk נשמר מוצפן (androidx.security). הוא לא נרשם בלוג, לא מוצג ולא נשלח לאף שרת מלבד BabiTk. בבנייה משולב רק מפתח הלקוח של רישום המכשירים, לא מפתח הניהול. סריקת gitleaks רצה על כל PR.
- **רשת:** HTTPS בלבד. רישום המכשירים פונה רק לכתובת `snap-users` הקבועה.
- **פעולות רגישות דורשות אישור:** חיוג ישיר רק אחרי «אישור לבצע שיחות» וספירה גלויה. WhatsApp כבוי כברירת מחדל. כל פעולה מוצגת בכרטיס אישור עם ביטול.
- **מינימום הרשאות:** כל הרשאה מבוקשת רק כשצריך אותה. גיבוי הענן של Android כבוי.
- **CI:** בדיקות יחידה ובנייה על כל PR. אין מיזוג בלי CI ירוק.

פרטים נוספים: [docs/SECURITY_RUNBOOK.md](docs/SECURITY_RUNBOOK.md) · פרטיות: [PRIVACY.md](PRIVACY.md)

---

## English summary
- **Report vulnerabilities privately** via GitHub **Security → Report a vulnerability** (private security advisory). Please don't open public issues.
- Only the latest release is supported.
- Official APKs are signed with one fixed certificate (SHA-1 above).
- The BabiTk token is stored encrypted and never logged. Only the registry client key is built into the app, never the admin key. gitleaks runs on every PR.
- HTTPS only. Direct calling and WhatsApp are opt-in, and every action has a confirm card with cancel.
