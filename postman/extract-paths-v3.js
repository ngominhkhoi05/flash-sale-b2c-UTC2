// Parse endpoints line by line
const fs = require('fs');
const path = require('path');

function walk(dir, files = []) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) walk(p, files);
    else if (f.endsWith('Controller.java')) files.push(p);
  }
  return files;
}

const root = 'd:/flash-sale-b2c-UTC2/src/main/java/com/b2c/flash_sale_b2c_UTC2';
const controllers = walk(root);
const endpoints = [];
for (const c of controllers) {
  const src = fs.readFileSync(c, 'utf8');
  const classMatch = src.match(/@RequestMapping\s*\(\s*"([^"]+)"\s*\)/);
  if (!classMatch) continue;
  const base = classMatch[1];
  const lines = src.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const m1 = line.match(/@(Get|Post|Put|Delete|Patch)Mapping/);
    if (!m1) continue;
    const method = m1[1];
    let args = '';
    const idx = m1.index;
    const parenStart = line.indexOf('(', idx);
    if (parenStart !== -1) {
      const parenEnd = line.indexOf(')', parenStart);
      if (parenEnd !== -1) {
        args = line.substring(parenStart + 1, parenEnd);
      }
    }
    let val = null;
    const vm = args.match(/(?:value|path)\s*=\s*"([^"]+)"/);
    if (vm) val = vm[1];
    else {
      const qm = args.match(/"([^"]+)"/);
      if (qm) val = qm[1];
    }
    if (val) {
      endpoints.push({ method, path: base + val });
    } else {
      endpoints.push({ method, path: base });
    }
  }
}
const set = new Set();
for (const e of endpoints) set.add(e.method.toUpperCase() + ' ' + e.path);
const sorted = [...set].sort();
console.log('Total:', sorted.length);
sorted.forEach(s => console.log(' ', s));
