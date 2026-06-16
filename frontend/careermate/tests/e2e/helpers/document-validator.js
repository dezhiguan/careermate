// @ts-check
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * @param {string} filePath
 */
function validatePdfFile(filePath) {
  const result = {
    exists: false,
    size: 0,
    validHeader: false,
    text: '',
    markdownLike: false,
    issues: [],
  };

  if (!fs.existsSync(filePath)) {
    result.issues.push('文件不存在');
    return result;
  }

  result.exists = true;
  const buf = fs.readFileSync(filePath);
  result.size = buf.length;

  if (result.size === 0) {
    result.issues.push('文件大小为 0 字节');
    return result;
  }

  result.validHeader = buf.slice(0, 4).toString('ascii') === '%PDF';
  if (!result.validHeader) {
    result.issues.push('文件头不是 %PDF');
  }

  result.text = extractPdfText(buf, filePath);
  result.markdownLike = detectMarkdownArtifacts(result.text);
  if (result.markdownLike) {
    result.issues.push('PDF 文本含大量 Markdown 语法标记');
  }

  return result;
}

/**
 * @param {string} filePath
 */
function validateDocxFile(filePath) {
  const result = {
    exists: false,
    size: 0,
    hasDocumentXml: false,
    text: '',
    markdownLike: false,
    issues: [],
  };

  if (!fs.existsSync(filePath)) {
    result.issues.push('文件不存在');
    return result;
  }

  result.exists = true;
  const stat = fs.statSync(filePath);
  result.size = stat.size;
  if (result.size === 0) {
    result.issues.push('文件大小为 0 字节');
    return result;
  }

  const tmpDir = path.join(path.dirname(filePath), `_unzip_${Date.now()}`);
  try {
    fs.mkdirSync(tmpDir, { recursive: true });
    execSync(`unzip -q -o "${filePath}" -d "${tmpDir}"`, { stdio: 'pipe' });
    const docXml = path.join(tmpDir, 'word/document.xml');
    result.hasDocumentXml = fs.existsSync(docXml);
    if (!result.hasDocumentXml) {
      result.issues.push('docx 缺少 word/document.xml');
      return result;
    }
    const xml = fs.readFileSync(docXml, 'utf8');
    result.text = xml
      .replace(/<w:tab[^/]*\/>/g, '\t')
      .replace(/<\/w:p>/g, '\n')
      .replace(/<[^>]+>/g, '')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/\s+\n/g, '\n')
      .trim();
    result.markdownLike = detectMarkdownArtifacts(result.text);
    if (result.markdownLike) {
      result.issues.push('Word 正文含大量 Markdown 语法标记');
    }
  } catch (e) {
    result.issues.push(`解压/读取 docx 失败: ${e instanceof Error ? e.message : String(e)}`);
  } finally {
    try {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    } catch {
      // ignore
    }
  }

  return result;
}

/**
 * @param {Buffer} buf
 * @param {string} filePath
 */
function extractPdfText(buf, filePath) {
  try {
    const out = execSync(`python3 - <<'PY'
import re, sys
path = sys.argv[1]
data = open(path, 'rb').read()
try:
    import pypdf
    reader = pypdf.PdfReader(path)
    text = '\\n'.join((p.extract_text() or '') for p in reader.pages)
    print(text[:8000])
    sys.exit(0)
except Exception:
    pass
try:
    import PyPDF2
    reader = PyPDF2.PdfReader(path)
    text = '\\n'.join((p.extract_text() or '') for p in reader.pages)
    print(text[:8000])
    sys.exit(0)
except Exception:
    pass
# fallback: decode literal strings in PDF streams
raw = data.decode('latin-1', errors='ignore')
chunks = re.findall(r'\\((?:\\\\.|[^\\\\)]){4,}\\)', raw)
text = ' '.join(c.replace('\\\\n', ' ').replace('\\\\(', '(').replace('\\\\)', ')') for c in chunks[:400])
print(text[:8000])
PY
"${filePath}"`, { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
    if (out && out.trim().length > 20) return out.trim();
  } catch {
    // fall through
  }

  const raw = buf.toString('latin1');
  const chunks = raw.match(/\((?:\\.|[^\\)]){4,}\)/g) || [];
  return chunks
    .slice(0, 400)
    .map((c) => c.slice(1, -1).replace(/\\n/g, ' '))
    .join(' ')
    .slice(0, 8000);
}

/**
 * @param {string} text
 */
function detectMarkdownArtifacts(text) {
  if (!text || text.length < 30) return false;
  const lines = text.split('\n');
  let mdSignals = 0;
  for (const line of lines) {
    const t = line.trim();
    if (/^#{1,6}\s/.test(t)) mdSignals += 2;
    if (/^[-*+]\s/.test(t)) mdSignals += 1;
    if (/^```/.test(t)) mdSignals += 3;
    if (/\*\*[^*]+\*\*/.test(t)) mdSignals += 1;
  }
  const ratio = mdSignals / Math.max(lines.length, 1);
  return mdSignals >= 5 && ratio > 0.15;
}

/**
 * @param {string} text
 * @param {string[]} keywords
 */
function textContainsKeywords(text, keywords) {
  const lower = (text || '').toLowerCase();
  return keywords.filter((k) => lower.includes(k.toLowerCase()));
}

module.exports = {
  validatePdfFile,
  validateDocxFile,
  detectMarkdownArtifacts,
  textContainsKeywords,
};
