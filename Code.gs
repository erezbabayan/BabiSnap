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
  'סניף', 'מספרה', 'מספרת', 'קניון', 'סופר', 'סופרמרקט', 'מכולת', 'בנק', 'דואר', 'בית'];
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
var CITIES_SORTED_ = CITIES_.slice().sort(byLenDesc_), VENUES_SORTED_ = VENUES_.slice().sort(byLenDesc_);
function bare_(tok) { return tok.replace(/[,.!?:;]+$/, ''); }
function isPlaceStop_(tok) {
  var w = bare_(tok);
  return !w || PLACE_STOP_[w] === 1 || NUMWORD_RE_.test(w) || DAYMONTH_RE_.test(w) || /^(?:ב|ל|ה|מ)?-?\d/.test(w) || /^\d/.test(w);
}
/** If toks[i..] spells one of the phrases (after optional ב / ב- on the first token), returns {phrase, len}. */
function matchPhrase_(toks, i, list, needB) {
  var first = bare_(toks[i]);
  if (needB) { if (first.charAt(0) !== 'ב') return null; first = first.replace(/^ב-?/, ''); }
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
function extractPlace_(s) {
  var toks = s.replace(/^\s+|\s+$/g, '').split(' ');
  for (var i = 0; i < toks.length; i++) {
    var w = bare_(toks[i]), end = i + 1, parts = [], city = null, closed = false, m, n;
    if (w === 'אצלי' || w === 'אצלנו') parts.push(w);
    else if (w === 'אצל' && i + 1 < toks.length && !isPlaceStop_(toks[i + 1])) {
      n = readName_(toks, end, false, false);
      if (!n.name.length) continue;
      parts.push('אצל ' + n.name.join(' ')); end = n.end; closed = n.closed;
    }
    else if ((m = matchPhrase_(toks, i, CITIES_SORTED_, true))) { city = m.phrase; end = i + m.len; }
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
    return { s: ' ' + toks.join(' ') + ' ', location: parts.join(', '), raw: raw };
  }
  return null;
}
// "להיפגש עם תמר" / "לפגוש את תמר" / "נפגש עם תמר" -> "פגישה עם תמר"
function meetingTitle_(t) {
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
  // ---- place in free text: "במסעדת כפית במודיעין", "אצל דני בחיפה", "בתל אביב" (before time/date, so "באר שבע"/"ראשון לציון" stay places)
  var place = r.location ? null : extractPlace_(s);
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

  // ---- title: r.title without the place (Calendar has a location field), r.titleFull keeps it (BabiTk task/note titles)
  r.title = titleOf_(s.split(PLACE_MARK_).join(' ')) || 'פגישה';
  r.titleFull = place ? (titleOf_(s.split(PLACE_MARK_).join(place.raw)) || r.title) : r.title;
  r.y = date.y; r.m = date.m; r.d = date.d; r.dow = date.dow; r.hour = h; r.minute = min;
  r.startStr = date.y + '-' + pad2_(date.m) + '-' + pad2_(date.d) + ' ' + pad2_(h) + ':' + pad2_(min);
  r.confirm = 'נקבע: ' + r.title + ', יום ' + HE_DAYS[date.dow] + ' ' + date.d + '/' + date.m + ' ' + pad2_(h) + ':' + pad2_(min) +
    (r.location ? (/^אצל/.test(r.location) ? ' ' : ' ב') + r.location : '') + (r.durationMin !== 60 ? ' (' + r.durationMin + ' דק\')' : '');
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
