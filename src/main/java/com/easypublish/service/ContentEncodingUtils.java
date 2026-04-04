package com.easypublish.service;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Content encoding helpers used by indexing/parsing paths.
 */
public final class ContentEncodingUtils {

    public static final String EPZIP_GZIP_BASE64_PREFIX = "EPZIP1:gzip+base64:";

    private ContentEncodingUtils() {
    }

    public record DecodedContent(
            String content,
            boolean encoded,
            boolean decoded,
            String error
    ) {
    }

    public static DecodedContent decodeForProcessing(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new DecodedContent(rawContent, false, false, null);
        }

        String trimmed = rawContent.trim();
        if (!trimmed.startsWith(EPZIP_GZIP_BASE64_PREFIX)) {
            return new DecodedContent(rawContent, false, false, null);
        }

        String payload = trimmed.substring(EPZIP_GZIP_BASE64_PREFIX.length()).trim();
        if (payload.isEmpty()) {
            return new DecodedContent(rawContent, true, false, "Missing gzip+base64 payload");
        }

        try {
            byte[] zipped = Base64.getDecoder().decode(payload.replaceAll("\\s+", ""));
            String unzipped = gunzipUtf8(zipped);
            return new DecodedContent(unzipped, true, true, null);
        } catch (Exception exception) {
            return new DecodedContent(rawContent, true, false, exception.getMessage());
        }
    }

    private static String gunzipUtf8(byte[] zipped) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (
                GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(zipped));
                Reader reader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8)
        ) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }
}
