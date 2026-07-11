package com.careermate.resume.service;

import com.careermate.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class ResumeFileParserService {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "md", "markdown");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final int MAX_CONTENT_CHARS = 50_000;

    private final Parser parser = new AutoDetectParser();

    /**
     * 校验并解析文件，返回提取的纯文本（最多 50000 字符）。
     */
    public String parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(400, "文件大小不能超过 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException(400, "文件名不能为空");
        }
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(400, "只支持 PDF、Word（doc/docx）、Markdown 格式");
        }

        BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_CHARS);
        try (InputStream is = file.getInputStream()) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            parser.parse(is, handler, metadata, context);
            String text = handler.toString().trim();
            if (text.isBlank()) {
                throw new BizException(400, "文件内容为空或无法提取文字（可能是扫描件）");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 文本超过 MAX_CONTENT_CHARS 时 Tika 抛 WriteLimitReachedException（包在 SAXException 内），
            // 此时 handler 已累积到上限的正文，按“已截断”正常返回，而非整份拒绝。
            if (isWriteLimitReached(e)) {
                String partial = handler.toString().trim();
                if (!partial.isBlank()) {
                    log.info("简历文本超过 {} 字符，已自动截断: filename={}", MAX_CONTENT_CHARS, originalFilename);
                    return partial;
                }
            }
            // 不向用户暴露底层解析器的内部异常信息
            log.warn("简历文件解析失败: filename={} err={}", originalFilename, e.getMessage());
            throw new BizException(400, "文件解析失败，请确认文件是有效且未加密的 PDF、Word 或 Markdown");
        }
    }

    /** 判断异常链中是否为 Tika 的“写入字符数超限”（超长文档，可按截断处理）。 */
    private boolean isWriteLimitReached(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getClass().getName().contains("WriteLimitReachedException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.contains("more than") && msg.contains("characters")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
