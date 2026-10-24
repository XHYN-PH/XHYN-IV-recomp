package com.libertyrecomp;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** No private saves are stored in the repository. An optional full-export path exercises real data. */
public final class SaveMetadataTest {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void u32(byte[] b, int p, int value) { ByteBuffer.wrap(b).putInt(p, value); }
    private static void seal(byte[] b) {
        u32(b, 4, b.length); int sum = 0;
        for (int i = 0; i < b.length - 8; i++) sum += b[i] & 255;
        u32(b, b.length - 8, sum);
    }
    private static byte[] save(float percent, int scriptBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(new byte[12]);
        out.write("SAVE".getBytes(StandardCharsets.US_ASCII));
        for (int block = 0; block < 32; block++) {
            int length = block == 0 ? 185 : block == 1 ? 217 : block == 2 ? 4177 :
                    block == 3 ? scriptBytes + 9 : block == 13 ? 5925 : 9;
            byte[] bytes = new byte[length];
            System.arraycopy("BLOCK".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 5);
            u32(bytes, 5, length);
            if (block == 13) u32(bytes, 9 + 19 * 4, Float.floatToRawIntBits(percent));
            out.write(bytes);
        }
        out.write(new byte[4]); out.write(new byte[]{'E','N','D',0});
        byte[] b = out.toByteArray(); u32(b, 0, 0x38); u32(b, 8, 0xFDD0); seal(b); return b;
    }
    private static byte[] header(String name, String display, int title) {
        byte[] b = new byte[0x148]; u32(b, 0, 1); u32(b, 4, 1); u32(b, 0x140, title);
        byte[] text = display.getBytes(StandardCharsets.UTF_16BE);
        System.arraycopy(text, 0, b, 8, Math.min(text.length, 254));
        byte[] filename = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(filename, 0, b, 0x108, Math.min(filename.length, 42)); return b;
    }
    private static byte[] withoutHeader(byte[] bytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry e; while ((e = in.getNextEntry()) != null) {
                if (e.getName().equals("slot-header")) continue;
                zip.putNextEntry(new ZipEntry(e.getName())); in.transferTo(zip); zip.closeEntry();
            }
        } return out.toByteArray();
    }
    private static byte[] wrongHeader(byte[] bytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry e; while ((e = in.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(e.getName()));
                if (e.getName().equals("slot-header")) zip.write(header("SGTA499", "Wrong slot", 0x545407F2));
                else in.transferTo(zip); zip.closeEntry();
            }
        } return out.toByteArray();
    }
    private static Map<String,String> hashes(File root) throws Exception {
        Map<String,String> result = new TreeMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root.toPath())) {
            for (Iterator<Path> it = files.iterator(); it.hasNext();) {
                Path path = it.next(); if (!Files.isRegularFile(path)) continue;
                result.put(root.toPath().relativize(path).toString(),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
        } return result;
    }
    private static void synthetic(File temporary) throws Exception {
        File file = new File(temporary, "save"), metadata = new File(temporary, "save.header");
        for (float value : new float[]{0f, 3.488372f, 4.883721f, 100f}) {
            for (int shift : new int[]{0, 3, 2000}) {
                Files.write(file.toPath(), save(value, shift));
                check(SaveMetadata.completion(file, "545407F2") == value, "block-relative completion failed");
            }
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1f, 100.01f}) {
            Files.write(file.toPath(), save(invalid, 7));
            check(Float.isNaN(SaveMetadata.completion(file, "545407F2")), "invalid statistic accepted");
        }
        byte[] valid = save(4.883721f, 7);
        for (int offset : new int[]{0, 4, 8, 12, 16, 21, valid.length - 8, valid.length - 1}) {
            byte[] bad = valid.clone(); bad[offset] ^= 1; Files.write(file.toPath(), bad);
            check(Float.isNaN(SaveMetadata.completion(file, "545407F2")), "damaged framing/checksum accepted");
        }
        // Even a correctly resealed save with an unknown framing/layout is not interpreted.
        byte[] unknown = valid.clone(); u32(unknown, 8, 0xFDD1); seal(unknown); Files.write(file.toPath(), unknown);
        check(Float.isNaN(SaveMetadata.completion(file, "545407F2")), "unknown format accepted");
        unknown = valid.clone(); u32(unknown, 21, 186); seal(unknown); Files.write(file.toPath(), unknown);
        check(Float.isNaN(SaveMetadata.completion(file, "545407F2")), "unknown block sequence accepted");
        Files.write(file.toPath(), valid);
        check(Float.isNaN(SaveMetadata.completion(file, "545408A7")), "unverified title accepted");
        Files.write(file.toPath(), Arrays.copyOf(valid, valid.length - 1));
        check(Float.isNaN(SaveMetadata.completion(file, "545407F2")), "truncated save accepted");
        byte[] goodHeader = header("SGTA403", "Bull in a China Shop - 10/23/26 10:34:18", 0x545407F2);
        Files.write(metadata.toPath(), goodHeader);
        SaveMetadata.Header info = SaveMetadata.header(metadata, "SGTA403", "545407F2");
        check(info != null && SaveMetadata.mission(info, "fallback").equals("Bull in a China Shop"), "header name");
        check(SaveMetadata.savedAt(info).equals("10/23/26 10:34:18"), "header date");
        check(SaveMetadata.header(metadata, "SGTA401", "545407F2") == null, "wrong package accepted");
        check(SaveMetadata.header(metadata, "SGTA403", "545408A7") == null, "wrong header title accepted");
        Files.write(metadata.toPath(), Arrays.copyOf(goodHeader, 0x134));
        check(SaveMetadata.header(metadata, "SGTA403", "545407F2") == null, "truncated header accepted");
        // Fail the second install after both originals were moved; both must roll back.
        File transaction = new File(temporary, "transaction"); transaction.mkdirs();
        File target = new File(temporary, "live-package"), liveHeader = new File(temporary, "live-header"),
                incoming = new File(temporary, "incoming"), missingHeader = new File(temporary, "missing");
        Files.write(target.toPath(), new byte[]{1}); Files.write(liveHeader.toPath(), new byte[]{2});
        Files.write(incoming.toPath(), new byte[]{3}); boolean[] preserve = {false};
        Method replace = SaveData.class.getDeclaredMethod("replaceSlot", File.class, File.class, File.class,
                File.class, File.class, boolean[].class); replace.setAccessible(true);
        try { replace.invoke(null, target, liveHeader, incoming, missingHeader, transaction, preserve);
            throw new AssertionError("missing staged header installed");
        } catch (InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "wrong failure"); }
        check(Arrays.equals(Files.readAllBytes(target.toPath()), new byte[]{1}), "package rollback failed");
        check(Arrays.equals(Files.readAllBytes(liveHeader.toPath()), new byte[]{2}), "header rollback failed");
        check(!preserve[0], "successful rollback left pending recovery");
        System.out.println("Save metadata: block traversal, checksum, format/float/header rejection and coordinated rollback passed");
    }
    private static void actual(File temporary, String input) throws Exception {
        File data = new File(temporary, "actual"), root = SaveData.root(data);
        try (InputStream in = new FileInputStream(input)) { SaveData.importZip(root, in); }
        List<SaveData.Slot> slots = SaveData.slots(root);
        check(slots.size() == 4, "expected four supplied packages"); Map<String,String> original = hashes(root);
        for (SaveData.Slot slot : slots) {
            String name = slot.file.getName(); boolean early = name.equals("SGTA401") || name.equals("SGTA407");
            check(early || name.equals("SGTA403") || name.equals("SGTA412"), "unexpected sample");
            check(Math.abs(slot.completion - (early ? 3.488372f : 4.883721f)) < 0.00001f, "screenshot mismatch");
            check(SaveMetadata.mission(slot.header, "").equals(early ? "Bleed Out" : "Bull in a China Shop"), "mission mismatch");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); SaveData.exportSlot(root, slot.relative, out);
            byte[] backup = out.toByteArray(); boolean foundHeader = false;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(backup))) {
                ZipEntry entry; while ((entry = zip.getNextEntry()) != null)
                    if (entry.getName().equals("slot-header")) foundHeader = true;
            } check(foundHeader, "package header omitted");
            for (byte[] bad : new byte[][]{withoutHeader(backup), wrongHeader(backup)}) {
                try { SaveData.importSlot(root, slot.relative, new ByteArrayInputStream(bad));
                    throw new AssertionError("incomplete/mismatched header accepted");
                } catch (IOException expected) { }
                check(hashes(root).equals(original), "rejected import changed live data");
            }
            File payload = new File(slot.file, name);
            String[] parts = slot.relative.split("/");
            File header = new File(root, parts[0]+"/"+parts[1]+"/Headers/00000001/"+name+".header");
            Files.write(payload.toPath(), new byte[]{9});
            Files.write(header.toPath(), header(name, "Replaced display name", 0x545407F2));
            SaveData.importSlot(root, slot.relative, new ByteArrayInputStream(backup));
            check(hashes(root).equals(original), "round trip changed a package/header/achievement");
            SaveData.deleteSlot(root, slot.relative);
            check(!slot.file.exists() && !header.exists(), "deletion left package or stale header");
            check(SaveData.slots(root).size() == 3, "deletion changed another slot");
            SaveData.importSlot(root, slot.relative, new ByteArrayInputStream(backup));
            check(hashes(root).equals(original), "restore of deleted package changed other files");
            System.out.println("Verified " + name + ": " + slot.label() + " | " + slot.details());
        }
        System.out.println("Supplied saves: four header+package round trips, deletions, rejection and account-data isolation passed");
    }
    public static void main(String[] args) throws Exception {
        File temporary = Files.createTempDirectory("xhyn-save-metadata-").toFile();
        try { synthetic(temporary); if (args.length == 1) actual(temporary, args[0]); }
        finally { SaveData.deleteTree(temporary); }
    }
}
