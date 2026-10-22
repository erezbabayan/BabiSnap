// ES5 harness, runs both in Node (vm) and in Rhino (Android JUnit).
function runCases(jsonText) {
  var data = JSON.parse(jsonText), pass = 0, log = [];
  for (var i = 0; i < data.cases.length; i++) {
    var txt = data.cases[i][0], exp = data.cases[i][1], r = parseHebrewEvent(txt, data.now), bad = [];
    for (var k in exp) if (r[k] !== exp[k]) bad.push(k + ': expected ' + JSON.stringify(exp[k]) + ' got ' + JSON.stringify(r[k]));
    if (bad.length) log.push('FAIL ' + txt + ' | ' + bad.join('; ')); else { pass++; log.push('PASS ' + txt + ' => ' + r.confirm); }
  }
  return JSON.stringify({ pass: pass, total: data.cases.length, log: log });
}
