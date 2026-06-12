package com.careermate.resume.version.export;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

@Component
public class ResumeVersionDocxRenderer {

    private static final int BODY_SIZE = 11;
    private static final int H1_SIZE = 18;
    private static final int H2_SIZE = 14;

    private final Parser markdownParser = Parser.builder().build();

    public void render(String markdown, OutputStream outputStream) throws Exception {
        Node document = markdownParser.parse(markdown == null ? "" : markdown);
        try (XWPFDocument docx = new XWPFDocument()) {
            document.accept(new MarkdownDocxVisitor(docx));
            docx.write(outputStream);
        }
    }

    private static final class MarkdownDocxVisitor extends AbstractVisitor {

        private final XWPFDocument docx;

        private MarkdownDocxVisitor(XWPFDocument docx) {
            this.docx = docx;
        }

        @Override
        public void visit(Heading heading) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setSpacingBefore(160);
            paragraph.setSpacingAfter(120);
            paragraph.setAlignment(heading.getLevel() <= 1 ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
            appendInlines(heading, paragraph, true, heading.getLevel() <= 1 ? H1_SIZE : H2_SIZE);
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraphNode) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setSpacingAfter(80);
            appendInlines(paragraphNode, paragraph, false, BODY_SIZE);
        }

        @Override
        public void visit(BulletList bulletList) {
            visitChildren(bulletList);
        }

        @Override
        public void visit(ListItem listItem) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setIndentationLeft(240);
            paragraph.setSpacingAfter(40);
            XWPFRun bullet = paragraph.createRun();
            bullet.setFontSize(BODY_SIZE);
            bullet.setText("• ");
            appendInlines(listItem, paragraph, false, BODY_SIZE);
        }

        private void appendInlines(Node parent, XWPFParagraph paragraph, boolean bold, int fontSize) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text textNode) {
                    XWPFRun run = paragraph.createRun();
                    run.setFontSize(fontSize);
                    run.setBold(bold);
                    run.setText(textNode.getLiteral());
                } else if (child instanceof StrongEmphasis emphasis) {
                    appendInlines(emphasis, paragraph, true, fontSize);
                } else if (child instanceof SoftLineBreak) {
                    paragraph.createRun().addBreak();
                } else if (child instanceof org.commonmark.node.Paragraph nested) {
                    appendInlines(nested, paragraph, bold, fontSize);
                } else {
                    appendInlines(child, paragraph, bold, fontSize);
                }
            }
        }
    }
}
