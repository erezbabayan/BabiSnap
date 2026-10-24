# מדריך אבטחה – BabiSnap

מדיניות מיושרת עם BabiTk (מעודכן 2026-10-24). בלי שירותים בתשלום.

## עקרונות
- אל תשבור הקלטה, Google Calendar, תור BabiTk, או pairing קיים.
- שינוי אחד בכל פעם; בדיקה לפני שחרור; rollback מוכן.
- לעולם לא להדביק סיסמאות/טוקנים בצ׳אט או בלוגים.
- שאל את Erez לפני: סיבוב מפתחות חיים, branch protection, טוקן GitHub עם הרשאת `workflow`, שינוי פרוטוקול pairing, שחרור APK או שינוי keystore.

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

## CI
- `.github/workflows/gitleaks.yml` — סריקת סודות; Actions עם pin ל־SHA; `permissions: contents: read`
- `.github/dependabot.yml` — עדכונים חודשיים ל־Gradle ול־Actions
- מיזוג קובץ workflow חדש עלול לדרוש טוקן GitHub עם scope של `workflow`

## תחזוקה שבועית
כל שבת ב־22:00 שעון ישראל (שגרת הבוט BabiSnap): סייבר, אבטחה, מהירות, תקינות, תהליכים וצווארי בקבוק; תיקון בטוח אוטומטי; דוח בעברית לצ׳אט זה.
