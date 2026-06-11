package com.careermate.resume.version.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

/**
 * 将 Markdown 经 CommonMark 解析后渲染为 PDF。
 */
@Component
public class ResumeVersionPdfRenderer {

    private static final float MARGIN_LEFT_RIGHT = 54f;
    private static final float MARGIN_TOP_BOTTOM = 36f;
    private static final float BODY_SIZE = 11f;
    private static final float H1_SIZE = 16f;
    private static final float H2_SIZE = 13f;
    private static final float LINE_MULTIPLIER = 1.5f;

    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public void render(String markdown, OutputStream outputStream) throws Exception {
        if (markdown == null) {
            markdown = "";
        }
        Node document = markdownParser.parse(markdown);
        // CommonMark → HTML（满足「解析 HTML 片段」流程，PDF 排版走 AST 访问器）
        htmlRenderer.render(document);

        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font bodyFont = new Font(baseFont, BODY_SIZE, Font.NORMAL);
        Font bodyBoldFont = new Font(baseFont, BODY_SIZE, Font.BOLD);
        Font h1Font = new Font(baseFont, H1_SIZE, Font.BOLD);
        Font h2Font = new Font(baseFont, H2_SIZE, Font.BOLD);

        Document pdf = new Document(PageSize.A4, MARGIN_LEFT_RIGHT, MARGIN_LEFT_RIGHT, MARGIN_TOP_BOTTOM, MARGIN_TOP_BOTTOM);
        PdfWriter.getInstance(pdf, outputStream);
        pdf.open();

        MarkdownPdfVisitor visitor = new MarkdownPdfVisitor(pdf, bodyFont, bodyBoldFont, h1Font, h2Font);
        document.accept(visitor);
        pdf.close();
    }

    private static final class MarkdownPdfVisitor extends AbstractVisitor {

        private final Document pdf;
        private final Font bodyFont;
        private final Font bodyBoldFont;
        private final Font h1Font;
        private final Font h2Font;

        private MarkdownPdfVisitor(Document pdf, Font bodyFont, Font bodyBoldFont, Font h1Font, Font h2Font) {
            this.pdf = pdf;
            this.bodyFont = bodyFont;
            this.bodyBoldFont = bodyBoldFont;
            this.h1Font = h1Font;
            this.h2Font = h2Font;
        }

        @Override
        public void visit(Heading heading) {
            Font font = heading.getLevel() <= 1 ? h1Font : h2Font;
            com.lowagie.text.Paragraph paragraph = buildParagraph(heading, font, font);
            paragraph.setSpacingBefore(8f);
            paragraph.setSpacingAfter(6f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraph) {
            com.lowagie.text.Paragraph pdfParagraph = buildParagraph(paragraph, bodyFont, bodyBoldFont);
            pdfParagraph.setSpacingAfter(4f);
            addParagraph(pdfParagraph);
        }

        @Override
        public void visit(BulletList bulletList) {
            visitChildren(bulletList);
        }

        @Override
        public void visit(ListItem listItem) {
            com.lowagie.text.Paragraph pdfParagraph = buildParagraph(listItem, bodyFont, bodyBoldFont);
            pdfParagraph.setIndentationLeft(12f);
            pdfParagraph.add(0, new Chunk("• ", bodyFont));
            pdfParagraph.setSpacingAfter(2f);
            addParagraph(pdfParagraph);
        }

        private com.lowagie.text.Paragraph buildParagraph(Node node, Font normalFont, Font boldFont) {
            com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            appendInlines(node, paragraph, normalFont, boldFont);
            return paragraph;
        }

        private void appendInlines(Node parent, com.lowagie.text.Paragraph paragraph, Font normalFont, Font boldFont) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    paragraph.add(new Chunk(textNode.getLiteral(), normalFont));
                } else if (child instanceof StrongEmphasis emphasis) {
                    appendInlines(emphasis, paragraph, boldFont, boldFont);
                } else if (child instanceof SoftLineBreak) {
                    paragraph.add(Chunk.NEWLINE);
                } else if (child instanceof org.commonmark.node.Paragraph nested) {
                    appendInlines(nested, paragraph, normalFont, boldFont);
                } else {
                    appendInlines(child, paragraph, normalFont, boldFont);
                }
            }
        }

        private void addParagraph(com.lowagie.text.Paragraph paragraph) throws RuntimeException {
            try {
                if (paragraph.isEmpty()) {
                    return;
                }
                pdf.add(paragraph);
            } catch (Exception e) {
                throw new RuntimeException("PDF 段落写入失败", e);
            }
        }
    }
}
