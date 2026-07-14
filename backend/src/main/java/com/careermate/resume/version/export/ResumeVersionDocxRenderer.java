package com.careermate.resume.version.export;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 Markdown 经 CommonMark 解析后渲染为 Word(.docx)。
 * 支持标题、段落、有序/无序列表、加粗/斜体、行内代码、代码块、引用、分割线、链接与 GFM 表格；
 * 若内容无法解析出任何可渲染块，则回退为纯文本输出，避免产出空白文件。
 */
@Component
public class ResumeVersionDocxRenderer {

    // P3：版式参数取自 Word · ATS 友好版预设，改版式改 ResumeLayoutProfile 即可
    private static final ResumeLayoutProfile.DocxLayout LAYOUT = ResumeLayoutProfile.WORD_ATS;
    private static final int BODY_SIZE = LAYOUT.bodySize();
    private static final int CODE_SIZE = LAYOUT.codeSize();
    private static final int H1_SIZE = LAYOUT.h1Size();
    private static final int H2_SIZE = LAYOUT.h2Size();
    private static final int H3_SIZE = LAYOUT.h3Size();
    private static final String CODE_FONT = LAYOUT.codeFont();
    private static final String CJK_FONT = "宋体";
    private static final String HEADER_FILL = LAYOUT.headerFillHex();

    public void render(String markdown, OutputStream outputStream) throws Exception {
        String source = MarkdownExportSupport.stripOptimizationMetaFromMarkdown(markdown);
        Node document = MarkdownExportSupport.parser().parse(source);
        try (XWPFDocument docx = new XWPFDocument()) {
            MarkdownDocxVisitor visitor = new MarkdownDocxVisitor(docx);
            document.accept(visitor);
            if (!visitor.wroteSomething()) {
                renderPlainFallback(docx, source);
            }
            applyCjkFont(docx);
            docx.write(outputStream);
        }
    }

    /**
     * 为文档内所有 run（正文/标题/列表/表格/代码块）设置东亚(中文)字体，
     * 使中文在 headless(如 LibreOffice) 或缺省字体环境下也能稳定渲染，
     * 并保证代码块中的中文注释不落到无中文字形的 Consolas 上（拉丁字体保持不变）。
     */
    private void applyCjkFont(XWPFDocument docx) {
        for (XWPFParagraph paragraph : docx.getParagraphs()) {
            applyCjkFont(paragraph);
        }
        for (XWPFTable table : docx.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        applyCjkFont(paragraph);
                    }
                }
            }
        }
    }

    private void applyCjkFont(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.eastAsia);
        }
    }

    private void renderPlainFallback(XWPFDocument docx, String source) {
        String text = source == null ? "" : source.strip();
        XWPFParagraph paragraph = docx.createParagraph();
        if (text.isEmpty()) {
            XWPFRun run = paragraph.createRun();
            run.setFontSize(BODY_SIZE);
            run.setText("（简历内容为空）");
            return;
        }
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setFontSize(BODY_SIZE);
            run.setText(lines[i]);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private static final class MarkdownDocxVisitor extends AbstractVisitor {

        private final XWPFDocument docx;
        private boolean wroteSomething = false;

        private MarkdownDocxVisitor(XWPFDocument docx) {
            this.docx = docx;
        }

        private boolean wroteSomething() {
            return wroteSomething;
        }

        @Override
        public void visit(Heading heading) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setSpacingBefore(160);
            paragraph.setSpacingAfter(120);
            paragraph.setAlignment(heading.getLevel() <= 1 ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
            int size = switch (heading.getLevel()) {
                case 1 -> H1_SIZE;
                case 2 -> H2_SIZE;
                default -> H3_SIZE;
            };
            appendInlines(heading, paragraph, true, false, size);
            wroteSomething = true;
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraphNode) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setSpacingAfter(80);
            appendInlines(paragraphNode, paragraph, false, false, BODY_SIZE);
            wroteSomething = true;
        }

        @Override
        public void visit(org.commonmark.node.HtmlBlock htmlBlock) {
            // BUG-16：块级 HTML 剥标签后按正文渲染，避免内容整段丢失（与 PDF 一致）。
            String text = ResumeVersionPdfRenderer.stripHtmlTags(htmlBlock.getLiteral());
            if (text.isBlank()) {
                return;
            }
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setSpacingAfter(80);
            XWPFRun run = paragraph.createRun();
            run.setFontSize(BODY_SIZE);
            run.setText(text);
            wroteSomething = true;
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
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setIndentationLeft(240);
            paragraph.setSpacingAfter(40);
            XWPFRun bullet = paragraph.createRun();
            bullet.setFontSize(BODY_SIZE);
            bullet.setText(marker);
            appendInlines(listItem, paragraph, false, false, BODY_SIZE);
            wroteSomething = true;
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
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setIndentationLeft(200);
            paragraph.setSpacingBefore(40);
            paragraph.setSpacingAfter(80);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                XWPFRun run = paragraph.createRun();
                run.setFontFamily(CODE_FONT);
                run.setFontSize(CODE_SIZE);
                run.setText(lines[i]);
                if (i < lines.length - 1) {
                    run.addBreak();
                }
            }
            wroteSomething = true;
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            visitChildren(blockQuote);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setBorderBottom(Borders.SINGLE);
            wroteSomething = true;
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
            XWPFTable table = docx.createTable(rows.size(), columns);
            for (int r = 0; r < rows.size(); r++) {
                List<TableCell> cells = rows.get(r);
                for (int c = 0; c < columns; c++) {
                    XWPFTableCell xwpfCell = table.getRow(r).getCell(c);
                    XWPFParagraph paragraph = xwpfCell.getParagraphs().isEmpty()
                            ? xwpfCell.addParagraph()
                            : xwpfCell.getParagraphs().get(0);
                    if (c < cells.size()) {
                        TableCell cell = cells.get(c);
                        boolean header = cell.isHeader();
                        if (header) {
                            xwpfCell.setColor(HEADER_FILL);
                        }
                        appendInlines(cell, paragraph, header, false, BODY_SIZE);
                    }
                }
            }
            wroteSomething = true;
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

        private void appendInlines(Node parent, XWPFParagraph paragraph, boolean bold, boolean italic, int fontSize) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    XWPFRun run = paragraph.createRun();
                    run.setFontSize(fontSize);
                    run.setBold(bold);
                    run.setItalic(italic);
                    run.setText(textNode.getLiteral());
                } else if (child instanceof StrongEmphasis emphasis) {
                    appendInlines(emphasis, paragraph, true, italic, fontSize);
                } else if (child instanceof org.commonmark.node.Emphasis emphasis) {
                    appendInlines(emphasis, paragraph, bold, true, fontSize);
                } else if (child instanceof Code code) {
                    XWPFRun run = paragraph.createRun();
                    run.setFontFamily(CODE_FONT);
                    run.setFontSize(fontSize - 1);
                    run.setText(code.getLiteral());
                } else if (child instanceof Link link) {
                    appendInlines(link, paragraph, bold, italic, fontSize);
                    String destination = link.getDestination();
                    if (destination != null && !destination.isBlank()) {
                        XWPFRun run = paragraph.createRun();
                        run.setFontSize(fontSize);
                        run.setText(" (" + destination + ")");
                    }
                } else if (child instanceof Image image) {
                    int before = paragraph.getRuns().size();
                    appendInlines(image, paragraph, bold, italic, fontSize);
                    if (paragraph.getRuns().size() == before && image.getDestination() != null) {
                        XWPFRun run = paragraph.createRun();
                        run.setFontSize(fontSize);
                        run.setText(image.getDestination());
                    }
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    paragraph.createRun().addBreak();
                } else if (child instanceof org.commonmark.node.Paragraph nested) {
                    appendInlines(nested, paragraph, bold, italic, fontSize);
                } else {
                    appendInlines(child, paragraph, bold, italic, fontSize);
                }
            }
        }
    }
}
