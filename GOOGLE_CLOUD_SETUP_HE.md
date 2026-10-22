# הגדרה חד-פעמית ב-Google Cloud (חינם, בערך 15 דקות, מהמחשב)

צריך לעשות את זה **פעם אחת בלבד**, ועל ידי Erez בלבד. אחרי זה כל משתמש מתקין את ה-APK ומתחבר עם החשבון שלו. לא צריך כרטיס אשראי.

הנתונים שתצטרך:
- **Package name:** `com.babisnap.app`
- **SHA-1 של מפתח החתימה:** `E7:BE:4B:D3:84:3E:BE:5C:2B:8F:1F:5A:D5:49:2E:AF:24:A4:1C:6B`

## 1. יצירת פרויקט
1. היכנס ל-https://console.cloud.google.com עם חשבון הגוגל שלך (אם זו הפעם הראשונה, אשר את תנאי השימוש).
2. למעלה, ליד הלוגו: **Select a project** ← **New project** ← שם: `BabiSnap` ← **Create**. ודא שהפרויקט החדש נבחר למעלה.

## 2. הפעלת Google Calendar API
1. בתפריט ☰ ← **APIs & Services** ← **Library**.
2. חפש `Google Calendar API` ← לחץ עליו ← **Enable**.

## 3. מסך ההסכמה (OAuth consent screen)
1. ☰ ← **APIs & Services** ← **OAuth consent screen** (בממשק החדש: **Google Auth Platform** ← **Get started**).
2. App name: `BabiSnap`. User support email: המייל שלך. ← Next.
3. Audience: **External** ← Next. Contact email: המייל שלך ← Next ← אשר ← **Create**.
4. **Data Access** (או Scopes) ← **Add or remove scopes** ← סמן `.../auth/calendar.events` (אפשר להדביק בתיבת הסינון: `https://www.googleapis.com/auth/calendar.events`) ← **Update** ← **Save**.
5. **Audience** ← **Test users** ← **Add users** ← הוסף את המייל שלך ואת המיילים של כל מי שישתמש (עד 100) ← **Save**.

> **מצב Testing:** רק משתמשי הבדיקה שהוספת יכולים להתחבר. ייתכן שגוגל תבקש מהם לאשר שוב את הגישה בערך פעם בשבוע (זה מאפיין של מצב Testing).
> **אפשרות: Publish app** (ב-Audience ← **Publish app**): כל אחד יכול להתחבר, אבל יופיע לו מסך "Google hasn't verified this app" (לוחצים Advanced ← Continue), ויש מגבלה של 100 משתמשים עד שעוברים אימות של גוגל. לשימוש אישי ומשפחתי, Testing מספיק.

## 4. יצירת OAuth client מסוג Android
1. ☰ ← **APIs & Services** ← **Credentials** ← **Create credentials** ← **OAuth client ID** (או ב-Google Auth Platform ← **Clients** ← **Create client**).
2. Application type: **Android**.
3. Name: `BabiSnap Android`.
4. Package name: `com.babisnap.app`
5. SHA-1 certificate fingerprint: `E7:BE:4B:D3:84:3E:BE:5C:2B:8F:1F:5A:D5:49:2E:AF:24:A4:1C:6B`
6. **Create**. לא צריך להעתיק שום Client ID לאפליקציה. גוגל מזהה אותה לפי שם החבילה וה-SHA-1.

זהו. אין צורך במפתח API, בשרת או בתשלום. מכסת ה-Calendar API החינמית (מאות אלפי בקשות ביום לפרויקט) רחוקה מאוד ממה שמשתמשים כאן.

## בעיות נפוצות
| הודעה | סיבה |
|---|---|
| `DEVELOPER_ERROR` או `10:` בזמן התחברות | שם החבילה או ה-SHA-1 ב-Android client לא תואמים בדיוק, או שעוד לא עברו כמה דקות מהיצירה. |
| "Access blocked: app has not completed verification" | המשתמש לא נמצא ברשימת Test users. |
| שגיאה 403 מיומן גוגל | ה-Calendar API לא הופעל (שלב 2) או שהופעל בפרויקט אחר. |

> **חשוב:** ה-APK חתום במפתח קבוע (שמור אצל Grok Bot בתיבה, בתיקייה `/workspace/secrets/`). גרסאות עתידיות חייבות להיחתם באותו מפתח, אחרת ה-SHA-1 ישתנה וההתחברות תיכשל.
