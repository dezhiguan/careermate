package com.careermate.resume.version.export;

import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ResumeExportResponseHeaders {

    private ResumeExportResponseHeaders() {
    }

    public static void pdf(HttpServletResponse response, String baseName) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", contentDisposition(baseName, "pdf"));
    }

    public static void docx(HttpServletResponse response, String baseName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", contentDisposition(baseName, "docx"));
    }

    private static String contentDisposition(String baseName, String extension) {
        String fallback = sanitizeAscii(baseName);
        String encoded = URLEncoder.encode(safeBaseName(baseName) + "." + extension, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallback + "." + extension + "\"; filename*=UTF-8''" + encoded;
    }

    private static String safeBaseName(String value) {
        if (value == null || value.isBlank()) {
            return "resume";
        }
        return value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
                .replaceAll("\\s+", " ");
    }

    private static String sanitizeAscii(String value) {
        String safe = safeBaseName(value).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return safe.isBlank() ? "resume" : safe;
    }
}
