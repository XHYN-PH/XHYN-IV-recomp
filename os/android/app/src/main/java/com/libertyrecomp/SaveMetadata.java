package com.libertyrecomp;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

/** Read-only metadata for XAM headers and the observed Xbox 360 GTA IV save layout. */
final class SaveMetadata {
    private static final int MAX_SAVE_BYTES = 16 * 1024 * 1024;
    static final class Header {
        final String displayName;
        Header(String displayName) { this.displayName = displayName; }
    }
    static Header header(File file, String packageName, String title) {
        try {
            if (Files.isSymbolicLink(file.toPath()) || !file.isFile() || file.length() != 0x148)
                return null;
            byte[] b = Files.readAllBytes(file.toPath());
            // XCONTENT_AGGREGATE_DATA: content_type, UTF-16BE display_name[128],
            // filename[42], padding, aligned XUID and title_id. Trailing padding is unspecified.
            if (b.length != 0x148 || u32(b, 4) != 1 ||
                    u32(b, 0x140) != Long.parseUnsignedLong(title, 16)) return null;
            int end = 0x108;
            while (end < 0x132 && b[end] != 0) end++;
            if (!packageName.equals(new String(b, 0x108, end - 0x108, StandardCharsets.US_ASCII)))
                return null;
            int textEnd = 8;
            while (textEnd < 0x108 && (b[textEnd] != 0 || b[textEnd + 1] != 0)) textEnd += 2;
            String text = StandardCharsets.UTF_16BE.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b, 8, textEnd - 8)).toString().trim();
            if (text.isEmpty()) return null;
            for (int i = 0; i < text.length(); i++) if (Character.isISOControl(text.charAt(i))) return null;
            return new Header(text);
        } catch (IOException | IllegalArgumentException error) { return null; }
    }
    static String mission(Header header, String fallback) {
        if (header == null) return fallback;
        int separator = dateSeparator(header.displayName);
        return separator > 0 ? header.displayName.substring(0, separator) : header.displayName;
    }
    static String savedAt(Header header) {
        if (header == null) return "";
        int separator = dateSeparator(header.displayName);
        return separator > 0 ? header.displayName.substring(separator + 3) : "";
    }
    private static int dateSeparator(String text) {
        int index = text.lastIndexOf(" - ");
        return index > 0 && text.substring(index + 3).matches("[0-9]{2}/[0-9]{2}/[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}") ? index : -1;
    }
    static float completion(File save, String title) {
        if (!"545407F2".equalsIgnoreCase(title)) return Float.NaN;
        try {
            if (Files.isSymbolicLink(save.toPath()) || !save.isFile() ||
                    save.length() < 32 || save.length() > MAX_SAVE_BYTES) return Float.NaN;
            byte[] b;
            // Bound the read itself as well as the initial length check.
            try (InputStream in = new FileInputStream(save)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > MAX_SAVE_BYTES) return Float.NaN;
                    out.write(buffer, 0, n);
                }
                b = out.toByteArray();
            }
            if (b.length < 32 || u32(b, 0) != 0x38 || u32(b, 4) != b.length ||
                    u32(b, 8) != 0xFDD0 || !magic(b, 12, "SAVE") ||
                    !magic(b, b.length - 4, "END\0")) return Float.NaN;
            long checksum = 0;
            for (int i = 0; i < b.length - 8; i++) checksum = (checksum + (b[i] & 255)) & 0xFFFFFFFFL;
            if (checksum != u32(b, b.length - 8)) return Float.NaN;
            int position = 16, statistics = -1;
            for (int block = 0; block < 32; block++) {
                if (position > b.length - 17 || !magic(b, position, "BLOCK")) return Float.NaN;
                long length = u32(b, position + 5);
                if (length < 9 || length > b.length - 8L - position) return Float.NaN;
                // These block layouts identify the format covered by the supplied console saves.
                if ((block == 0 && length != 185) || (block == 1 && length != 217) ||
                        (block == 2 && length != 4177)) return Float.NaN;
                if (block == 13) {
                    if (length != 5925) return Float.NaN;
                    statistics = position + 9;
                }
                position += (int) length;
            }
            if (position != b.length - 8 || statistics < 0) return Float.NaN;
            // Float statistic 19 in the recognized statistics block. Traverse block lengths;
            // preceding script/world blocks can vary. Never use a fixed whole-file offset.
            float value = Float.intBitsToFloat((int) u32(b, statistics + 19 * 4));
            return Float.isFinite(value) && value >= 0 && value <= 100 ? value : Float.NaN;
        } catch (IOException | IndexOutOfBoundsException error) { return Float.NaN; }
    }
    private static long u32(byte[] b, int p) {
        return ((b[p] & 255L) << 24) | ((b[p + 1] & 255L) << 16) |
                ((b[p + 2] & 255L) << 8) | (b[p + 3] & 255L);
    }
    private static boolean magic(byte[] b, int p, String text) {
        if (p < 0 || p + text.length() > b.length) return false;
        for (int i = 0; i < text.length(); i++) if ((b[p + i] & 255) != text.charAt(i)) return false;
        return true;
    }
}
