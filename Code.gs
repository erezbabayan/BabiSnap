/**
 * Voice -> Google Calendar (v1). Google Apps Script web app.
 * The parser section is pure JS (no Apps Script APIs) so it is unit-tested in Node.
 */
var TZ = 'Asia/Jerusalem';

/* ======================= PARSER (pure JS) ======================= */
var HE_DAYS = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
var HE_MONTHS = { 'ינואר': 1, 'פברואר': 2, 'מרץ': 3, 'מרס': 3, 'אפריל': 4, 'מאי': 5, 'יוני': 6, 'יולי': 7,
  'אוגוסט': 8, 'ספטמבר': 9, 'אוקטובר': 10, 'נובמבר': 11, 'דצמבר': 12 };
var HE_NUMS = { 'אחת עשרה': 11, 'אחד עשרה': 11, 'שתים עשרה': 12, 'שתיים עשרה': 12, 'אחת': 1, 'אחד': 1,
  'שתיים': 2, 'שתים': 2, 'שלוש': 3, 'ארבע': 4, 'חמש': 5, 'שש': 6, 'שבע': 7, 'שמונה': 8, 'תשע': 9, 'עשר': 10 };
var B = '(?:^|\\s)';            // start boundary (JS \b does not work with Hebrew)
var E = '(?=$|[\\s,.!?])';       // end boundary
var NUMW = '(\\d{1,2}|אחת עשרה|אחד עשרה|שתים עשרה|שתיים עשרה|אחת|אחד|שתיים|שתים|שלוש|ארבע|חמש|שש|שבע|שמונה|תשע|עשר)';

var DAY_LETTERS = { "א'": 'ראשון', "ב'": 'שני', "ג'": 'שלישי', "ד'": 'רביעי', "ה'": 'חמישי', "ו'": 'שישי', "ש'": 'שבת' };
var SYNONYMS = [
  [/(\s)(?:ב|ל)?(?:-)?יום ([אבגדהוש]')(?=$|[\s,.!?])/g, function (_, sp, l) { return sp + 'ביום ' + DAY_LETTERS[l]; }],
  [/(\s)(?:ה|ל)?יום שבת/g, '$1ביום שבת'],
  [/(\s)(?:בעוד יומיים|עוד יומיים)(?=$|[\s,.!?])/g, '$1מחרתיים'],
  [/(\s)(?:בעוד יום|עוד יום)(?=$|[\s,.!?])/g, '$1מחר'],
  [/(\s)(?:אחר הצהריים|אחרי הצהריים|אחה"צ|אחהצ|אחה צ)(?=$|[\s,.!?])/g, '$1בצהריים'],
  [/(\s)(?:לפנות ערב)(?=$|[\s,.!?])/g, '$1בערב'],
  [/(\s)(?:בצהרים)(?=$|[\s,.!?])/g, '$1בצהריים'],
  [/(\s)(?:בבקר)(?=$|[\s,.!?])/g, '$1בבוקר'],
  [/(\s)(?:בשעתיים)(?=$|[\s,.!?])/g, '$1לשעתיים'],
  [/(\s)(?:תזכיר לי|תזכירי לי|תכניס|תכניסי|תיצור|צור|תקבעו)(?=$|[\s,.!?])/g, '$1תקבע'],
  [/(\s)(?:ב-?חצות)(?=$|[\s,.!?])/g, '$1ב-00:00']
];
// Command prefixes ("תוסיף לי", "תקבע לי", "שים לי"...) and leftover time-of-day words never belong in a title.
var CMD_PREFIX_ = /^(?:(?:תוסיף|תוסיפי|הוסף|הוסיפי|תקבע|תקבעי|קבע|קבעי|תרשום|תרשמי|רשום|רשמי|שים|תשים|תשימי|תכניס|תכניסי|הכנס|תיצור|תיצרי|צור|צרי|תזמן|תזמני)(?:\s+(?:לי|לנו))?\s+|(?:לי|לנו)\s+)+/;
var TOD_ = /(^|\s)(?:בערב|בבוקר|בצהריים|בצהרים|בלילה|אחה"צ|אחר הצהריים|אחרי הצהריים|לפנות ערב)(?=$|[\s,.!?])/g;
function cleanTitle_(t) {
  t = t.replace(CMD_PREFIX_, '');
  t = t.replace(TOD_, ' – ');                      // a removed time word between two phrases becomes a separator
  t = t.replace(/\s+/g, ' ').replace(/(?:\s*–\s*)+/g, ' – ');
  t = t.replace(/^[\s–,.\-:]+|[\s–,.\-:]+$/g, '');
  t = t.replace(/(\s)(?:עם|של|ב|ל|ב-|ל-|בשעה) – /g, '$1').replace(/ – (?=(?:עם|של)\s)/g, ' ');
  return t.trim();
}
/* ---- place (stage 1 of locations): text only, Google Calendar turns it into a Maps link ---- */
// Known cities/towns, matched as whole tokens after a ב prefix ("במודיעין", "בבאר שבע"). Longest first at runtime.
var CITIES_ = ['תל אביב יפו', 'תל אביב', 'תל-אביב', 'ת"א', 'יפו', 'ירושלים', 'י-ם', 'חיפה', 'באר שבע', 'ב"ש', 'ראשון לציון', 'ראשל"צ',
  'פתח תקווה', 'פתח תקוה', 'פ"ת', 'אשדוד', 'נתניה', 'חולון', 'בני ברק', 'רמת גן', 'בת ים', 'אשקלון', 'רחובות', 'הרצליה פיתוח', 'הרצליה',
  'כפר סבא', 'רעננה', 'מודיעין מכבים רעות', 'מודיעין עילית', 'מודיעין', 'לוד', 'רמלה', 'נס ציונה', 'הוד השרון', 'גבעתיים',
  'קריית אונו', 'קרית אונו', 'אור יהודה', 'יהוד', 'ראש העין', 'אילת', 'טבריה', 'נצרת', 'נוף הגליל', 'עפולה', 'חדרה', 'זכרון יעקב',
  'קיסריה', 'עכו', 'נהריה', 'כרמיאל', 'צפת', 'קריית שמונה', 'קרית שמונה', 'קריית גת', 'קרית גת', 'קריית מוצקין', 'קריית ביאליק',
  'קריית אתא', 'קריית ים', 'קריית מלאכי', 'דימונה', 'ערד', 'אופקים', 'נתיבות', 'יבנה', 'גדרה', 'בית שמש', 'בית שאן',
  'מעלה אדומים', 'אריאל', 'אלעד', 'ביתר עילית', 'אור עקיבא', 'פרדס חנה', 'יקנעם', 'נשר', 'טירת כרמל', 'רמת השרון', 'גבעת שמואל',
  'כפר יונה', 'אבן יהודה', 'שוהם', 'מבשרת ציון', 'גני תקווה', 'סביון', 'קדימה', 'צורן', 'באר יעקב', 'מגדל העמק', 'שפרעם',
  'אום אל פחם', 'רהט', 'טייבה', 'קצרין', 'מצפה רמון', 'עין גדי', 'ים המלח', 'נתב"ג'];
// Venue words that open a place after ב ("במסעדת כפית", "בבית קפה", "במשרד של דני", "ברחוב הרצל 5"). Longest first at runtime.
var VENUES_ = ['בית קפה', 'בית חולים', 'בית ספר', 'בית כנסת', 'בית משפט', 'בית מרקחת', 'חדר ישיבות', 'חדר כושר', 'שדה תעופה',
  'מרכז מסחרי', 'מסעדת', 'מסעדה', 'קפה', 'קפית', 'משרד', 'משרדי', 'מלון', 'פאב', 'פארק', 'חוף', 'גן', 'רחוב', 'רח\'', 'שדרות',
  'שד\'', 'כיכר', 'מרכז', 'סטודיו', 'מכון', 'בניין', 'מגדל', 'מגדלי', 'מתחם', 'שוק', 'נמל', 'אולם', 'מועדון', 'חדר', 'קמפוס',
  'אוניברסיטה', 'אוניברסיטת', 'מכללה', 'מכללת', 'תיכון', 'ספרייה', 'ספריה', 'ספריית', 'מתנ"ס', 'מרפאה', 'מרפאת', 'קופת חולים',
  'קיבוץ', 'מושב', 'שכונת', 'שכונה', 'סניף', 'מספרה', 'מספרת', 'קניון', 'סופר', 'סופרמרקט', 'מכולת', 'בנק', 'דואר', 'בית'];
var STREETS_ = { 'רחוב': 1, 'רח\'': 1, 'שדרות': 1, 'שד\'': 1, 'כיכר': 1 };
var PLACE_MAX_NAME_ = 4, PLACE_MAX_CITY_ = 3;
// Tokens that end a place: people, time/date words, purpose words. A place never swallows them.
var PLACE_STOP_ = {};
('עם את ו מחר מחרתיים היום הערב הבוקר הלילה בצהריים ביום יום בעוד עוד בשעה שעה שעות דקות בבוקר בערב בלילה בחצות למשך לשעה ' +
 'לשעתיים לחצי לרבע וחצי ורבע ועשרים ועשר הבא הקרוב בשבוע השבוע שבוע בסוף בתחילת במחר ברבע רבע כדי בשביל לגבי על בנושא בקשר ' +
 'בעניין אחרי לפני עד ליום ל-יום ב-יום מיום ביחד יחד ואז הוא היא הם זה זו יש אין צריך בבקשה ליומן ביומן לדבר בזום בטלפון').split(' ')
  .forEach(function (w) { PLACE_STOP_[w] = 1; });
var PERIOD_WORDS_ = { 'בבוקר': 1, 'בערב': 1, 'בצהריים': 1, 'בלילה': 1, 'וחצי': 1, 'ורבע': 1, 'ועשרים': 1, 'ועשר': 1 };
var NUMWORD_RE_ = new RegExp('^(?:ב|ל|ב-|ל-|ה)?' + NUMW + '$');
var DAYMONTH_RE_ = new RegExp('^(?:ב|ל)-?(?:' + HE_DAYS.join('|') + '|' + Object.keys(HE_MONTHS).join('|') + ')$');
function byLenDesc_(a, b) { return b.length - a.length; }
// Every Israeli locality (CBS "רשימת ישובים", embedded at build time - no network), plus spelling variants. Generated, do not edit.
// GEN-LOCALITIES-BEGIN (tools/gen-localities.py, 1193 names incl. variants, from 1316 CBS localities)
var LOCALITIES_ = 'כעביה טבאש חג\'אג\'רה|כאוכב אבו אל היג\'א|כוכב יאיר צור יגאל|מודיעין מכבים רעות|עין כרם בי"ס חקלאי|מסעודין אל עזאזמה|בועיינה נוג\'ידאת|בית יצחק שער חפר|בנימינה גבעת עדה|בית לחם הגלילית|מדרשת בן גוריון|פוריה כפר עבודה|פוריה נווה עובד|קודייראת א צאנע|רמת הנשיא טראמפ|שבלי אום אל גנם|כפר הנוער הדתי|פוריה נוה עובד|פרדס חנה כרכור|באקה אל גרביה|דאלית אל כרמל|כפר חסידים א\'|כפר חסידים ב\'|כפר ראש הנקרה|לוחמי הגיטאות|מודיעין עילית|תראבין א צאנע|אבו ג\'ווייעד|אום אל קוטוף|ביר אל מכסור|גורנות הגליל|הרצליה פיתוח|חצור הגלילית|יהוד מונוסון|כפר רוזנואלד|מבוא מודיעים|מבואות יריחו|מג\'ד אל כרום|מודיעין עלית|מעלות תרשיחא|קריית ביאליק|קריית מוצקין|שושנת העמקים|אבו קורינאת|אבו רובייעה|בוסתן הגליל|בית חשמונאי|ג\'דיידה מכר|ג\'סר א זרקא|גבעון החדשה|גבעות הרואה|גבעת השלושה|גבעת ישעיהו|גני מודיעין|דייר אל אסד|חבצלת השרון|טובא זנגריה|יקנעם עילית|כפר גליקסון|כפר האורנים|כרם בן זמרה|להבות חביבה|מדרשת רופין|מחנה יהודית|מחנה תל נוף|מלאכי השלום|מעלה אדומים|מעלה החמישה|מקווה ישראל|משואות יצחק|משמר איילון|נתיב השיירה|ערב אל נעים|פוריה עילית|פקיעין חדשה|קריית טבעון|קריית יערים|קריית מלאכי|קריית נטפים|קריית ענבים|קריית עקרון|קריית שמונה|קרית ביאליק|קרית מוצקין|קרני שומרון|ראשון לציון|רמת מגשימים|שדמות דבורה|שדמות מחולה|שלווה במדבר|תל אביב יפו|אבו סריחאן|אבו קרינאת|אבו רוקייק|אום אל פחם|איילת השחר|אלון הגליל|אלוני הבשן|אלוני יצחק|אשדות יעקב|בארות יצחק|בית אלעזרי|בית גוברין|בית גמליאל|בית עוזיאל|ביתר עילית|בסמת טבעון|ברוש הבקעה|גאולי תימן|גבע בנימין|גבעת יערים|גבעת ניל"י|גבעת שמואל|גבעת שפירא|גן השומרון|דייר ראפאת|זכרון יעקב|חצור אשדוד|טירת יהודה|יסוד המעלה|יקנעם עלית|כוכב מיכאל|כפר אדומים|כפר ביאליק|כפר בן נון|כפר ויתקין|כפר ורבורג|כפר זוהרים|כפר ידידיה|כפר יחזקאל|כפר סירקין|כפר שמריהו|כרם בן שמן|להבות הבשן|מבוא חורון|מבשרת ציון|מוצא עילית|מזכרת בתיה|מחנה יוכבד|מנשית זבדה|מסילת ציון|מעגן מיכאל|מעלה אפרים|מעלה גלבוע|מעלה לבונה|מעלה עירון|מצפה אבי"ב|מצפה יריחו|מצפה נטופה|מרכז שפירא|משמר הירדן|משמר השבעה|משמר השרון|משמר יהודה|נאות הכיכר|נאות מרדכי|נווה אטי"ב|נווה דניאל|נווה מיכאל|נוף איילון|נזר שומרון|ניצני סיני|נצרת עילית|נתיב הגדוד|נתיב העשרה|עין אל אסד|עין הנצי"ב|עין השלושה|ערערה בנגב|פוריה עלית|קבוצת יבנה|קדימה צורן|קריית אונו|קריית ארבע|קריית שלמה|קרית טבעון|קרית יערים|קרית מלאכי|קרית נטפים|קרית ענבים|קרית עקרון|קרית שמונה|ראס אל עין|רמות השבים|רמות נפתלי|שבי שומרון|שדה אליעזר|שדה ורבורג|שדה עוזיהו|שדי תרומות|שייח\' דנון|שער העמקים|שער שומרון|תלמי אליהו|תלמי אלעזר|תלמי ביל"ו|תלמי יחיאל|אבו עבדון|אבן יהודה|אבן שמואל|אבני איתן|אור הגנוז|אור יהודה|אור עקיבא|אחוזת ברק|איבי הנחל|אלון מורה|אלון שבות|אלוני אבא|אלפי מנשה|אשל הנשיא|באר טוביה|באר מילכה|ביר הדאג\'|בית הערבה|בית השיטה|בית חוגלה|בית חורון|בית חירות|בית חלקיה|בית חנניה|בית יהושע|בית נחמיה|בית נקופה|בית רימון|בית שערים|ביתן אהרן|ביתר עלית|בנו עוקבה|בני דקלים|בני יהודה|בני נצרים|בני עטרות|ג\'לג\'וליה|גבעות עדן|גבעת אבני|גבעת ברנר|גבעת חיים|גבעת יואב|גוש עציון|גני יוחנן|גני תקווה|הוד השרון|ורד יריחו|חוג\'ייראת|חיבת ציון|טירת כרמל|יאנוח ג\'ת|יד בנימין|יד השמונה|כוכב השחר|כוכב יאיר|כוכב יעקב|כמאנה שבט|כסרא סמיע|כפר אוריה|כפר ביל"ו|כפר גדעון|כפר גלעדי|כפר דניאל|כפר החורש|כפר המכבי|כפר הנגיד|כפר הנשיא|כפר הרא"ה|כפר הרי"ף|כפר ורדים|כפר זיתים|כפר חיטים|כפר חנניה|כפר טרומן|כפר יאסיף|כפר יהושע|כפר מימון|כפר מסריק|כפר מרדכי|כפר סילבר|כפר עבודה|כפר עציון|כפר רופין|כפר שמואל|כרם ביבנה|כרם מהר"ל|כרמי יוסף|כרמי קטיף|מאיר שפיה|מבוא ביתר|מבוא דותן|מבואות ים|מג\'דל שמס|מגדל העמק|מוצא עלית|מחנה הילה|מחנה יתיר|מחנה מרים|מעוז חיים|מעין ברוך|מעלה גמלא|מעלה מכמש|מעלה עמוס|מצוקי ארץ|מצפה אילן|מצפה רמון|מרום גולן|משאבי שדה|משמר הנגב|משמר העמק|נאות גולן|נאות סמדר|נוה אטי"ב|נוה דניאל|נוה מיכאל|נווה אבות|נווה אילן|נווה איתן|נווה גדיד|נווה זוהר|נווה חריף|נווה ימין|נווה מבטח|נווה שלום|נוף הגליל|ניצני עוז|ניר אליהו|ניר ישראל|ניר עציון|ניר עקיבא|נצרת עלית|נתיב הל"ה|עין איילה|עין הבשור|עין החורש|עין המפרץ|עין השופט|עין זיוון|עין ייט"ב|עין נקובא|עין עירון|עין צורים|עין קנייא|עיר אובות|עצמון שגב|פעמי תש"ז|פתח תקווה|קריית אתא|קרית אונו|קרית ארבע|קרית שלמה|ראש צורים|רומת הייב|רמות מאיר|רמות מנשה|רמת הכובש|רמת השופט|רמת השרון|רמת יוחנן|רמת רזיאל|שדה אליהו|שדה נחמיה|שדות מיכה|שדי אברהם|שער אפרים|שער הגולן|תל תאומים|תלמי יוסף|אבו סנאן|אבו עמאר|אבו עמרה|אבו תלול|אבן יצחק|אבן מנחם|אבן ספיר|אבני חפץ|אום בטין|אל עריאן|באר אורה|באר גנים|באר יעקב|בארותיים|בית אורן|בית אלפא|בית אריה|בית הגדי|בית הלוי|בית העמק|בית יוסף|בית ינאי|בית יצחק|בית יתיר|בית מאיר|בית עובד|בית עזרא|בית עריף|בית שקמה|בני דרום|בני דרור|בני עי"ש|בני ציון|בר גיורא|בר יוחאי|בר כוכבא|ברור חיל|גבע כרמל|גבעות בר|גבעת אלה|גבעת זאב|גבעת ח"ן|גבעת כ"ח|גבעת עדה|גבעת עוז|גבעת שמש|גן הדרום|גן יאשיה|גן שמואל|גנות הדר|גני תקוה|גשר הזיו|גת רימון|דגניה א\'|דגניה ב\'|דייר חנא|חפץ חיים|חשמונאים|טירת צבי|יד מרדכי|יד רמב"ם|כנפי שחר|כפר אביב|כפר אחים|כפר בלום|כפר ברוך|כפר גלים|כפר חב"ד|כפר חושן|כפר חיים|כפר חרוב|כפר יונה|כפר יעבץ|כפר מונש|כפר מל"ל|כפר מנדא|כפר מנחם|כפר סאלד|כפר פינס|כפר קאסם|כפר שמאי|כפר תבור|כפר תפוח|כרם רעים|כרם שלום|כרמי עוז|כרמי צור|מבוא חמה|מגדל עוז|מגן שאול|מדרך עוז|מוקייבלה|מחנה טלי|מחנה יפה|מעין צבי|מצפה זיף|מצפה שלם|משמר דוד|נוה אבות|נוה אילן|נוה איתן|נוה גדיד|נוה זוהר|נוה חריף|נוה ימין|נוה מבטח|נוה שלום|נווה אור|נווה זיו|נווה ירק|נווה צוף|נופי פרת|ניר בנים|ניר גלים|ניר יצחק|נס ציונה|נצר חזני|נצר סרני|עין העמק|עין חצבה|עין חרוד|עין יעקב|עין כרמל|עין מאהל|עין ראפה|עין שריד|פוריידיס|פרדס חנה|פתח תקוה|צור הדסה|צור יגאל|צור יצחק|קדם ערבה|קדמת צבי|קצר א סר|קריית גת|קריית ים|קרית אתא|ראש העין|ראש פינה|רוח מדבר|רמת אביב|שאר ישוב|שבות רחל|שבי דרום|שבי ציון|שגב שלום|שדה אילן|שדה בוקר|שדה יואב|שדה יעקב|שדה יצחק|שדה נחום|שדה ניצן|שער מנשה|תל עדשים|תלמי יפה|אבו גוש|אבטליון|אביגדור|אדוריים|אור הנר|אל סייד|אשכולות|באר שבע|בוקייעה|בוקעאתא|בית ברל|בית ג\'ן|בית דגן|בית הלל|בית זיד|בית זית|בית זרע|בית חנן|בית ניר|בית צבי|בית קמה|בית קשת|בית רבן|בית שאן|בית שמש|בלפוריה|בן זכאי|בני ברק|בני ראם|בנימינה|בת שלמה|ג\'דיידה|ג\'נאביב|גבעולים|גבעתיים|גוש חלב|גליל ים|גן חיים|גן יבנה|גן שורק|גן שלמה|גני הדר|דריג\'את|הגושרים|הוואשלה|הזורעים|החותרים|הסוללים|הר גילה|הר עמשא|חוסנייה|חפצי בה|חרב לאת|טל מנשה|יושיביה|ים המלח|ירושלים|כיסופים|כפר ברא|כפר כמא|כפר כנא|כפר מצר|כפר נטר|כפר סבא|כפר עזה|כפר קיש|כפר קרע|כפר רות|מבקיעים|מגשימים|מודיעין|מונוסון|מרגליות|מרחב עם|משגב דב|משגב עם|נוה אור|נוה זיו|נוה ירק|נוה צוף|נווה ים|נחל עוז|נחשולים|נחשונים|ניצן ב\'|ניר דוד|ניר ח"ן|ניר יפה|ניר משה|ניר עוז|ניר צבי|נס הרים|נס עמים|עטאוונה|עיילבון|עין גדי|עין דור|עין הוד|עין ורד|עין חוד|עין יהב|עין שמר|עין תמר|עלי זהב|פני חבר|פני קדם|צוקי ים|צור משה|צור נתן|צפרירים|קוואעין|קוממיות|קלנסווה|קרית גת|קרית ים|קרן יער|ראס עלי|ריחאניה|רימונים|רמת דוד|רמת ישי|רמת צבי|רמת רחל|שדה דוד|שדה משה|שדה צבי|שדות ים|שדי חמד|שיבולים|תימורים|תל אביב|תל יוסף|תל יצחק|תל מונד|תל ציון|תל קציר|אביבים|אביחיל|אביעזר|אבירים|אדירים|אופקים|אורנים|אורנית|אחיהוד|אחיטוב|אחיסמך|אחיעזר|אילניה|איתנים|אל עזי|אל רום|אלומות|אלונים|אלי עד|אליכין|אליפלט|אלישיב|אלישמע|אלמגור|אמירים|אעבלין|אפיניש|אשקלון|אשתאול|בורגתה|ביצרון|בית אל|בן עמי|בן שמן|ברוכין|בת הדר|בת חפר|בת עין|גאולים|גדעונה|גיבתון|גינוסר|גיניגר|גינתון|גני טל|גני עם|גרופית|דבוריה|דימונה|דמיידה|הבונים|הדר עם|הודיות|הוזייל|הושעיה|המעפיל|הר אדר|הר בזק|הרצליה|זבארגה|זבדיאל|ח\'ואלד|חורפיש|חורשים|חיננית|חרוצים|טורעאן|טל שחר|יד חנה|יד נתן|יובלים|יזרעאל|ירקונה|כורזים|כפר הס|כרמיאל|לי און|לפידות|מבועים|מבטחים|מחניים|מי עמי|מכמורת|מכמנים|מלילות|מנחמיה|מעברות|מעיליא|מרחביה|משכיות|מתתיהו|נאעורה|נגוהות|נוה ים|נוקדים|נורדיה|נחליאל|ניצנים|ניר עם|נצאצרה|נתיבות|סאג\'ור|סח\'נין|עדי עד|עוזייר|עזריאל|עזריקם|עיינות|עין גב|עמינדב|עמיעוז|עראמשה|ערוגות|עתניאל|פדויים|פלמחים|פקיעין|פרדסיה|פרי גן|צוריאל|ציפורי|קדומים|קורנית|קיסריה|ראשל"צ|רביבים|רומאנה|רחובות|רינתיה|רם און|רמת גן|שא נור|שומריה|שלומית|שעלבים|שריגים|שתולים|תל עמל|תל שבע|תנובות|תרשיחא|אבטין|אביאל|אדורה|אדמית|אובנת|אודים|אורטל|אורים|אחוזם|איבים|אילון|אילות|אירוס|איתמר|אכסאל|אליפז|אלעזר|אלקוש|אלקנה|אמציה|אניעם|אריאל|ארסוף|אשבול|אשדוד|אשלים|ביריה|בסמ"ה|בקעות|ברכיה|ברקאי|בת חן|בת ים|בתרון|ג\'ולס|גאליה|גברעם|גילון|גיתית|גלאון|גן נר|געתון|ד\'הרה|דוב"ב|דישון|דלתון|הזורע|היוגב|העוגן|הרדוף|הררית|ורדון|זכריה|זרועה|זרזיר|זרחיה|זרעית|חברון|חד נס|חולדה|חולון|חולית|חולתה|חופית|חוקוק|חמדיה|חמירה|חניאל|חניתה|חנתון|חספין|חצרים|חרשים|טבריה|טייבה|טל אל|טלמון|טפחות|יבנאל|יודפת|יונדב|יחיעם|יטבתה|ייט"ב|יכיני|יסעור|יקנעם|יראון|ירדנה|ירוחם|ירחיב|כאבול|כדורי|כדיתה|כישור|כמאנה|כמהין|כסיפה|כסלון|כרכום|כרכור|כרמיה|להבים|לוזית|מגדים|מגידו|מגרון|מזרעה|מחולה|מחסיה|מטולה|מייסר|מירון|מכבים|מכורה|מכחול|מלכיה|מעונה|מעלות|מענית|מצובה|נבטים|נהורה|נהריה|נופית|נחושה|נחלים|נחשון|נטועה|נטעים|ניל"י|ניצנה|נירים|נירית|נמרוד|נעמ"ה|נתב"ג|נתניה|סביון|סואעד|סוסיה|סלעית|סנסנה|סתריה|ע\'ג\'ר|עבדון|עברון|עדנים|עופרה|עוקבי|עזריה|עילוט|עלמון|עמיחי|עמיעד|עמיקם|עספיא|עפולה|עראבה|ערערה|עשהאל|עתלית|פדואל|פוריה|פסגות|פסוטה|פרזון|פרשים|פתחיה|צאלים|צבעון|צופיה|צופית|צוקים|צורית|צלפון|צנדלה|צפריה|צרופה|קדרון|קדרים|קטורה|קלחים|קצרין|רבדים|רגבים|רוחמה|רחלים|ריינה|רכסים|רעננה|רשפון|רשפים|רתמים|שואבה|שומרה|שוקדה|שחרות|שיטים|שכניה|שלומי|שמשית|שפיים|שפרעם|שרונה|שתולה|תאשור|תלמים|אביה|אודם|אושה|אחיה|אטרש|אילת|אלעד|אסיף|אספר|אעצם|אפרת|ארבל|אשבל|אשחר|אשרת|בארי|בענה|בצרה|בקוע|ברעם|ברקן|ברקת|גבים|גדות|גדיש|גדרה|גודר|גונן|גזית|גיזו|גיתה|גלעד|גמזו|גשור|דביר|דברת|דולב|האון|הראל|זיתן|זמרת|זנוח|חגור|חגלה|חדיד|חדרה|חורה|חיפה|חמאם|חמדת|חמרה|חצבה|חריש|חרמש|חרשה|טירה|טמרה|יבנה|יגור|יהוד|ינוב|ינון|יערה|יפיע|יפית|יפעת|יפתח|יצהר|יציץ|יקום|ירכא|ישעי|ישרש|כברי|כחלה|כסרא|כרמל|לבון|לוטם|לוטן|לח"י|לימן|לכיש|לקיה|מגאר|מזרע|מיצר|מיתר|מנרה|מסדה|מעגן|משהד|משען|נגבה|נהלל|נופך|נטור|ניין|נערן|נצרת|סאסא|סחלב|סייד|סלמה|סעוה|עגור|עוזה|עולש|עזוז|עטרת|עיבל|עינת|עמקה|פארן|פדיה|פורת|פצאל|פרוד|צובה|צוחר|צופר|צורן|צרעה|קליה|ראמה|רבבה|רביד|ריחן|רמלה|רקפת|שבלי|שדמה|שוהם|שיזף|שילה|שילת|שמיר|שמעה|שמרת|שניר|שפיר|שריד|תדהר|תובל|תמרה|תמרת|תעוז|תפרח|תקוע|אסד|אפק|ב"ש|בצת|ג\'ש|ג\'ת|גבע|גבת|געש|דחי|חלץ|י ם|יעף|יפו|ישע|כחל|לוד|לשם|מצר|נוב|נחם|נחף|נטף|נען|נשר|סמר|עכו|ערד|פ"ת|צפת|קדר|רהט|שעב|שעל|שפר|שקף|ת"א'.split('|');
// GEN-LOCALITIES-END
var CITIES_SORTED_ = uniq_(CITIES_.concat(LOCALITIES_)).sort(byLenDesc_), VENUES_SORTED_ = VENUES_.slice().sort(byLenDesc_);
function uniq_(a) { var o = {}, r = []; for (var i = 0; i < a.length; i++) if (!o[a[i]]) { o[a[i]] = 1; r.push(a[i]); } return r; }
// first word -> phrases starting with it (longest first), so a lookup is O(1) instead of scanning ~1500 names per token
function indexByFirst_(list) {
  var idx = {};
  for (var i = 0; i < list.length; i++) { var f = list[i].split(' ')[0]; (idx[f] = idx[f] || []).push(list[i]); }
  return idx;
}
CITIES_SORTED_.index = indexByFirst_(CITIES_SORTED_);
function bare_(tok) { return tok.replace(/[,.!?:;]+$/, ''); }
function isPlaceStop_(tok) {
  var w = bare_(tok);
  return !w || PLACE_STOP_[w] === 1 || NUMWORD_RE_.test(w) || DAYMONTH_RE_.test(w) || /^(?:ב|ל|ה|מ)?-?\d/.test(w) || /^\d/.test(w);
}
/** If toks[i..] spells one of the phrases (after optional ב / ב- on the first token), returns {phrase, len}. */
function matchPhrase_(toks, i, list, needB) {
  var first = bare_(toks[i]);
  if (needB) { if (first.charAt(0) !== 'ב') return null; first = first.replace(/^ב-?/, ''); }
  if (list.index) list = list.index[first] || [];
  for (var k = 0; k < list.length; k++) {
    var words = list[k].split(' '), ok = words[0] === first;
    for (var j = 1; ok && j < words.length; j++) ok = i + j < toks.length && bare_(toks[i + j]) === words[j] && bare_(toks[i + j - 1]) === toks[i + j - 1];
    if (ok) return { phrase: list[k], len: words.length };
  }
  return null;
}
/** City after a place: known city ("במודיעין") or, after a named venue, a generic ב-word ("בגבעת עדה"). */
function matchCity_(toks, i, generic) {
  var c = matchPhrase_(toks, i, CITIES_SORTED_, true);
  if (c) return { text: c.phrase, len: c.len };
  if (!generic || isPlaceStop_(toks[i]) || bare_(toks[i]).charAt(0) !== 'ב' || bare_(toks[i]).length < 3) return null;
  if (matchPhrase_(toks, i, VENUES_SORTED_, true)) return null;
  var words = [bare_(toks[i]).replace(/^ב-?/, '')], n = 1;
  while (bare_(toks[i + n - 1]) === toks[i + n - 1] && i + n < toks.length && n < PLACE_MAX_CITY_ && !isPlaceStop_(toks[i + n]) &&
         bare_(toks[i + n]).charAt(0) !== 'ב') { words.push(bare_(toks[i + n])); n++; }
  return { text: words.join(' '), len: n };
}
/**
 * Finds the first place in s and replaces it by the PLACE_MARK_ token.
 * Patterns: ב<venue> [name] [ב<city>], ב<city>, אצל <name> [ב<city>], אצלי/אצלנו.
 * @return {{s:string, location:string, raw:string}|null}
 */
var PLACE_MARK_ = '@@PLACE@@';
/** Reads a place name after a venue / אצל (max PLACE_MAX_NAME_ words), stopping at time/date/people words, a city or another venue. */
function readName_(toks, end, street, venue) {
  var name = [], closed = bare_(toks[end - 1]) !== toks[end - 1];
  while (!closed && end < toks.length && name.length < PLACE_MAX_NAME_) {
    var t = toks[end], tw = bare_(t);
    // a bare number is a house/room number ("רחוב הרצל 5", "חדר ישיבות 3") unless a time word follows ("8 בערב", "5 וחצי")
    var houseNo = venue && (street ? name.length > 0 : true) && /^\d{1,4}[א-ת]?$/.test(tw) && !(end + 1 < toks.length && PERIOD_WORDS_[bare_(toks[end + 1])]);
    if (!houseNo) {
      if (isPlaceStop_(t) || matchPhrase_(toks, end, CITIES_SORTED_, true) || matchPhrase_(toks, end, VENUES_SORTED_, true)) break;
      if (name.length > 0 && tw.charAt(0) === 'ב' && matchCity_(toks, end, true)) break; // "כפית בגבעת עדה": the ב-word is a town
    }
    name.push(tw); end++;
    if (tw !== t) closed = true; // a comma closes the place
  }
  while (name.length && /^(?:של|ליד|מול|ה|ב|ל)$/.test(name[name.length - 1])) { name.pop(); end--; closed = false; }
  return { name: name, end: end, closed: closed };
}
/** Known locality at toks[i]; in a meeting also the speech-to-text forms "וברוכין"/"ובחיפה" (ב heard as וב). */
function matchKnownCity_(toks, i, meeting) {
  var m = matchPhrase_(toks, i, CITIES_SORTED_, true), t = toks[i];
  if (m || !meeting || t.charAt(0) !== 'ו' || t.length < 4) return m;
  var alt = toks.slice();
  alt[i] = t.slice(1);                     // "ובחיפה" -> "בחיפה"
  m = t.charAt(1) === 'ב' ? matchPhrase_(alt, i, CITIES_SORTED_, true) : null;
  if (!m) { alt[i] = 'ב' + t.slice(1); m = matchPhrase_(alt, i, CITIES_SORTED_, true); } // "וברוכין" -> "בברוכין"
  return m;
}
// "ביפו העתיקה", "בקריית גת הצפונית": up to two ה-adjectives after a city belong to it
function adjectivesAfter_(toks, end) {
  var n = 0;
  while (n < 2 && end + n < toks.length && bare_(toks[end + n - 1]) === toks[end + n - 1]) {
    var w = bare_(toks[end + n]);
    if (!/^ה[א-ת]{2,}(?:ה|ית|ים|ות|י)$/.test(w) || isPlaceStop_(w) || /^(?:הזה|הזאת|הזו|האלה|הבאה|הבאים|הקרובה)$/.test(w)) break;
    n++;
  }
  return n;
}
function extractPlace_(s, meeting) {
  var toks = s.replace(/^\s+|\s+$/g, '').split(' ');
  for (var i = 0; i < toks.length; i++) {
    var w = bare_(toks[i]), end = i + 1, parts = [], city = null, closed = false, m, n;
    if (w === 'אצלי' || w === 'אצלנו') parts.push(w);
    else if (w === 'אצל' && i + 1 < toks.length && !isPlaceStop_(toks[i + 1])) {
      n = readName_(toks, end, false, false);
      if (!n.name.length) continue;
      parts.push('אצל ' + n.name.join(' ')); end = n.end; closed = n.closed;
    }
    else if ((m = matchKnownCity_(toks, i, meeting))) {
      end = i + m.len; n = adjectivesAfter_(toks, end);
      city = m.phrase + (n ? ' ' + toks.slice(end, end + n).map(bare_).join(' ') : ''); end += n;
    }
    else if (matchPhrase_(toks, i, VENUES_SORTED_, true)) {
      // up to two venue segments: "בבנק לאומי בסניף רמת אביב" -> "בנק לאומי, סניף רמת אביב"
      while (parts.length < 2 && !closed && end - 1 < toks.length && (m = matchPhrase_(toks, end - 1, VENUES_SORTED_, true))) {
        n = readName_(toks, end - 1 + m.len, STREETS_[m.phrase] === 1, true);
        parts.push(m.phrase + (n.name.length ? ' ' + n.name.join(' ') : ''));
        end = n.end + 1; closed = n.closed;
      }
      end--;
    }
    if (!parts.length && !city) continue;
    if (parts.length && !closed && end < toks.length) {
      var c = matchCity_(toks, end, /\s/.test(parts[parts.length - 1]));
      if (c) { city = c.text; end += c.len; }
    }
    if (city) parts.push(city);
    var raw = toks.slice(i, end).join(' ').replace(/[,.!?:;]+$/, '');
    var trail = toks[end - 1].match(/[,.!?:;]+$/);
    toks.splice(i, end - i, PLACE_MARK_ + (trail ? trail[0] : ''));
    // "פגישה אצל רופא השיניים": who you meet stays in the title, the city does not
    var keep = /^אצל/.test(parts[0]) ? parts[0] : '';
    return { s: ' ' + toks.join(' ') + ' ', location: parts.join(', '), raw: raw, keep: keep, cityOnly: parts.length === 1 && !!city };
  }
  return null;
}
/* ---- meeting context: "פגישה (עם X) ב<place> ..." -> the ב-word is a place even when it is not a known town ---- */
var MEETING_RE_ = /(?:^|\s)(?:ו?(?:[הל]?פגישה|פגישת|ה?פגישות|להיפגש|להפגש|ניפגש|נפגש|נפגשים|ניפגשים|לפגוש|נפגוש|[הל]?מפגש|מפגשים|[הל]?ישיבה|ישיבת|[הל]?זימון|[הל]?ראיון|ראיון|ראיונות|[הל]?כנס|[הל]?סטנדאפ|[הל]?ספרינט|[הל]?זום|שיחת|ל?שיחת))(?=$|[\s,.!?])/;
var MEETING_KW_ = /^(?:ו?(?:[הל]?פגישה|פגישת|ה?פגישות|להיפגש|להפגש|ניפגש|נפגש|נפגשים|ניפגשים|לפגוש|נפגוש|[הל]?מפגש|מפגשים|[הל]?ישיבה|ישיבת|[הל]?זימון|[הל]?ראיון|ראיון|ראיונות|[הל]?כנס|[הל]?סטנדאפ|[הל]?ספרינט|[הל]?זום|שיחת|ל?שיחת))$/;
// ב-words that are never a place: topics, manner, online tools, time spans, pronouns, holidays
var NON_PLACE_B_ = {};
('בנוגע בקשר בעניין בענין בנושא בהקדם בדחיפות דחוף בבקשה בזמן בסביבות בערך בשבוע בחודש בשנה בסוף בתחילת באמצע בווידאו בוידאו ' +
 'בוידיאו בגוגל בוואטסאפ בווטסאפ בוואטסאפ בהצלחה ביחד בשעה ביום בין בלי במקום בגלל בהמשך בקרוב בכל בדיוק בתור בזום בטלפון בטימס ' +
 'בסקייפ במיט באונליין בטלפוניה בשיחה בהודעה במייל בצ\'אט במשך בחצי ברבע בשביל בשבילי בשבילך בשבילו בשבילה בשבילנו בעצם באמת ' +
 'בטוח בערב בבוקר בצהריים בצהרים בלילה במחר בעוד בו בה בי בך בנו בכם בהם בהן בעצמי בעצמו בחג בחגים בפסח בחנוכה בפורים בסוכות ' +
 'בשבועות בחופש בחופשה בשבת בשבתות בראש בכיף בשמחה בנחת בשקט בכוונה בלבד בנפרד בפרטיות בפרטי בנוכחות בהשתתפות בעקבות בהתאם ' +
 'בשניים בשניהם בשנינו בשלושה בארבעה בחמישה בשלושתנו בנוסף באופן בצורה במסגרת בהקשר בעקבות בתאריך בחודשים בשבועיים בימים בשעות בדקות בסביבה בהפסקה בהפסקת בארוחה בארוחת').split(' ')
  .forEach(function (w) { NON_PLACE_B_[w] = 1; });
// first names that start with ב ("פגישה עם דני ברק" must not become a place "רק")
var B_NAMES_ = { 'בני': 1, 'ברק': 1, 'בועז': 1, 'ברוך': 1, 'בתיה': 1, 'בלה': 1, 'בר': 1, 'בן': 1, 'בת': 1, 'בתאל': 1, 'בכור': 1, 'בצלאל': 1, 'בנימין': 1, 'ביבי': 1, 'בוריס': 1, 'בילי': 1, 'בתשבע': 1 };
function isPurposeWord_(w) { return /^(?:ל-?[א-ת]{2,}|על|כדי|בשביל|לגבי|שנדבר|ש[א-ת]{3,})$/.test(w); }
/** Only for meetings, after date/time/duration were cut out of s. @return same shape as extractPlace_ or null */
function extractMeetingPlace_(s) {
  var toks = s.replace(/^\s+|\s+$/g, '').split(/\s+/), k, j;
  for (k = 0; k < toks.length && !MEETING_KW_.test(bare_(toks[k])); k++);
  if (k >= toks.length || bare_(toks[k]) !== toks[k]) return null;
  j = k + 1;
  // one describing word may follow: "ישיבת צוות בקיבוץ יגור", "פגישה חשובה בברוכין"
  if (j + 1 < toks.length && bare_(toks[j]) === toks[j] && !/^(?:עם|את)$/.test(toks[j]) && !/^[בולש]/.test(toks[j]) &&
      !isPlaceStop_(toks[j]) && /^ו?ב/.test(toks[j + 1])) j++;
  if (j < toks.length && /^(?:עם|את)$/.test(toks[j])) {
    if (++j >= toks.length || isPlaceStop_(toks[j])) return null;
    var closed = bare_(toks[j]) !== toks[j]; j++;          // the first word after עם is always part of the name
    for (var extra = 0; !closed && extra < 2 && j < toks.length; extra++, j++) {
      var nw = bare_(toks[j]);
      if (/^ו?ב/.test(nw) || isPlaceStop_(nw) || isPurposeWord_(nw)) break;
      if (nw !== toks[j]) closed = true;
    }
    if (closed) return null;
  }
  if (j >= toks.length) return null;
  var t = bare_(toks[j]), pm = t.match(/^(ו?ב)-?(.+)$/);
  if (!pm || pm[2].length < 2 || /^[\d"'.\-]/.test(pm[2])) return null;
  var asB = 'ב' + pm[2];
  if (isPlaceStop_(t) || isPlaceStop_(asB) || NON_PLACE_B_[t] || NON_PLACE_B_[asB] || B_NAMES_[t] || PERIOD_WORDS_[asB] || TOD_.test(' ' + asB + ' ')) { TOD_.lastIndex = 0; return null; }
  TOD_.lastIndex = 0;
  var words = [pm[2]], end = j + 1, city = null, c;
  while (words.length < 3 && end < toks.length && bare_(toks[end - 1]) === toks[end - 1]) {
    var w = bare_(toks[end]);
    if (!w || w.indexOf(PLACE_MARK_) >= 0) break;
    // "באיקאה ראשון לציון": a known town right after the business name, said without ב
    if ((c = matchPhrase_(toks, end, CITIES_SORTED_, false))) { city = c.phrase; end += c.len; break; }
    if (isPlaceStop_(w) || isPurposeWord_(w) || /^[בוש]/.test(w) || /^\d/.test(w) || MEETING_KW_.test(w) || NON_PLACE_B_[w]) break;
    words.push(w); end++;
  }
  var raw = toks.slice(j, end).join(' ').replace(/[,.!?:;]+$/, ''), trail = toks[end - 1].match(/[,.!?:;]+$/);
  toks.splice(j, end - j, PLACE_MARK_ + (trail ? trail[0] : ''));
  return { s: ' ' + toks.join(' ') + ' ', location: words.join(' ') + (city ? ', ' + city : ''), raw: raw, keep: '' };
}
/* ---- stage 2: explicit invites only ("תזמין את תמר", "ותשלח לה זימון", "ותזמין אותה" -> back to "עם תמר").
 * Each invitee is a contact query: the name plus any marker words said after it ("תמר עבודה", "דני מהגן").
 * The invite phrase leaves the text; in the title only the first word of each name stays ("פגישה עם תמר"). ---- */
var INV_VERB_RE_ = /^ו?(?:תזמין|תזמיני|תזמינו|להזמין|הזמן|הזמיני|הזמינו|לזמן|תזמן|תזמני|תזמנו|זמן|זמני|זמנו)$/;
// "לזמן תמר" (schedule-family verbs) may skip "את"; "להזמין שולחן" may not
var INV_NOAT_RE_ = /^ו?(?:לזמן|תזמן|תזמני|תזמנו|זמני|זמנו)$/;
var SEND_VERB_RE_ = /^ו?(?:תשלח|תשלחי|תשלחו|שלח|שלחי|לשלוח|שליחת)$/;
var INV_NOUN_RE_ = /^ה?(?:זימון|הזמנה|זימונים|הזמנות)$/;
// "ותוסיף את דני לזימון": add-verbs count only when the phrase ends in "לזימון"/"להזמנה"
var INV_ADD_RE_ = /^ו?(?:תוסיף|תוסיפי|תוסיפו|הוסף|הוסיפי|להוסיף)$/;
var INV_TO_NOUN_RE_ = /^ל(?:זימון|הזמנה)$/;
var INV_PRON_RE_ = /^(?:לה|לו|להם|להן)$/;
var INV_OBJ_RE_ = /^(?:אותה|אותו|אותם|אותן)$/;
var NAME_STOP_RE_ = /^ו?(?:מחר|מחרתיים|היום|הערב|הבוקר|הלילה|השבוע|יום|שעה|על|לגבי|בנושא|כדי|כי|ש|אצל|עם|של|את|גם|בבקשה|ליד|דרך|אחרי|לפני|עד|מ|מ-|ב|ל|ב-|ל-|זימון|הזמנה|לפגישה|לישיבה|למפגש|אותה|אותו|אותם|אותן|לה|לו|להם|להן|לי|לנו|הזימון|ההזמנה|לזימון|להזמנה|זימונים|הזמנות)$/;
function nameStop_(w) {
  if (!w || NAME_STOP_RE_.test(w) || /^[בלמ]?-?\d/.test(w) || w.indexOf(PLACE_MARK_) >= 0) return true;
  if (!/^[א-תA-Za-z][א-תA-Za-z'"\-]*$/.test(w)) return true;
  if (/^ו?ב/.test(w) && !B_NAMES_[w.replace(/^ו/, '')]) return true;          // "במסעדת", "בשבע" (but "ברק" is a name)
  if (/^ו?ל-?(?:שעה|שעתיים|חצי|רבע|משך|גבי|דבר|ראות|סגור|תאם|פגישה|ישיבה|ארוחת|קפה|כבוד|בדוק|עבור)/.test(w)) return true;
  if (/^ו?ש[א-ת]{3,}$/.test(w) && /^ו?ש(?:נ|י|ת)/.test(w) && w.length > 4) return true; // "שנדבר", "שיביא", "שתביא"
  return PLACE_STOP_[w] === 1 || MEETING_KW_.test(w) || INV_VERB_RE_.test(w) || SEND_VERB_RE_.test(w);
}
/** People starting at toks[i]: "תמר", "תמר כהן", "תמר ויוסי", "תמר ואת יוסי", "תמר, יוסי ודני". @return {people:[[words]], end} */
function readPeople_(toks, i) {
  var people = [], cur = [];
  while (i < toks.length) {
    var raw = toks[i], w = bare_(raw), punct = w !== raw;
    if (cur.length && (w === 'ואת' || w === 'וגם')) { people.push(cur); cur = []; i++; continue; }
    if (cur.length && /^ו[א-ת]{2,}$/.test(w) && !nameStop_(w.slice(1)) && !nameStop_(w)) {
      people.push(cur); cur = [w.slice(1)]; i++; if (punct && raw.slice(-1) !== ',') break; continue;
    }
    if (nameStop_(w) || cur.length >= 3) break;
    cur.push(w); i++;
    if (punct) { if (raw.slice(-1) === ',' && i < toks.length && !nameStop_(bare_(toks[i]).replace(/^ו/, ''))) { people.push(cur); cur = []; continue; } break; }
  }
  if (cur.length) people.push(cur);
  return { people: people, end: i };
}
/** @return {s, invite:[query], names:[first words to show in the title when the text did not say "עם X"]} */
function extractInvites_(s) {
  var toks = s.replace(/^\s+|\s+$/g, '').split(/\s+/), cut = [], explicit = [], backRef = false, i, k, rp;
  function at(j) { return j < toks.length ? bare_(toks[j]) : ''; }
  for (i = 0; i < toks.length; i++) {
    var w = at(i);
    if (INV_VERB_RE_.test(w)) {
      k = i + 1; if (at(k) === 'גם') k++;
      if (INV_OBJ_RE_.test(at(k))) { backRef = true; k++; }
      else {
        if (at(k) === 'את') k++;
        else if (!INV_NOAT_RE_.test(w) || i === 0) continue;   // "להזמין שולחן" / "תזמן פגישה" are not invites
        rp = readPeople_(toks, k);
        if (!rp.people.length) continue;
        explicit = explicit.concat(rp.people); k = rp.end;
      }
      if (/^(?:לפגישה|לישיבה|למפגש)$/.test(at(k)) && k + 1 >= toks.length) k++;   // trailing "...ותזמין אותה לפגישה"
      cut.push([i, k]); i = k - 1;
    } else if (INV_ADD_RE_.test(w) && at(i + 1) === 'את') {   // "ותוסיף את דני לזימון"
      rp = readPeople_(toks, i + 2);
      if (!rp.people.length || !INV_TO_NOUN_RE_.test(at(rp.end))) continue;
      explicit = explicit.concat(rp.people); cut.push([i, rp.end + 1]); i = rp.end;
    } else if (SEND_VERB_RE_.test(w) || (w === 'עם' && INV_NOUN_RE_.test(at(i + 1)))) {
      k = i + 1;
      var also = false;
      if (at(k) === 'גם') { also = true; k++; }                          // "תשלח גם זימון ל..." / "תשלח גם לתמר"
      if (at(k) === 'את' && INV_NOUN_RE_.test(at(k + 1))) k++;          // "תשלח את הזימון לתמר"
      if (INV_NOUN_RE_.test(at(k)) && at(k + 1) === 'גם') { toks.splice(k + 1, 1); }   // "זימון גם לתמר"
      if (!INV_NOUN_RE_.test(at(k)) && !INV_PRON_RE_.test(at(k)) && /^ל-?[א-תA-Za-z]/.test(at(k)) && w !== 'עם' && !/^ו?שליחת$/.test(w)) {
        var t3 = toks.slice(); t3[k] = toks[k].replace(/^ל-?/, '');
        rp = readPeople_(t3, k);
        // "תשלח לתמר זימון" (noun after the name), or "תשלח גם לתמר" (bare "תשלח לתמר" may be a message, so "גם" is required)
        if (rp.people.length && (INV_NOUN_RE_.test(at(rp.end)) || (also && !at(rp.end) && !nameStop_(at(k).replace(/^ל-?/, ''))))) {
          explicit = explicit.concat(rp.people); k = rp.end + (INV_NOUN_RE_.test(at(rp.end)) ? 1 : 0);
          cut.push([i, k]); i = k - 1; continue;
        }
        continue;
      }
      if (INV_PRON_RE_.test(at(k)) && INV_NOUN_RE_.test(at(k + 1))) { backRef = true; k += 2; }
      else if (INV_NOUN_RE_.test(at(k)) && INV_PRON_RE_.test(at(k + 1))) { backRef = true; k += 2; }
      else if (INV_NOUN_RE_.test(at(k)) && /^ל-?[א-תA-Za-z]/.test(at(k + 1))) {
        var t2 = toks.slice(); t2[k + 1] = toks[k + 1].replace(/^ל-?/, '');
        rp = readPeople_(t2, k + 1);
        if (!rp.people.length) continue;
        explicit = explicit.concat(rp.people); k = rp.end;
      } else continue;
      cut.push([i, k]); i = k - 1;
    }
  }
  if (!cut.length) return { s: s, invite: [], names: [] };
  var people = [];
  if (backRef) {   // "עם תמר (מהעבודה) ... ותזמין אותה": the people after the first "עם"; their marker words leave the title
    for (i = 0; i < toks.length; i++) {
      if (at(i) !== 'עם' || INV_NOUN_RE_.test(at(i + 1)) || inCut_(cut, i)) continue;
      rp = readPeople_(toks, i + 1);
      people = rp.people;
      for (k = i + 1; k < rp.end; k++) {               // keep "תמר", "ויוסי"; drop the marker words, keep trailing punctuation
        var keep = false, tw = at(k);
        for (var q = 0; q < people.length; q++) if (tw === people[q][0] || tw === 'ו' + people[q][0] || tw === 'ואת' || tw === 'וגם') keep = true;
        if (!keep) { var pu = toks[k].match(/[,.!?:;]+$/); toks[k] = ''; if (pu && k > 0) toks[k - 1] = bare_(toks[k - 1]) + pu[0]; }
      }
      break;
    }
  }
  people = people.concat(explicit);
  var seen = {}, invite = [], names = [];
  for (i = 0; i < people.length; i++) {
    var qy = people[i].join(' ');
    if (seen[qy]) continue; seen[qy] = 1; invite.push(qy);
  }
  for (i = cut.length - 1; i >= 0; i--) toks.splice(cut[i][0], cut[i][1] - cut[i][0]);
  var rest = ' ' + toks.join(' ').replace(/\s+/g, ' ') + ' ';
  for (i = 0; i < explicit.length; i++) if (rest.indexOf(' ' + explicit[i][0]) < 0 && names.indexOf(explicit[i][0]) < 0) names.push(explicit[i][0]);
  return { s: rest, invite: invite, names: names };
}
function inCut_(cut, i) { for (var c = 0; c < cut.length; c++) if (i >= cut[c][0] && i < cut[c][1]) return true; return false; }
function withNames_(t, n) { var i = t.indexOf(' – '), w = ' עם ' + joinNames_(n); return i < 0 ? t + w : t.slice(0, i) + w + t.slice(i); }
function joinNames_(n) { return n.length < 2 ? n.join('') : n.slice(0, -1).join(', ') + ' ו' + n[n.length - 1]; }

// "להיפגש עם תמר" / "לפגוש את תמר" / "נפגש עם תמר" -> "פגישה עם תמר"
function meetingTitle_(t) {
  t = t.replace(/^ו?ה?זימון(?=\s|$)/, 'פגישה');                 // "זימון מחר עם דני" -> "פגישה עם דני"
  t = t.replace(/^ו?[לה]?(?=(?:פגישה|פגישת|ישיבה|ישיבת|מפגש|זימון|ראיון|כנס|סטנדאפ|ספרינט|זום|שיחת|שיחה)(?:\s|$))/, '');   // "תזמין את תמר לפגישה" -> "פגישה עם תמר"
  return t.replace(/^(?:להיפגש|להפגש|ניפגש|נפגש|נפגשים|ניפגשים)(?:\s+עם)?(?=\s|$)/, 'פגישה עם')
          .replace(/^(?:לפגוש|נפגוש)(?:\s+את)?(?=\s|$)/, 'פגישה עם').replace(/^פגישה עם$/, 'פגישה');
}

function toNum_(w) { return /^\d+$/.test(w) ? parseInt(w, 10) : HE_NUMS[w]; }
function pad2_(n) { return (n < 10 ? '0' : '') + n; }
function addDays_(y, m, d, k) {
  var t = new Date(Date.UTC(y, m - 1, d + k));
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate(), dow: t.getUTCDay() };
}
function dowOf_(y, m, d) { return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); }

function titleOf_(s) {
  var t = s.replace(new RegExp(B + '(?:ב|ל|ה|ו|של|עם|ב-|ל-|ביום|יום|בשעה|שעה|ל-?יום|ב-?יום)' + '(?=\\s*$)'), ' ');
  t = t.replace(/\s+/g, ' ').replace(/ ([,.!?])/g, '$1').replace(/([,.!?])(?:[,.!?])+/g, '$1').replace(/^[\s,.\-:]+|[\s,.\-:]+$/g, '').trim();
  // strip dangling prepositions left from removed parts (repeat)
  for (var j = 0; j < 3; j++) t = t.replace(/(?:^|\s)(?:ב|ל|ב-|ל-|ביום|בשעה)$/, '').replace(/[\s,.\-:]+$/, '').trim();
  return meetingTitle_(cleanTitle_(t));
}

/**
 * @param {string} text Hebrew free text
 * @param {{y:number,m:number,d:number,dow:number}} now  today's date in Asia/Jerusalem (dow 0=Sunday)
 */
function parseHebrewEvent(text, now) {
  var s = ' ' + String(text || '').replace(/[״“”]/g, '"').replace(/[׳‘’]/g, "'").replace(/\s+/g, ' ').trim() + ' ';
  var r = { durationMin: (now && now.defaultDuration) || 60, timeDefaulted: false, dateDefaulted: false, allDay: false, location: '' };
  // ---- synonyms -> canonical forms
  for (var si = 0; si < SYNONYMS.length; si++) s = s.replace(SYNONYMS[si][0], SYNONYMS[si][1]);
  function cut(re) { var m = s.match(re); if (m) s = s.replace(re, ' '); return m; }

  // leading command words
  s = s.replace(new RegExp('^\\s*(?:תקבע לי|תקבעי לי|קבע לי|קבעי לי|תקבע|תקבעי|קבע|תוסיף|תוסיפי|הוסף|תרשום|תרשמי|רשום)' + E), ' ');
  s = s.replace(new RegExp(B + '(?:ביומן|ליומן|בבקשה)' + E, 'g'), ' ');
  // ---- v2.14 all-day: "יום שלם" / "כל היום" / "יום מלא" / "all day" -> Calendar all-day event (cut before "היום" is read as a date)
  if (cut(new RegExp(B + '(?:(?:של|ל|ב|ה)\\s?)?(?:(?:ל|ב)?כו?ל(?: ה| ה-|-)?יום(?: כולו)?|(?:ל|ב|ה)?(?:-)?יום (?:שלם|מלא|שלם כולו)|(?:ל|ב)?יממה שלמה|all[- ]day)' + E, 'gi'))) r.allDay = true;
  // ---- stage 2: invitees (only an explicit "תזמין" / "תשלח לה זימון"); removed from the text before anything else
  var inv = MEETING_RE_.test(s) || /(?:^|\s)ל(?:פגישה|ישיבה|מפגש)(?=$|[\s,.!?])/.test(s) ? extractInvites_(s) : { s: s, invite: [], names: [] };
  s = inv.s; r.invite = inv.invite;

  // ---- location: "במקום X" / "בכתובת X" / "מיקום X" (until comma or end)
  var m = cut(/(?:^|\s)(?:במקום|בכתובת|מיקום:?|כתובת:?)\s+([^,]+?)\s*(?=,|$)/);
  if (m) r.location = m[1].trim();
  // ---- place in free text: "במסעדת כפית במודיעין", "אצל דני בחיפה", "בתל אביב" (before time/date, so "באר שבע"/"ראשון לציון" stay places)
  var meeting = MEETING_RE_.test(s);
  var place = r.location ? null : extractPlace_(s, meeting);
  if (place) { s = place.s; r.location = place.location; }

  // ---- duration
  var dur = [
    [B + 'ל(?:-)?שעה וחצי' + E, 90], [B + 'ל(?:-)?שעתיים וחצי' + E, 150], [B + 'ל(?:-)?שעתיים' + E, 120],
    [B + 'ל(?:-)?חצי שעה' + E, 30], [B + 'ל(?:-)?רבע שעה' + E, 15], [B + 'ל(?:-)?שעה' + E, 60]
  ];
  for (var i = 0; i < dur.length; i++) { if (cut(new RegExp(dur[i][0]))) { r.durationMin = dur[i][1]; break; } }
  m = cut(new RegExp(B + '(?:למשך|ל|ב)(?:-)?\\s?' + NUMW + ' (דקות|שעות)' + E));
  if (m) r.durationMin = toNum_(m[1]) * (m[2] === 'שעות' ? 60 : 1);

  // ---- date
  var date = null;
  if (cut(new RegExp(B + '(?:ה|ל|ב)?מחרתיים' + E))) date = addDays_(now.y, now.m, now.d, 2);
  else if (cut(new RegExp(B + '(?:ה|ל|ב)?מחר' + E))) date = addDays_(now.y, now.m, now.d, 1);
  else if (cut(new RegExp(B + '(?:ה|ל)?היום' + E))) date = addDays_(now.y, now.m, now.d, 0);
  if (!date) {
    m = cut(new RegExp(B + '(?:בעוד|עוד) ' + NUMW + ' ימים' + E));
    if (m) date = addDays_(now.y, now.m, now.d, toNum_(m[1]));
    else if (cut(new RegExp(B + '(?:בעוד|עוד) שבוע' + E))) date = addDays_(now.y, now.m, now.d, 7);
  }
  if (!date) {
    m = cut(new RegExp(B + '(?:ב|ל)?(?:-)?יום (' + HE_DAYS.join('|') + ')( הבא| הקרוב)?' + E));
    if (!m) m = cut(new RegExp(B + '(?:ב|ל)(' + HE_DAYS.join('|') + ')( הבא| הקרוב)?' + E));
    if (m) {
      var target = HE_DAYS.indexOf(m[1]);
      var k = (target - now.dow + 7) % 7;
      if (k === 0) k = 7; // same weekday => next week
      date = addDays_(now.y, now.m, now.d, k);
    }
  }
  if (!date) {
    m = cut(/(?:^|\s)(?:ב|ל)?(?:-)?(?:ה-?)?(\d{1,2})[\/.](\d{1,2})(?:[\/.](\d{2,4}))?(?=$|[\s,!?])/);
    var dd, mm, yy;
    if (m) { dd = +m[1]; mm = +m[2]; yy = m[3] ? +m[3] : null; }
    else {
      m = cut(new RegExp(B + '(?:ב|ל)?(?:-)?(?:ה-?)?(\\d{1,2}) (?:ב|ל)(' + Object.keys(HE_MONTHS).join('|') + ')(?: (\\d{4}))?' + E));
      if (m) { dd = +m[1]; mm = HE_MONTHS[m[2]]; yy = m[3] ? +m[3] : null; }
    }
    if (m && mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
      if (yy !== null && yy < 100) yy += 2000;
      if (yy === null) {
        yy = now.y;
        if (mm < now.m || (mm === now.m && dd < now.d)) yy++; // past date => next year
      }
      date = { y: yy, m: mm, d: dd, dow: dowOf_(yy, mm, dd) };
    }
  }
  if (!date) { date = addDays_(now.y, now.m, now.d, 0); r.dateDefaulted = true; }

  // ---- time
  var period = null;
  var pm = cut(new RegExp(B + '(בבוקר|בצהריים|בערב|בלילה)' + E));
  if (pm) period = pm[1];
  var h = null, min = 0, explicit24 = false;
  var P = '(?:בשעה|ב-|ב|ל-|ל|עד)?\\s?';
  m = cut(new RegExp(B + P + 'רבע ל(?:-)?' + NUMW + E));
  if (m) { h = toNum_(m[1]) - 1; min = 45; if (h === 0) h = 12; }
  if (h === null) {
    m = cut(new RegExp(B + P + '(\\d{1,2}):(\\d{2})' + E));
    if (m) { h = +m[1]; min = +m[2]; explicit24 = h >= 13 || m[1].length === 2 && h === 0 || h === 12; }
  }
  if (h === null) {
    // "בשעה 3", "ב-8", "ב8 בערב", "15 וחצי", "בשעה שלוש ורבע"
    m = cut(new RegExp(B + '(?:בשעה|ב-|ב|ל-|ל)\\s?' + NUMW + '( וחצי| ורבע| ועשרים| ועשר)?' + E)) ||
        cut(new RegExp(B + NUMW + '( וחצי| ורבע)' + E)) ||
        (period ? cut(new RegExp(B + NUMW + E)) : null);
    if (m) {
      h = toNum_(m[1]);
      min = { ' וחצי': 30, ' ורבע': 15, ' ועשרים': 20, ' ועשר': 10 }[m[2]] || 0;
      explicit24 = h >= 13;
    }
  }
  if (h !== null && h <= 24 && min < 60) {
    r.allDay = false; // an explicit time wins over "כל היום"
    if (!explicit24) {
      if (period === 'בבוקר') { if (h === 12) h = 0; }
      else if (period === 'בלילה') { if (h >= 7 && h <= 11) h += 12; else if (h === 12) h = 0; }
      else if (period) { if (h < 12) h += 12; }       // צהריים/ערב
      else if (h >= 1 && h <= 7) h += 12;              // no period: 1-7 => afternoon
    }
    if (h === 24) h = 0;
  } else if (period) {
    r.allDay = false;
    h = { 'בבוקר': 9, 'בצהריים': 13, 'בערב': 19, 'בלילה': 21 }[period]; min = 0;
  } else if (r.allDay) { h = 0; min = 0; r.durationMin = 1440; } // all-day: no time of day (not "defaulted")
  else { h = 9; min = 0; r.timeDefaulted = true; }

  // ---- title: r.title without the place (Calendar has a location field), r.titleFull keeps it (BabiTk task/note titles)
  // ---- meeting place by context ("פגישה בברוכין לראות את הבית"): after time/date are gone, so "בשבע"/"במחר" never reach it
  var g;
  if (meeting && (!r.location || (place && place.cityOnly)) && (g = extractMeetingPlace_(s))) {
    if (!r.location) { place = g; s = g.s; r.location = g.location; }
    else if (g.s.indexOf(PLACE_MARK_ + ' ' + PLACE_MARK_) >= 0) {
      // "פגישה בארומה ברמת אביב": business name + the town found earlier -> one place "ארומה, רמת אביב"
      s = g.s.replace(PLACE_MARK_ + ' ' + PLACE_MARK_, PLACE_MARK_);
      place = { location: g.location + ', ' + place.location, raw: g.raw + ' ' + place.raw, keep: '' };
      r.location = place.location;
    }
  }
  // a purpose right after the removed place gets a separator: "פגישה – לראות את הבית"
  var ts = s.replace(new RegExp(PLACE_MARK_ + '(?=\\s+(?!(?:לגבי|לכבוד|למען|לקראת|לפני|לאחר|ליד|למשך|לשם|לבד|לבין|לכל|לנו|לי|לו|לה|להם)(?:\\s|$))ל-?[א-ת]{2,})'), ' – ');
  r.title = titleOf_(ts.split(PLACE_MARK_).join(place && place.keep ? ' ' + place.keep + ' ' : ' ')) || 'פגישה';
  // v2.6: the invite phrase (verb + name + markers) never reaches the title; invitees are in r.invite only
  r.titleFull = place ? (titleOf_(s.split(PLACE_MARK_).join(place.raw)) || r.title) : r.title;
  r.y = date.y; r.m = date.m; r.d = date.d; r.dow = date.dow; r.hour = h; r.minute = min;
  r.startStr = date.y + '-' + pad2_(date.m) + '-' + pad2_(date.d) + ' ' + pad2_(h) + ':' + pad2_(min);
  r.confirm = 'נקבע: ' + r.title + ', יום ' + HE_DAYS[date.dow] + ' ' + date.d + '/' + date.m + ' ' + (r.allDay ? 'כל היום' : pad2_(h) + ':' + pad2_(min)) +
    (r.location ? (/^אצל/.test(r.location) ? ' ' : ' ב') + r.location : '') + (!r.allDay && r.durationMin !== 60 ? ' (' + r.durationMin + ' דק\')' : '');
  return r;
}

/* ======================= APPS SCRIPT WEB APP ======================= */
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

function nowParts_() {
  var p = Utilities.formatDate(new Date(), TZ, 'yyyy-M-d-u').split('-').map(Number);
  return { y: p[0], m: p[1], d: p[2], dow: p[3] % 7 };
}

function doPost(e) {
  try {
    var body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
    if (!secret || body.secret !== secret) return json_({ ok: false, message: 'שגיאה: קוד סודי שגוי' });
    var text = String(body.text || '').trim();
    if (!text) return json_({ ok: false, message: 'שגיאה: לא התקבל טקסט' });
    var p = parseHebrewEvent(text, nowParts_());
    var dry = body.dry === true || body.dry === 'true' || body.dry === 1 || body.dry === '1';
    if (dry) return json_({ ok: true, dry: true, message: 'בדיקה (לא נשמר) ' + p.confirm, parsed: p });
    var start = Utilities.parseDate(p.startStr, TZ, 'yyyy-MM-dd HH:mm');
    var end = new Date(start.getTime() + p.durationMin * 60000);
    var opts = { description: 'נוצר בקול: ' + text };
    if (p.location) opts.location = p.location;
    var ev = p.allDay ? CalendarApp.getDefaultCalendar().createAllDayEvent(p.title, start, opts)
                      : CalendarApp.getDefaultCalendar().createEvent(p.title, start, end, opts);
    return json_({ ok: true, message: p.confirm, eventId: ev.getId(), parsed: p });
  } catch (err) {
    return json_({ ok: false, message: 'שגיאה: ' + err });
  }
}

function doGet() { return json_({ ok: true, message: 'השירות פעיל. שלחו POST.' }); }

/** Run once from the editor to authorize + sanity check (dry). */
function testFromEditor() {
  Logger.log(JSON.stringify(parseHebrewEvent('פגישה עם דני מחר ב-14:00 לשעתיים', nowParts_())));
}

if (typeof module !== 'undefined') module.exports = { parseHebrewEvent: parseHebrewEvent };
