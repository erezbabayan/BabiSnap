# פרטיות – BabiSnap

> מסמך קצר שמתאר מה האפליקציה עושה בפועל (לפי הקוד). המדיניות המלאה: [docs/PRIVACY_HE.md](docs/PRIVACY_HE.md).

## העיקרון
המידע נשאר במכשיר. תוכן ההקלטה נשלח **רק ליעד שבחרתם** (במילים או בכרטיס האישור), ולא לשום מקום אחר.

## זיהוי דיבור
ההקלטה מתומללת על ידי שירות זיהוי הדיבור של Android/Google (`SpeechRecognizer`), לפי הגדרות המכשיר שלכם. BabiSnap לא שולחת את האודיו לשרת משלה. הפענוח (תאריך, שעה, יעד) נעשה במכשיר.

## לאן התוכן יכול להגיע – רק היעד שנבחר
| יעד | מה נשלח | לאן |
|---|---|---|
| יומן Google | כותרת, תאריך/שעה, מיקום, ומוזמנים אם ביקשתם | Google Calendar API (‏`calendar.events`) בחשבון שחיברתם |
| יומן המכשיר | אותו אירוע | ספק היומן של Android במכשיר (`READ/WRITE_CALENDAR`) |
| BabiTk | כותרת, תאריך/שעה, מיקום, סוג (משימה/הערה), #תגיות | שרת BabiTk, עם טוקן מכשיר שנשמר מוצפן |
| שיחה | מספר הטלפון של איש הקשר | החייגן, או חיוג ישיר רק אם הפעלתם «אישור לבצע שיחות» (`CALL_PHONE`) |
| WhatsApp | הטקסט או קובץ הקול | אפליקציית WhatsApp במכשיר (שיתוף). פועל כברירת מחדל, אפשר לכבות |

אנשי הקשר (`READ_CONTACTS`) נקראים במכשיר רק כדי למצוא שם או מספר. הם לא מועלים לשום מקום.

## רישום מכשירים (Supabase)
כדי לנהל משתמשים, האפליקציה מדווחת לפונקציה `snap-users` ב־Supabase, רק בכתובת הקבועה הזו. אלה השדות:
- `email`: כתובת חשבון Google שהתחברתם איתה
- `install_id`: מזהה אקראי של ההתקנה
- `event`: ‏`signin` / `heartbeat` / `disconnect`
- `sources`: אילו חיבורים פעילים, `google_calendar` ו/או `babitk`
- `app_version`: גרסת האפליקציה
- `device_label`: יצרן ודגם המכשיר (למשל Samsung SM-…)

**לא נשלח** לרישום שום תוכן הקלטה, אירוע, משימה, איש קשר או מיקום.

## בדיקת עדכונים
פעם ביום, או כשלוחצים «בדוק עדכונים», האפליקציה קוראת את ה־API הציבורי של GitHub (`api.github.com/repos/erezbabayan/BabiSnap/releases/latest`), כדי לדעת מה הגרסה האחרונה. לא נשלח בבדיקה הזו שום מידע אישי.

## בלי מעקב
אין באפליקציה ספריות אנליטיקה, פרסום או מעקב. אין בה גם רישום לוגים (Log) של תוכן. גיבוי הענן של Android כבוי (`allowBackup="false"`). אתר ההורדה הוא דף סטטי בלי עוגיות ובלי טפסים.

## מחיקת כל הנתונים
בהגדרות ← «מחק את כל הנתונים»:
- מבטל את הגישה ליומן Google ומדווח ניתוק לרישום.
- מוחק את טוקן BabiTk ואת התורים (BabiTk ויומן).
- מוחק קובצי קול זמניים של WhatsApp.
- מאפס את כל ההגדרות.

נשאר רק מזהה ההתקנה האקראי. אירועים ופריטים שכבר נשלחו ליעד (יומן, BabiTk) נשארים שם, ונמחקים ביעד עצמו.

## הרשאות
`INTERNET`, `RECORD_AUDIO`, `READ_CONTACTS`, `CALL_PHONE`, `READ_CALENDAR`, `WRITE_CALENDAR`, `POST_NOTIFICATIONS`. כל אחת מבוקשת רק כשהפעולה צריכה אותה.

---

## English summary
- Data stays on the device. Content goes **only to the destination you choose**: Google Calendar API, the device calendar, BabiTk, the phone dialer, or the WhatsApp app (on by default, can be turned off in Settings).
- Speech-to-text is done by Android/Google `SpeechRecognizer`. BabiSnap has no audio server of its own.
- Device registry (Supabase `snap-users`) receives only: Google account email, random install ID, event (`signin`/`heartbeat`/`disconnect`), active sources (`google_calendar`/`babitk`), app version, and device maker/model. It receives no recording content.
- The update check reads the public GitHub releases API and sends no personal data.
- No analytics, ads or tracking SDKs, and Android cloud backup is off.
- «מחק את כל הנתונים» revokes Google access, wipes the BabiTk token, queues, temp voice files and settings.
- Full policy (Hebrew): [docs/PRIVACY_HE.md](docs/PRIVACY_HE.md).
