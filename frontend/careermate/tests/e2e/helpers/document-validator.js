// @ts-check
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

/**
 * @param {string} text
 * @param {number} [maxLen]
 */
function summarizeForReport(text, maxLen = 180) {
  if (!text) {
    return '结构校验通过（未抽取可读正文）';
  }
  const cleaned = text
    .replace(/[^\x09\x0A\x0D\x20-\x7E\u4e00-\u9fff]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (cleaned.length < 8) {
    return '结构校验通过（未抽取可读正文）';
  }
  const hasCjk = /[\u4e00-\u9fff]/.test(cleaned);
  const hasReadableWords = /\b[a-zA-Z]{4,}\b/.test(cleaned);
  if (!hasCjk && !hasReadableWords) {
    return '结构校验通过（未抽取可读正文）';
  }
  return cleaned.slice(0, maxLen);
}

/**
 * @param {string} filePath
 */
function validatePdfFile(filePath) {
  const result = {
    exists: false,
    size: 0,
    validHeader: false,
    text: '',
    textSummary: '',
    markdownLike: false,
    issues: [],
  };

  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved)) {
    result.issues.push('文件不存在');
    return result;
  }

  result.exists = true;
  const buf = fs.readFileSync(resolved);
  result.size = buf.length;

  if (result.size === 0) {
    result.issues.push('文件大小为 0 字节');
    return result;
  }

  result.validHeader = buf.slice(0, 4).toString('ascii') === '%PDF';
  if (!result.validHeader) {
    result.issues.push('文件头不是 %PDF');
  }

  result.text = extractPdfText(buf);
  result.textSummary = summarizeForReport(result.text);
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
    textSummary: '',
    markdownLike: false,
    issues: [],
  };

  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved)) {
    result.issues.push('文件不存在');
    return result;
  }

  result.exists = true;
  const stat = fs.statSync(resolved);
  result.size = stat.size;
  if (result.size === 0) {
    result.issues.push('文件大小为 0 字节');
    return result;
  }

  const tmpDir = path.join(path.dirname(resolved), `_unzip_${Date.now()}`);
  try {
    fs.mkdirSync(tmpDir, { recursive: true });
    execFileSync('unzip', ['-q', '-o', resolved, '-d', tmpDir], { stdio: 'pipe' });
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
    result.textSummary = summarizeForReport(result.text);
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
 */
function extractPdfText(buf) {
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
  summarizeForReport,
};
