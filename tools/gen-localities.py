#!/usr/bin/env python3
"""Builds the LOCALITIES_ string embedded in Code.gs from data/cbs-localities.json (CBS / data.gov.il "רשימת ישובים").

Run:  python3 tools/gen-localities.py   (rewrites the block between the GEN-LOCALITIES markers in Code.gs)
No network: the CBS list was downloaded once into data/cbs-localities.json.
"""
import json, re, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
names = json.load(open(os.path.join(ROOT, 'data', 'cbs-localities.json'), encoding='utf-8'))['names']

# Single-word localities that are also everyday words / first names ("בשני" = Monday, "בעלי" = my husband, "בצופים" = scouts).
# A meeting "ב<word>" with one of these still becomes a place through the generic meeting rule, so nothing is lost.
AMBIGUOUS = set('''
אומץ אורה אורות אזור אחווה אייל אלון אמונים ארגמן ארז אתגר בחן בטחה ברוש ברכה ברק גבעות גורן גזר גלגל גפן גשר דבורה דור דורות דן
דפנה דקל הילה זוהר זמר חבר חוסן חירות חלוץ חצב טללים יובל יעל יעד יתד כליל כלנית כנף כמון כרמים לביא להב לפיד מאור מגדל מגן מגל
מורשת מזור מטע מיטב מישר מנוחה מנוף מנות מסד מסלול מסעדה מעוז מעון מתן נווה נועם נורית נחלה ניצן נטע סגולה סולם סופה ספיר סער עדי
עומר עופר עוצם עזר עידן ענב פטיש פלך קשת קלע קציר רווחה רחוב רותם שדרות שחר שירה שלווה שני שקד שורש שורשים תקומה תושיה תומר תלם
תירוש יגל רנן רעים שזור אדרת אלומה אמנון אביטל יונתן אבשלום אליקים עמנואל אליאב גיאה מתת מצפה מסילות רמות גבעתי מולדת נופים מעגלים
משמרת משמרות עמוקה יסודות משואה עלי מירב קדמה שלוחות גנים גנות מגדלים שרשרת צופים לבנים יבול מפלסים אפיק אפיקים סעד דליה נוגה
חגי בעמק כנות עשרת מלאה שובה חומש מוסד אומן בניה נעלה עלמה מיטל גילת חזון תרום כדים קבועה מצליח שחרית אלמוג הודיה ידידה רוויה נעורים עלומים גבולות
מורן עמיר אביגיל אוהד איתן רועי יקיר יהל ספיר שובל נריה צביה חמד מעש בני גת כנרת מנחה מבוא מרכז שדה כפר קדימה רגבה זיקים טנא
'''.split())
SPLIT = {  # compound municipalities whose parts are said on their own
  'תל אביב - יפו': ['תל אביב', 'יפו'], 'מודיעין-מכבים-רעות': ['מודיעין', 'מכבים'], 'בנימינה-גבעת עדה': ['בנימינה', 'גבעת עדה'],
  'בית יצחק-שער חפר': ['בית יצחק'], 'פרדס חנה-כרכור': ['פרדס חנה', 'כרכור'], 'קדימה-צורן': ['צורן'], 'יהוד-מונוסון': ['יהוד', 'מונוסון'],
  'מעלות-תרשיחא': ['מעלות', 'תרשיחא'], 'כוכב יאיר-צור יגאל': ['כוכב יאיר', 'צור יגאל'], "ג'דיידה-מכר": ["ג'דיידה"],
  'כסרא-סמיע': ['כסרא'], 'שגב-שלום': [], 'חצור-אשדוד': [],
}
EXTRA = ['ת"א', 'י-ם', 'ב"ש', 'ראשל"צ', 'פ"ת', 'נתב"ג', 'ים המלח', 'הרצליה פיתוח', 'תל אביב יפו', 'מודיעין מכבים רעות', 'נוף הגליל',
         'עין גדי', 'נצרת עילית', 'קריית שמונה', 'אילת', 'קיסריה', 'ירושלים', 'מצפה רמון', 'כנרת', 'גוש עציון', 'רמת אביב', 'גבעת עדה']

out = set()
def add(n):
    n = re.sub(r'\s+', ' ', n.replace('-', ' ').replace('(', ' ').replace(')', ' ')).strip()
    if not n or (' ' not in n and n in AMBIGUOUS) or len(n) < 2:
        return
    out.add(n)
    for a, b in (('קריית', 'קרית'), ('תקווה', 'תקוה'), ('נווה', 'נוה'), ('עילית', 'עלית')):
        if a in n: out.add(n.replace(a, b))
        if b in n and a not in n and b != 'עלית': out.add(n.replace(b, a))
for raw in names:
    n = raw.strip()
    m = re.match(r'^(.*?)\s*\((.*)\)\s*$', n)
    if m:
        base, inner = m.group(1).strip(), m.group(2).strip()
        add(base)
        if inner not in ('שבט', 'יישוב', 'ישוב', 'מושב', 'קיבוץ', 'איחוד', 'מאוחד', 'מושבה', 'קבוצה', 'כפר נוער', 'ישיבה', 'קהילת חינוך'):
            add(inner)   # "ג'ש (גוש חלב)", "ניר דוד (תל עמל)"
        n = base
    else:
        add(n)
    if n in SPLIT:
        for p in SPLIT[n]: add(p)
    elif ' - ' in n:
        add(n.split(' - ')[0])
for e in EXTRA: add(e)
# never let a locality swallow a time/date/stop word
for bad in ('שבע', 'שמונה', 'עשר', 'אחת', 'שלוש', 'ערב', 'בוקר', 'לילה', 'צהריים', 'יום', 'שבוע', 'שבת', 'ראשון'):
    out.discard(bad)
lst = sorted(out, key=lambda x: (-len(x), x))
js = "var LOCALITIES_ = '" + '|'.join(lst).replace('\\', '\\\\').replace("'", "\\'") + "'.split('|');"
path = os.path.join(ROOT, 'Code.gs')
src = open(path, encoding='utf-8').read()
start, end = '// GEN-LOCALITIES-BEGIN', '// GEN-LOCALITIES-END'
a, b = src.index(start), src.index(end)
src = src[:a] + start + ' (tools/gen-localities.py, %d names incl. variants, from %d CBS localities)\n' % (len(lst), len(names)) + js + '\n' + src[b:]
open(path, 'w', encoding='utf-8').write(src)
print(len(lst), 'localities written')
