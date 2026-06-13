package com.careermate.resume.version.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
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
 * 将 Markdown 经 CommonMark 解析后渲染为 PDF。
 * 支持标题、段落、有序/无序列表、加粗/斜体、行内代码、代码块、引用、分割线、链接与 GFM 表格；
 * 若内容无法解析出任何可渲染块，则回退为纯文本输出，避免产出空白文件。
 */
@Component
public class ResumeVersionPdfRenderer {

    private static final float MARGIN_LEFT_RIGHT = 54f;
    private static final float MARGIN_TOP_BOTTOM = 36f;
    private static final float BODY_SIZE = 11f;
    private static final float CODE_SIZE = 10f;
    private static final float H1_SIZE = 16f;
    private static final float H2_SIZE = 13f;
    private static final float H3_SIZE = 12f;
    private static final float LINE_MULTIPLIER = 1.5f;
    private static final Color HEADER_BG = new Color(0xF1, 0xF5, 0xF9);
    private static final Color CODE_COLOR = new Color(0x33, 0x41, 0x55);

    public void render(String markdown, OutputStream outputStream) throws Exception {
        String source = MarkdownExportSupport.stripWrappingFence(markdown);
        Node document = MarkdownExportSupport.parser().parse(source);

        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Fonts fonts = new Fonts(baseFont);

        Document pdf = new Document(PageSize.A4, MARGIN_LEFT_RIGHT, MARGIN_LEFT_RIGHT, MARGIN_TOP_BOTTOM, MARGIN_TOP_BOTTOM);
        PdfWriter.getInstance(pdf, outputStream);
        pdf.open();

        MarkdownPdfVisitor visitor = new MarkdownPdfVisitor(pdf, fonts);
        document.accept(visitor);

        if (!visitor.wroteSomething()) {
            renderPlainFallback(pdf, source, fonts.body);
        }

        pdf.close();
    }

    private void renderPlainFallback(Document pdf, String source, Font bodyFont) {
        String text = source == null ? "" : source.strip();
        com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
        paragraph.setLeading(0, LINE_MULTIPLIER);
        if (text.isEmpty()) {
            paragraph.add(new Chunk("（简历内容为空）", bodyFont));
        } else {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                paragraph.add(new Chunk(lines[i], bodyFont));
                if (i < lines.length - 1) {
                    paragraph.add(Chunk.NEWLINE);
                }
            }
        }
        try {
            pdf.add(paragraph);
        } catch (Exception e) {
            throw new RuntimeException("PDF 兜底文本写入失败", e);
        }
    }

    private static final class Fonts {
        private final Font body;
        private final Font bold;
        private final Font italic;
        private final Font code;
        private final Font h1;
        private final Font h2;
        private final Font h3;

        private Fonts(BaseFont baseFont) {
            this.body = new Font(baseFont, BODY_SIZE, Font.NORMAL);
            this.bold = new Font(baseFont, BODY_SIZE, Font.BOLD);
            this.italic = new Font(baseFont, BODY_SIZE, Font.ITALIC);
            this.code = new Font(baseFont, CODE_SIZE, Font.NORMAL, CODE_COLOR);
            this.h1 = new Font(baseFont, H1_SIZE, Font.BOLD);
            this.h2 = new Font(baseFont, H2_SIZE, Font.BOLD);
            this.h3 = new Font(baseFont, H3_SIZE, Font.BOLD);
        }
    }

    private static final class MarkdownPdfVisitor extends AbstractVisitor {

        private final Document pdf;
        private final Fonts fonts;
        private boolean wroteSomething = false;

        private MarkdownPdfVisitor(Document pdf, Fonts fonts) {
            this.pdf = pdf;
            this.fonts = fonts;
        }

        private boolean wroteSomething() {
            return wroteSomething;
        }

        @Override
        public void visit(Heading heading) {
            Font font = switch (heading.getLevel()) {
                case 1 -> fonts.h1;
                case 2 -> fonts.h2;
                default -> fonts.h3;
            };
            com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            appendInlines(heading, paragraph, font, font);
            paragraph.setSpacingBefore(8f);
            paragraph.setSpacingAfter(6f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraph) {
            com.lowagie.text.Paragraph pdfParagraph = new com.lowagie.text.Paragraph();
            pdfParagraph.setLeading(0, LINE_MULTIPLIER);
            appendInlines(paragraph, pdfParagraph, fonts.body, fonts.bold);
            pdfParagraph.setSpacingAfter(4f);
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
            com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            paragraph.setIndentationLeft(14f);
            paragraph.add(new Chunk(marker, fonts.body));
            appendInlines(listItem, paragraph, fonts.body, fonts.bold);
            paragraph.setSpacingAfter(2f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock.getLiteral());
        }

        private void renderCodeBlock(String literal) {
            String content = literal == null ? "" : literal.replaceAll("\n$", "");
            com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
            paragraph.setLeading(0, LINE_MULTIPLIER);
            paragraph.setIndentationLeft(10f);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                paragraph.add(new Chunk(lines[i], fonts.code));
                if (i < lines.length - 1) {
                    paragraph.add(Chunk.NEWLINE);
                }
            }
            paragraph.setSpacingBefore(4f);
            paragraph.setSpacingAfter(6f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            visitChildren(blockQuote);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
            paragraph.add(new Chunk(new LineSeparator()));
            paragraph.setSpacingBefore(6f);
            paragraph.setSpacingAfter(6f);
            addParagraph(paragraph);
        }

        @Override
        public void visit(org.commonmark.node.CustomBlock customBlock) {
            if (customBlock instanceof TableBlock tableBlock) {
                renderTable(tableBlock);
            } else {
                visitChildren(customBlock);
            }
        }

        private void renderTable(TableBlock tableBlock) {
            List<List<TableCell>> rows = collectRows(tableBlock);
            if (rows.isEmpty()) {
                return;
            }
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            if (columns == 0) {
                return;
            }
            PdfPTable table = new PdfPTable(columns);
            table.setWidthPercentage(100f);
            table.setSpacingBefore(4f);
            table.setSpacingAfter(8f);
            for (List<TableCell> row : rows) {
                for (int c = 0; c < columns; c++) {
                    PdfPCell pdfCell = new PdfPCell();
                    pdfCell.setPadding(4f);
                    com.lowagie.text.Paragraph cellParagraph = new com.lowagie.text.Paragraph();
                    cellParagraph.setLeading(0, LINE_MULTIPLIER);
                    if (c < row.size()) {
                        TableCell cell = row.get(c);
                        boolean header = cell.isHeader();
                        appendInlines(cell, cellParagraph, header ? fonts.bold : fonts.body, fonts.bold);
                        if (header) {
                            pdfCell.setBackgroundColor(HEADER_BG);
                        }
                    }
                    pdfCell.addElement(cellParagraph);
                    table.addCell(pdfCell);
                }
            }
            try {
                pdf.add(table);
                wroteSomething = true;
            } catch (Exception e) {
                throw new RuntimeException("PDF 表格写入失败", e);
            }
        }

        private List<List<TableCell>> collectRows(TableBlock tableBlock) {
            List<List<TableCell>> rows = new ArrayList<>();
            for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
                if (section instanceof TableHead || section instanceof TableBody) {
                    for (Node rowNode = section.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                        if (rowNode instanceof TableRow tableRow) {
                            List<TableCell> cells = new ArrayList<>();
                            for (Node cellNode = tableRow.getFirstChild(); cellNode != null; cellNode = cellNode.getNext()) {
                                if (cellNode instanceof TableCell tableCell) {
                                    cells.add(tableCell);
                                }
                            }
                            rows.add(cells);
                        }
                    }
                }
            }
            return rows;
        }

        private void appendInlines(Node parent, com.lowagie.text.Paragraph paragraph, Font normalFont, Font boldFont) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    paragraph.add(new Chunk(textNode.getLiteral(), normalFont));
                } else if (child instanceof StrongEmphasis emphasis) {
                    appendInlines(emphasis, paragraph, boldFont, boldFont);
                } else if (child instanceof org.commonmark.node.Emphasis emphasis) {
                    appendInlines(emphasis, paragraph, fonts.italic, boldFont);
                } else if (child instanceof Code code) {
                    paragraph.add(new Chunk(code.getLiteral(), fonts.code));
                } else if (child instanceof Link link) {
                    int before = paragraph.size();
                    appendInlines(link, paragraph, normalFont, boldFont);
                    String destination = link.getDestination();
                    if (destination != null && !destination.isBlank()) {
                        if (paragraph.size() == before) {
                            paragraph.add(new Chunk(destination, normalFont));
                        } else {
                            paragraph.add(new Chunk(" (" + destination + ")", fonts.code));
                        }
                    }
                } else if (child instanceof Image image) {
                    int before = paragraph.size();
                    appendInlines(image, paragraph, normalFont, boldFont);
                    if (paragraph.size() == before && image.getDestination() != null) {
                        paragraph.add(new Chunk(image.getDestination(), fonts.code));
                    }
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    paragraph.add(Chunk.NEWLINE);
                } else if (child instanceof org.commonmark.node.Paragraph nested) {
                    appendInlines(nested, paragraph, normalFont, boldFont);
                } else {
                    appendInlines(child, paragraph, normalFont, boldFont);
                }
            }
        }

        private void addParagraph(com.lowagie.text.Paragraph paragraph) {
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
