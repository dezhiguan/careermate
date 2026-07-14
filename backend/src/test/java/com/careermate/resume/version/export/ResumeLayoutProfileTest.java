package com.careermate.resume.version.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeLayoutProfileTest {

    @Test
    void pdfPreciseHasExpectedValues() {
        ResumeLayoutProfile.PdfLayout p = ResumeLayoutProfile.PDF_PRECISE;
        assertThat(p.margin()).isEqualTo(40f);
        assertThat(p.nameSize()).isEqualTo(20f);
        assertThat(p.contactSize()).isEqualTo(9f);
        assertThat(p.sectionTitleSize()).isEqualTo(11f);
        assertThat(p.bodySize()).isEqualTo(10.5f);
        assertThat(p.lineMultiplier()).isEqualTo(1.25f);
        assertThat(p.contactColorRgb()).isEqualTo(0x6B7280);
        assertThat(p.sectionLineColorRgb()).isEqualTo(0xD1D5DB);
    }

    @Test
    void wordAtsHasAtsFriendlyValues() {
        ResumeLayoutProfile.DocxLayout w = ResumeLayoutProfile.WORD_ATS;
        assertThat(w.bodySize()).isEqualTo(11);
        assertThat(w.codeSize()).isEqualTo(10);
        assertThat(w.h1Size()).isEqualTo(18);
        assertThat(w.h2Size()).isEqualTo(14);
        assertThat(w.h3Size()).isEqualTo(12);
        assertThat(w.codeFont()).isEqualTo("Consolas");
        assertThat(w.headerFillHex()).isEqualTo("F1F5F9");
    }
}
