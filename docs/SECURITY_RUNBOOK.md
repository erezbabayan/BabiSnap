# מדריך אבטחה – BabiSnap

מדיניות מיושרת עם BabiTk (מעודכן 2026-11-10). בלי שירותים בתשלום.

## עקרונות
- אל תשבור הקלטה, Google Calendar, תור BabiTk, או pairing קיים.
- שליחה לוואטסאפ כבויה כברירת מחדל. כשהיא כבויה, מילת הערוץ לא משנה יעד ולא נמחקת מהכותרת.
- שינוי אחד בכל פעם; בדיקה לפני שחרור; rollback מוכן.
- לעולם לא להדביק סיסמאות/טוקנים בצ׳אט או בלוגים.
- שאל את Erez לפני: סיבוב מפתחות חיים, branch protection, טוקן GitHub עם הרשאת `workflow`, שינוי פרוטוקול pairing, שחרור APK או שינוי keystore.

## חיוג מיידי
- «תתקשר» / «להתקשר» בלי תאריך או שעה עתידיים הוא יעד אחד: לא נכתב ליומן ולא ל-BabiTk.
- הקלטה של פקודת חיוג («תתקשר ל…», «תחייג ל…», «התקשר ל…») נעצרת אחרי 1.5 שניות שקט. שאר ההקלטות נשארות 5 שניות שקט ותקרת 60 שניות. טיימר השקט שלנו על התמלול החלקי עושה את זה, כי זמן השקט של מזהה הדיבור קבוע לכל ההקלטה.
- כברירת מחדל: `Intent.ACTION_DIAL` עם `tel:`. חיוג ישיר (`ACTION_CALL`) רק כש«אישור לבצע שיחות» דלוק ו־`CALL_PHONE` אושר, בסוף 5 השניות או בלחיצה על «חייג».
- `CALL_PHONE` מבוקש רק מהמתג בהגדרות, לא מכרטיס השיחה. אין `READ_PHONE_STATE`, אין יומן שיחות, אין הקלטה.
- אם ההרשאה נשללה במערכת, המתג נחשב כבוי והחייגן נפתח כמו קודם.
- יציאה מהכרטיס, כיבוי מסך, או ביטול לפני סוף הספירה: לא מחייגים.
- המספר נקרא מאנשי הקשר עם `READ_CONTACTS` שכבר קיימת, מוצג מוסווה, ולא נשמר ולא נרשם בלוג.
- שם מדויק ויחיד (כולל כינוי שמור) נפתח בכרטיס גם אם יש שם דומה בספר. בורר, בלי חיוג אוטומטי, רק כשאין התאמה מדויקת או כשכמה אנשי קשר נושאים אותו שם.
- ביטול, אין התאמה, בורר בלי בחירה, או סירוב לאנשי קשר: הודעה בעברית, בלי ליצור משימה.

## וואטסאפ (שלב 1)
- יעד אחד: מילת ערוץ לא נכתבת גם ליומן או ל-BabiTk.
- שליחה רק ב-Intent/Share לאפליקציה `com.whatsapp` (או אפליקציית WhatsApp Business המותקנת). אין Green-API, אין WhatsApp Business API, אין רשת חדשה.
- צ'אט פרטי נפתח אצל איש הקשר אחרי כרטיס של 5 שניות, עם תמלול בתיבה או עם קובץ שמע. קובץ הקול מוקלט רק אחרי שהנמען ידוע, לא במקביל לזיהוי הדיבור, ונשמר ב-cache בלבד דרך FileProvider עם `exported=false`. אין הרשאת אחסון חדשה.
- פקיעת הטיימר, ביטול, או יציאה מהכרטיס: ביטול ומחיקת הקובץ. אין טיוטה ואין היסטוריה. הקובץ אינו הודעה קולית של וואטסאפ; המשתמש לוחץ שלח שם.
- לוגים בלי תמליל, בלי שם נמען ובלי מספר טלפון.

## אין אנטי־וירוס באפליקציה
ההגנה היא hardening + סריקות CI + Play Protect אצל המשתמש:
- `usesCleartextTraffic=false` ו־`network_security_config`
- טוקן מכשיר רק ב־EncryptedSharedPreferences (`babitk_secure`)
- gitleaks ב־CI + Dependabot (Gradle / GitHub Actions)
- R8 minify עם keep rules ל־Rhino / WorkManager / widgets

## איפה הסודות
| סוד | איפה |
|-----|------|
| Device token (BabiTk) | EncryptedSharedPreferences במכשיר בלבד |
| Keystore חתימת APK | מחוץ ל־git (מקומי / גיבוי מוצפן אופליין) |
| GitHub token / secrets | GitHub Secrets / Vault — לא בריפו |
| `SNAP_REGISTRY_CLIENT_KEY` (מפתח לקוח לרישום) | GitHub Secret באותו שם, או `SNAP_REGISTRY_CLIENT_KEY` ב־`android/local.properties` (לא ב-git). נכנס ל־`BuildConfig.SNAP_REGISTRY_CLIENT_KEY` רק בזמן בנייה, ולכן כן נמצא ב־APK |
| `SNAP_REGISTRY_ADMIN_KEY` (מפתח מנהל) | שרת בלבד (BabiTk / Supabase). לא ב־APK, לא ב־`local.properties`, לא ב־CI של BabiSnap |

## טוקן מכשיר מול BabiTk
- פורמט: `babi_` + 32 בתים אקראיים (base64url)
- בשרת BabiTk נשמר כ־SHA-256; טוקן פעיל אחד למשתמש
- ניתוק: `babisnap_disconnect` בצד BabiTk
- מגבלת 60 בקשות/שעה/מכשיר — GET של tags ו־POST ל־voice-ingest חולקים אותה; TagSync שומר מטמון (~12 שעות) כדי לא לשרוף את המכסה

### סיבוב טוקן (אפס downtime)
1. Erez יוצר pairing חדש ב־BabiTk.
2. מאשרים ב־BabiSnap (מחליף את הטוקן הישן רק אחרי אימות).
3. רק אז מבטלים את הישן ב־BabiTk.
לעולם לא לבטל לפני שהחדש עובד.

## אם APK או מפתח חתימה נחשפו
1. לעצור הפצה של הקישור הישן.
2. לעדכן את Erez מיד.
3. עם אישורו בלבד: לבטל/לסובב מפתחות, לבנות APK חדש, לעדכן את [releases/latest](https://github.com/erezbabayan/BabiSnap/releases/latest).
4. אם נחשף device token — לנתק ב־BabiTk ולהנפיק pairing חדש.

## מפתחות רישום משתמשים (לקוח מול מנהל)

האפליקציה קוראת ל־`https://ghibfuinantybqidwadj.supabase.co/functions/v1/snap-users` עם `Authorization: Bearer <SNAP_REGISTRY_CLIENT_KEY>`. זה לא טוקן Google.

שני מפתחות נפרדים. השרת אמור לקבל את מפתח הלקוח ל־signin / heartbeat / disconnect ולקריאת הסטטוס של אותו אימייל והתקנה, ולדחות אותו לפעולות מנהל.

| מפתח | איפה | מה מותר איתו |
|------|------|----------------|
| `SNAP_REGISTRY_CLIENT_KEY` | בתוך ה־APK (BuildConfig) | דיווח לקוח: `signin`, `heartbeat`, `disconnect`, ו־GET של הסטטוס של ההתקנה הזו (`active` / `blocked` / `disconnected`) |
| `SNAP_REGISTRY_ADMIN_KEY` | שרת בלבד | רשימה, חסימה, ביטול חסימה ושאר פעולות מנהל. לא נשלח מהאפליקציה ולא נכנס לבנייה |

- לא לשמור אף ערך ב-git, בלוג, או ב־`gradle.properties` (הקובץ כן ב-git).
- מקומית, רק מפתח הלקוח: שורה `SNAP_REGISTRY_CLIENT_KEY=...` בקובץ `android/local.properties` (כבר ב־`.gitignore`). אין לשים שם את מפתח המנהל.
- ב־CI: הסוד `SNAP_REGISTRY_CLIENT_KEY` ב־GitHub Actions של `erezbabayan/BabiSnap` מוזרק כמשתנה סביבה ל־`./gradlew` (ראו `.github/workflows/ci.yml`). Gradle מעדיף את משתנה הסביבה על פני `local.properties`, וכותב אותו ל־`BuildConfig.SNAP_REGISTRY_CLIENT_KEY` רק בתיקיית הבנייה. ה־workflow לא מזריק את `SNAP_REGISTRY_ADMIN_KEY`.
- בלי מפתח הלקוח (או בלי כתובת) הבנייה עדיין עוברת. האפליקציה לא קוראת לשרת הרישום, כדי לא לחסום יומן או pairing.
- מפתח הלקוח כן נמצא ב־APK, כי המכשיר חייב להציג אותו. לא להדפיס אותו בלוגים. אם הוא נחשף — לסובב אותו בשרת; מפתח המנהל נשאר במקומו.

## CI
- `.github/workflows/gitleaks.yml` — סריקת סודות; Actions עם pin ל־SHA; `permissions: contents: read`
- `.github/dependabot.yml` — עדכונים חודשיים ל־Gradle ול־Actions
- מיזוג קובץ workflow חדש עלול לדרוש טוקן GitHub עם scope של `workflow`

## תחזוקה שבועית
כל שבת ב־22:00 שעון ישראל (שגרת הבוט BabiSnap): סייבר, אבטחה, מהירות, תקינות, תהליכים וצווארי בקבוק; תיקון בטוח אוטומטי; דוח בעברית לצ׳אט זה.
