package com.careermate.resume.version.export;

/**
 * 简历导出版式预设（P3）——把原先散落在渲染器里的版式参数集中为可配置的两套内置模板。
 *
 * <ul>
 *   <li>{@link #PDF_PRECISE}：PDF 精排版（iText 渲染）——居中/字号层次/品牌色分节线，
 *       适合直接投递、给人看。</li>
 *   <li>{@link #WORD_ATS}：Word · ATS 友好版（POI 渲染）——单栏、纯文本结构，
 *       最大化机器可解析度，适合投递 ATS 系统或二次编辑。</li>
 * </ul>
 *
 * <p>改版式只改这里，渲染器逻辑不动；两套预设即产品对外的「两套简历模板」。
 */
public final class ResumeLayoutProfile {

    /** PDF 精排版预设。 */
    public static final PdfLayout PDF_PRECISE = new PdfLayout(
            40f, 20f, 9f, 11f, 10.5f, 1.25f, 0x6B7280, 0xD1D5DB);

    /** Word · ATS 友好版预设。 */
    public static final DocxLayout WORD_ATS = new DocxLayout(
            11, 10, 18, 14, 12, "Consolas", "F1F5F9");

    /** PDF 版式参数（单位 pt，颜色为 0xRRGGBB）。 */
    public record PdfLayout(
            float margin,
            float nameSize,
            float contactSize,
            float sectionTitleSize,
            float bodySize,
            float lineMultiplier,
            int contactColorRgb,
            int sectionLineColorRgb
    ) {
    }

    /** Word 版式参数。 */
    public record DocxLayout(
            int bodySize,
            int codeSize,
            int h1Size,
            int h2Size,
            int h3Size,
            String codeFont,
            String headerFillHex
    ) {
    }

    private ResumeLayoutProfile() {
    }
}
