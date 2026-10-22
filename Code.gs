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
function toNum_(w) { return /^\d+$/.test(w) ? parseInt(w, 10) : HE_NUMS[w]; }
function pad2_(n) { return (n < 10 ? '0' : '') + n; }
function addDays_(y, m, d, k) {
  var t = new Date(Date.UTC(y, m - 1, d + k));
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate(), dow: t.getUTCDay() };
}
function dowOf_(y, m, d) { return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); }

/**
 * @param {string} text Hebrew free text
 * @param {{y:number,m:number,d:number,dow:number}} now  today's date in Asia/Jerusalem (dow 0=Sunday)
 */
function parseHebrewEvent(text, now) {
  var s = ' ' + String(text || '').replace(/[״“”]/g, '"').replace(/[׳‘’]/g, "'").replace(/\s+/g, ' ').trim() + ' ';
  var r = { durationMin: (now && now.defaultDuration) || 60, timeDefaulted: false, dateDefaulted: false, location: '' };
  // ---- synonyms -> canonical forms
  for (var si = 0; si < SYNONYMS.length; si++) s = s.replace(SYNONYMS[si][0], SYNONYMS[si][1]);
  function cut(re) { var m = s.match(re); if (m) s = s.replace(re, ' '); return m; }

  // leading command words
  s = s.replace(new RegExp('^\\s*(?:תקבע לי|תקבעי לי|קבע לי|קבעי לי|תקבע|תקבעי|קבע|תוסיף|תוסיפי|הוסף|תרשום|תרשמי|רשום)' + E), ' ');
  s = s.replace(new RegExp(B + '(?:ביומן|ליומן|בבקשה)' + E, 'g'), ' ');

  // ---- location: "במקום X" / "בכתובת X" / "מיקום X" (until comma or end)
  var m = cut(/(?:^|\s)(?:במקום|בכתובת|מיקום:?|כתובת:?)\s+([^,]+?)\s*(?=,|$)/);
  if (m) r.location = m[1].trim();

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
  if (cut(new RegExp(B + '(?:ה|ל)?מחרתיים' + E))) date = addDays_(now.y, now.m, now.d, 2);
  else if (cut(new RegExp(B + '(?:ה|ל)?מחר' + E))) date = addDays_(now.y, now.m, now.d, 1);
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
    if (!explicit24) {
      if (period === 'בבוקר') { if (h === 12) h = 0; }
      else if (period === 'בלילה') { if (h >= 7 && h <= 11) h += 12; else if (h === 12) h = 0; }
      else if (period) { if (h < 12) h += 12; }       // צהריים/ערב
      else if (h >= 1 && h <= 7) h += 12;              // no period: 1-7 => afternoon
    }
    if (h === 24) h = 0;
  } else if (period) {
    h = { 'בבוקר': 9, 'בצהריים': 13, 'בערב': 19, 'בלילה': 21 }[period]; min = 0;
  } else { h = 9; min = 0; r.timeDefaulted = true; }

  // ---- title
  var t = s.replace(new RegExp(B + '(?:ב|ל|ה|ו|של|עם|ב-|ל-|ביום|יום|בשעה|שעה|ל-?יום|ב-?יום)' + '(?=\\s*$)'), ' ');
  t = t.replace(/\s+/g, ' ').replace(/^[\s,.\-:]+|[\s,.\-:]+$/g, '').trim();
  // strip dangling prepositions left from removed parts (repeat)
  for (var j = 0; j < 3; j++) t = t.replace(/(?:^|\s)(?:ב|ל|ב-|ל-|ביום|בשעה)$/, '').replace(/[\s,.\-:]+$/, '').trim();
  r.title = t || 'פגישה';
  r.y = date.y; r.m = date.m; r.d = date.d; r.dow = date.dow; r.hour = h; r.minute = min;
  r.startStr = date.y + '-' + pad2_(date.m) + '-' + pad2_(date.d) + ' ' + pad2_(h) + ':' + pad2_(min);
  r.confirm = 'נקבע: ' + r.title + ', יום ' + HE_DAYS[date.dow] + ' ' + date.d + '/' + date.m + ' ' + pad2_(h) + ':' + pad2_(min) +
    (r.location ? ' ב' + r.location : '') + (r.durationMin !== 60 ? ' (' + r.durationMin + ' דק\')' : '');
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
    var ev = CalendarApp.getDefaultCalendar().createEvent(p.title, start, end, opts);
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
