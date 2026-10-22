// Run: node parser.test.js — loads Code.gs (single source of truth) and test-cases.json (shared with Android JUnit test)
const fs = require('fs'), vm = require('vm');
const ctx = { module: { exports: {} } }; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname + '/Code.gs', 'utf8'), ctx);
vm.runInContext(fs.readFileSync(__dirname + '/run-cases.js', 'utf8'), ctx);
const out = JSON.parse(ctx.runCases(fs.readFileSync(__dirname + '/test-cases.json', 'utf8')));
out.log.forEach(l => console.log(l));
console.log(`\n${out.pass}/${out.total} passed`); process.exit(out.pass === out.total ? 0 : 1);
