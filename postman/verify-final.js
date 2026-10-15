// Verify collection URLs against code paths
const fs = require('fs');
const cp = require('child_process');

// Get all code endpoints with placeholder names normalized
const out = cp.execSync('node extract-paths-v3.js', { encoding: 'utf8' });
const codeEndpoints = new Map(); // key: "METHOD path" -> path with {{var}}
const lines = out.split('\n');
for (const l of lines) {
  const m = l.match(/^\s*(GET|POST|PUT|DELETE|PATCH)\s+(\/api\/v1\/\S+)/);
  if (m) codeEndpoints.set(m[1] + ' ' + m[2], m[2]);
}

// Parse collection
const col = JSON.parse(fs.readFileSync('d:/flash-sale-b2c-UTC2/postman/Flash-Sale-B2C.postman_collection.json', 'utf8'));
const colEndpoints = new Map();

function normalizePath(path) {
  // Replace {{var}} with {id} generic
  return path.replace(/\{\{(\w+)\}\}/g, (_, v) => {
    if (v === 'orderCode') return '{orderCode}';
    if (v === 'reservationId') return '{reservationId}';
    if (v === 'variantId') return '{variantId}';
    return '{id}';
  });
}

function walk(items) {
  for (const i of items) {
    if (i.item) walk(i.item);
    else {
      const m = i.request.method;
      let raw = i.request.url.raw.split('?')[0].replace('{{baseUrl}}', '');
      const normalized = normalizePath(raw);
      colEndpoints.set(m + ' ' + normalized, raw);
    }
  }
}
walk(col.item);

console.log('=== Verification Report ===');
console.log('Code endpoints:', codeEndpoints.size);
console.log('Col endpoints:', colEndpoints.size);
console.log();

let match = 0, mismatch = 0, missing = 0, extra = 0;
const issues = [];

for (const [key, codePath] of codeEndpoints) {
  if (colEndpoints.has(key)) {
    match++;
  } else {
    missing++;
    issues.push('MISSING in collection: ' + key);
  }
}

for (const [key, colPath] of colEndpoints) {
  if (!codeEndpoints.has(key)) {
    extra++;
    issues.push('EXTRA in collection (not in code): ' + key + ' (raw: ' + colPath + ')');
  }
}

console.log('Matched:', match);
console.log('Missing in collection:', missing);
console.log('Extra in collection (not in code):', extra);
console.log();
console.log('=== ISSUES ===');
issues.forEach(i => console.log(' ', i));
