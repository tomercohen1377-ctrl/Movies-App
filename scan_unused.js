const fs = require('fs');
const path = require('path');

const root = path.join(process.cwd(), 'app', 'src');
const sourceRoots = ['main/java', 'test/java', 'androidTest/java']
  .map(p => path.join(root, p))
  .filter(fs.existsSync);

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (entry.isFile() && full.endsWith('.kt') && !full.includes(path.sep + 'build' + path.sep)) out.push(full);
  }
  return out;
}

const files = sourceRoots.flatMap(walk);
const texts = Object.fromEntries(files.map(f => [f, fs.readFileSync(f, 'utf8')]));

const isSkippedFile = (file) => {
  const rel = path.relative(root, file).replace(/\\/g, '/');
  const base = path.basename(file);
  if (base === 'MainActivity.kt' || base === 'MoviesApplication.kt') return true;
  if (rel.includes('/ai/')) return true;
  const txt = texts[file];
  if (/@Module\b|@InstallIn\b|@HiltViewModel\b|@AndroidEntryPoint\b|@HiltAndroidApp\b|@Provides\b|@Binds\b|@Inject\s+constructor/.test(txt)) return true;
  return false;
};

function stripComments(line) {
  return line.replace(/\/\/.*$/, '').trim();
}

function parseDecl(line) {
  const s = line;
  const prefix = '(?:@[A-Za-z_][\\w.]*?(?:\\([^)]*\\))?\\s*)*';
  let m;
  if ((m = s.match(new RegExp('^' + prefix + '(?:public|private|internal|protected|final|abstract|open|sealed|data|inner|override|const|lateinit|expect|actual|suspend|tailrec|operator|infix|inline|external)?\\s*(class|object|interface|enum\\s+class|annotation\\s+class)\\s+([A-Za-z_][\\w]*)')))) {
    return { kind: m[1], name: m[2] };
  }
  if ((m = s.match(new RegExp('^' + prefix + '(?:public|private|internal|protected|final|abstract|open|override|suspend|tailrec|operator|infix|inline|external|expect|actual)?\\s*fun\\s+(?:<[^>]+>\\s*)?(?:[A-Za-z_][\\w<>.?\\[\\],\\s]*\\.)*([A-Za-z_][\\w]*)\\s*\\(')))) {
    return { kind: 'fun', name: m[1] };
  }
  if ((m = s.match(new RegExp('^' + prefix + '(?:public|private|internal|protected|const|lateinit|override|final|open)?\\s*const\\s+val\\s+([A-Za-z_][\\w]*)\\b')))) {
    return { kind: 'const', name: m[1] };
  }
  if ((m = s.match(new RegExp('^' + prefix + '(?:public|private|internal|protected|override|final|open)?\\s*val\\s+([A-Z][A-Z0-9_]*)\\b')))) {
    return { kind: 'val', name: m[1] };
  }
  return null;
}

function declarations(file, topLevelOnly) {
  const out = [];
  let depth = 0;
  const lines = texts[file].split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    const line = stripComments(raw);
    if (line) {
      const shouldParse = !topLevelOnly || depth === 0;
      if (shouldParse) {
        const decl = parseDecl(line);
        if (decl) out.push({ ...decl, line: i + 1 });
      }
    }
    const open = (raw.match(/\{/g) || []).length;
    const close = (raw.match(/\}/g) || []).length;
    depth += open - close;
    if (depth < 0) depth = 0;
  }
  return out;
}

const topDeclsByFile = Object.fromEntries(files.map(f => [f, declarations(f, true)]));
const allDeclsByFile = Object.fromEntries(files.map(f => [f, declarations(f, false)]));

function regexEscape(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function referenceCount(name, kind) {
  const pat = new RegExp('\\b' + regexEscape(name) + '\\b', 'g');
  let total = 0;
  for (const file of files) {
    const matches = texts[file].match(pat);
    if (matches) total += matches.length;
  }
  return total;
}

const deadFiles = [];
const deadFileSet = new Set();
for (const file of files) {
  if (isSkippedFile(file)) continue;
  const decls = topDeclsByFile[file];
  if (decls.length > 0 && decls.every(d => referenceCount(d.name, d.kind) === 1)) {
    deadFiles.push({ file: path.relative(root, file).replace(/\\/g, '/'), reason: 'all top-level declarations appear only in their own definitions' });
    deadFileSet.add(file);
  }
}

const deadSymbols = [];
for (const file of files) {
  if (isSkippedFile(file) || deadFileSet.has(file)) continue;
  for (const d of allDeclsByFile[file]) {
    if (referenceCount(d.name, d.kind) === 1) {
      deadSymbols.push({ file: path.relative(root, file).replace(/\\/g, '/'), line: d.line, kind: d.kind, name: d.name, reason: 'only occurrence appears to be its declaration' });
    }
  }
}

console.log(JSON.stringify({ fileCount: files.length, deadFiles, deadSymbols }, null, 2));
