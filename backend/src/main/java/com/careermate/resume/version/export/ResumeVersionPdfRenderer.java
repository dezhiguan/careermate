package com.careermate.resume.version.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 Markdown 简历渲染为中文技术简历风格 PDF（A4）。
 * AI 版本与「我的简历」导出均经本类，改此处即可统一版式。
 */
@Component
public class ResumeVersionPdfRenderer {

    private static final float MARGIN = 40f;
    private static final float NAME_SIZE = 20f;
    private static final float CONTACT_SIZE = 9f;
    private static final float SECTION_TITLE_SIZE = 11f;
    private static final float BODY_SIZE = 10.5f;
    private static final float LINE_MULTIPLIER = 1.25f;
    private static final Color CONTACT_COLOR = new Color(0x6B, 0x72, 0x80);
    private static final Color SECTION_LINE_COLOR = new Color(0xD1, 0xD5, 0xDB);

    public void render(String markdown, OutputStream outputStream) throws Exception {
        String source = MarkdownExportSupport.stripOptimizationMetaFromMarkdown(markdown);
        ResumeStructureParser.ResumeStructure structure = ResumeStructureParser.parse(source);

        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Fonts fonts = new Fonts(baseFont);

        Document pdf = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter.getInstance(pdf, outputStream);
        pdf.open();

        renderHeader(pdf, structure, fonts);

        if (structure.sections().isEmpty()) {
            renderPlainFallback(pdf, source, fonts.body);
        } else {
            for (ResumeStructureParser.ResumeSection section : structure.sections()) {
                if (section.title() != null && !section.title().isBlank()) {
                    renderSectionTitle(pdf, section.title(), fonts);
                }
                String content = section.contentMarkdown();
                if (content != null && !content.isBlank()) {
                    SectionContentVisitor visitor = new SectionContentVisitor(pdf, fonts);
                    Node document = MarkdownExportSupport.parser().parse(content);
                    document.accept(visitor);
                }
            }
        }

        pdf.close();
    }

    private void renderHeader(Document pdf, ResumeStructureParser.ResumeStructure structure, Fonts fonts) throws Exception {
        Paragraph nameParagraph = new Paragraph(structure.name(), fonts.name);
        nameParagraph.setAlignment(Element.ALIGN_LEFT);
        nameParagraph.setSpacingAfter(4f);
        pdf.add(nameParagraph);

        if (!structure.contactParts().isEmpty()) {
            String contactLine = String.join(" / ", structure.contactParts());
            Paragraph contactParagraph = new Paragraph(contactLine, fonts.contact);
            contactParagraph.setAlignment(Element.ALIGN_LEFT);
            contactParagraph.setSpacingAfter(10f);
            pdf.add(contactParagraph);
        } else {
            nameParagraph.setSpacingAfter(10f);
        }
    }

    private void renderSectionTitle(Document pdf, String title, Fonts fonts) throws Exception {
        Paragraph titleParagraph = new Paragraph(title, fonts.sectionTitle);
        titleParagraph.setSpacingBefore(6f);
        titleParagraph.setSpacingAfter(2f);
        pdf.add(titleParagraph);

        Paragraph line = new Paragraph();
        LineSeparator separator = new LineSeparator(0.5f, 100f, SECTION_LINE_COLOR, Element.ALIGN_LEFT, -2f);
        line.add(new Chunk(separator));
        line.setSpacingAfter(4f);
        pdf.add(line);
    }

    private void renderPlainFallback(Document pdf, String source, Font bodyFont) throws Exception {
        String text = source == null ? "" : source.strip();
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(0, LINE_MULTIPLIER);
        if (text.isEmpty()) {
            paragraph.add(new Chunk("（简历内容为空）", bodyFont));
        } else {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                paragraph.add(new Chunk(stripMarkdownLine(lines[i]), bodyFont));
                if (i < lines.length - 1) {
                    paragraph.add(Chunk.NEWLINE);
                }
            }
        }
        paragraph.setSpacingAfter(2f);
        pdf.add(paragraph);
    }

    private static String stripMarkdownLine(String line) {
        if (line == null) {
            return "";
        }
        return line.strip()
                .replaceAll("^#{1,6}\\s*", "")
                .replaceAll("^[-*+]\\s+", "")
                .replaceAll("^\\d+\\.\\s+", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1");
    }

    private static final class Fonts {
        private final Font name;
        private final Font contact;
        private final Font sectionTitle;
        private final Font body;
        private final Font bold;

        private Fonts(BaseFont baseFont) {
            this.name = new Font(baseFont, NAME_SIZE, Font.BOLD);
            this.contact = new Font(baseFont, CONTACT_SIZE, Font.NORMAL, CONTACT_COLOR);
            this.sectionTitle = new Font(baseFont, SECTION_TITLE_SIZE, Font.BOLD);
            this.body = new Font(baseFont, BODY_SIZE, Font.NORMAL);
            this.bold = new Font(baseFont, BODY_SIZE, Font.BOLD);
        }
    }

    private static final class SectionContentVisitor extends AbstractVisitor {

        private final Document pdf;
        private final Fonts fonts;
        private boolean wroteSomething = false;

        private SectionContentVisitor(Document pdf, Fonts fonts) {
            this.pdf = pdf;
            this.fonts = fonts;
        }

        private boolean wroteSomething() {
            return wroteSomething;
        }

        @Override
        public void visit(Heading heading) {
            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            appendInlines(heading, paragraph, fonts.bold, fonts.bold);
            paragraph.setSpacingAfter(2f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraph) {
            Paragraph pdfParagraph = new Paragraph();
            pdfParagraph.setLeading(0, LINE_MULTIPLIER);
            appendInlines(paragraph, pdfParagraph, fonts.body, fonts.bold);
            pdfParagraph.setSpacingAfter(3f);
            addParagraph(pdfParagraph);
        }

        @Override
        public void visit(BulletList bulletList) {
            for (Node item = bulletList.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem listItem) {
                    renderListItem(listItem, "• ");
                }
            }
        }

        @Override
        public void visit(OrderedList orderedList) {
            int index = orderedList.getMarkerStartNumber() != null ? orderedList.getMarkerStartNumber() : 1;
            for (Node item = orderedList.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem listItem) {
                    renderListItem(listItem, index + ". ");
                    index++;
                }
            }
        }

        private void renderListItem(ListItem listItem, String marker) {
            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            paragraph.setIndentationLeft(12f);
            paragraph.add(new Chunk(marker, fonts.body));
            appendInlines(listItem, paragraph, fonts.body, fonts.bold);
            paragraph.setSpacingAfter(1.5f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            if ("meta".equalsIgnoreCase(codeBlock.getInfo())) {
                return;
            }
            renderPlainTextBlock(codeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            renderPlainTextBlock(codeBlock.getLiteral());
        }

        private void renderPlainTextBlock(String literal) {
            String content = literal == null ? "" : literal.replaceAll("\n$", "").strip();
            if (content.isBlank()) {
                return;
            }
            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            paragraph.add(new Chunk(content, fonts.body));
            paragraph.setSpacingAfter(3f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            visitChildren(blockQuote);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            // 简历版式忽略 Markdown 分割线
        }

        @Override
        public void visit(org.commonmark.node.CustomBlock customBlock) {
            if (customBlock instanceof TableBlock tableBlock) {
                renderTableAsText(tableBlock);
            } else {
                visitChildren(customBlock);
            }
        }

        private void renderTableAsText(TableBlock tableBlock) {
            List<List<String>> rows = collectRowTexts(tableBlock);
            if (rows.isEmpty()) {
                return;
            }
            List<String> headers = rows.get(0);
            for (int r = 1; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                String line = formatTableRow(headers, row);
                if (!line.isBlank()) {
                    Paragraph paragraph = new Paragraph();
                    paragraph.setLeading(0, LINE_MULTIPLIER);
                    paragraph.setIndentationLeft(8f);
                    paragraph.add(new Chunk("• ", fonts.body));
                    paragraph.add(new Chunk(line, fonts.body));
                    paragraph.setSpacingAfter(1.5f);
                    addParagraph(paragraph);
                }
            }
            if (rows.size() == 1) {
                String line = String.join(" / ", headers);
                Paragraph paragraph = new Paragraph(line, fonts.body);
                paragraph.setSpacingAfter(3f);
                addParagraph(paragraph);
            }
        }

        private static String formatTableRow(List<String> headers, List<String> cells) {
            List<String> parts = new ArrayList<>();
            int columns = Math.max(headers.size(), cells.size());
            for (int i = 0; i < columns; i++) {
                String header = i < headers.size() ? headers.get(i).strip() : "";
                String value = i < cells.size() ? cells.get(i).strip() : "";
                if (value.isBlank()) {
                    continue;
                }
                if (!header.isBlank() && !header.matches("-+")) {
                    parts.add(header + ": " + value);
                } else {
                    parts.add(value);
                }
            }
            return String.join("    ", parts);
        }

        private List<List<String>> collectRowTexts(TableBlock tableBlock) {
            List<List<String>> rows = new ArrayList<>();
            for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
                if (section instanceof TableHead || section instanceof TableBody) {
                    for (Node rowNode = section.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                        if (rowNode instanceof TableRow tableRow) {
                            List<String> cells = new ArrayList<>();
                            for (Node cellNode = tableRow.getFirstChild(); cellNode != null; cellNode = cellNode.getNext()) {
                                if (cellNode instanceof TableCell tableCell) {
                                    cells.add(extractPlainText(tableCell));
                                }
                            }
                            rows.add(cells);
                        }
                    }
                }
            }
            return rows;
        }

        private String extractPlainText(Node node) {
            StringBuilder sb = new StringBuilder();
            appendPlainText(node, sb);
            return sb.toString().strip();
        }

        private void appendPlainText(Node node, StringBuilder sb) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    sb.append(textNode.getLiteral());
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    sb.append(' ');
                } else {
                    appendPlainText(child, sb);
                }
            }
        }

        private void appendInlines(Node parent, Paragraph paragraph, Font normalFont, Font boldFont) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    paragraph.add(new Chunk(textNode.getLiteral(), normalFont));
                } else if (child instanceof StrongEmphasis emphasis) {
                    appendInlines(emphasis, paragraph, boldFont, boldFont);
                } else if (child instanceof org.commonmark.node.Emphasis emphasis) {
                    appendInlines(emphasis, paragraph, normalFont, boldFont);
                } else if (child instanceof Code code) {
                    paragraph.add(new Chunk(code.getLiteral(), normalFont));
                } else if (child instanceof Link link) {
                    int before = paragraph.size();
                    appendInlines(link, paragraph, normalFont, boldFont);
                    if (paragraph.size() == before && link.getDestination() != null) {
                        paragraph.add(new Chunk(link.getDestination(), normalFont));
                    }
                } else if (child instanceof Image image) {
                    int before = paragraph.size();
                    appendInlines(image, paragraph, normalFont, boldFont);
                    if (paragraph.size() == before && image.getDestination() != null) {
                        paragraph.add(new Chunk(image.getDestination(), normalFont));
                    }
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    paragraph.add(Chunk.NEWLINE);
                } else if (child instanceof org.commonmark.node.Paragraph nested) {
                    appendInlines(nested, paragraph, normalFont, boldFont);
                } else if (child instanceof ListItem listItem) {
                    appendInlines(listItem, paragraph, normalFont, boldFont);
                } else {
                    appendInlines(child, paragraph, normalFont, boldFont);
                }
            }
        }

        private void addParagraph(Paragraph paragraph) {
            try {
                if (paragraph.isEmpty()) {
                    return;
                }
                paragraph.setAlignment(Element.ALIGN_LEFT);
                pdf.add(paragraph);
                wroteSomething = true;
            } catch (Exception e) {
                throw new RuntimeException("PDF 段落写入失败", e);
            }
        }
    }
}
