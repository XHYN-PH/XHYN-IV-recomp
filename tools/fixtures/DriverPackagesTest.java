package com.libertyrecomp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class DriverPackagesTest {
    interface Task { void run() throws IOException; }
    static void rejects(Task task) throws IOException {
        try { task.run(); throw new AssertionError("Expected rejection"); }
        catch (IOException expected) {}
    }
    static byte[] archive(String... names) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(data)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[32]); zip.closeEntry();
            }
        }
        return data.toByteArray();
    }
    static File staged(File root, String value) throws IOException {
        File stage = Files.createTempDirectory(root.toPath(), ".test-").toFile();
        Files.writeString(new File(stage, "driver.so").toPath(), value);
        return stage;
    }
    static String value(File root) throws IOException {
        return Files.readString(new File(root, "candidate/driver.so").toPath());
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("xhyn-driver-test-").toFile();
        try {
            File stage = DriverPackages.stage(root, new ByteArrayInputStream(archive("package/driver.so", "package/meta.json")), 128);
            assert new File(stage, "driver.so").isFile();
            DriverPackages.delete(stage);
            rejects(() -> DriverPackages.stage(root, new ByteArrayInputStream(archive("a/driver.so", "b/driver.so")), 256));
            rejects(() -> DriverPackages.stage(root, new ByteArrayInputStream(archive("driver.so", "DRIVER.SO")), 256));
            for (String path : new String[]{"../driver.so", "/driver.so", "a/../driver.so", "a//driver.so", "C:/driver.so", "bad\n.so"})
                rejects(() -> DriverPackages.stage(root, new ByteArrayInputStream(archive(path)), 256));
            rejects(() -> DriverPackages.stage(root, new ByteArrayInputStream(archive("driver.so")), 16));
            assert Objects.requireNonNull(root.list()).length == 0 : "Rejected imports left temporary files";

            DriverPackages.activate(root, "candidate", staged(root, "old"));
            File replacement = staged(root, "new");
            final File firstStage = replacement;
            rejects(() -> DriverPackages.activate(root, "candidate", firstStage, (from, to) -> {
                if (from.equals(firstStage)) throw new IOException("Injected promotion failure");
                Files.move(from.toPath(), to.toPath());
            }));
            assert value(root).equals("old") : "Old driver lost after failed replacement";
            DriverPackages.delete(replacement);

            replacement = staged(root, "new");
            final File failedStage = replacement;
            rejects(() -> DriverPackages.activate(root, "candidate", failedStage, (from, to) -> {
                if (from.equals(failedStage) || from.getName().startsWith(".previous-"))
                    throw new IOException("Injected promotion/rollback failure");
                Files.move(from.toPath(), to.toPath());
            }));
            assert new File(root, ".previous-candidate/driver.so").isFile();
            DriverPackages.recoverAll(root);
            assert value(root).equals("old") : "Interrupted replacement was not recovered";
            DriverPackages.delete(replacement);

            Files.move(new File(root, "candidate").toPath(), new File(root, ".previous-candidate").toPath());
            Files.move(staged(root, "new").toPath(), new File(root, "candidate").toPath());
            DriverPackages.recoverAll(root);
            assert value(root).equals("new");
            assert !new File(root, ".previous-candidate").exists();
            DriverPackages.activate(root, "candidate", staged(root, "final"));
            assert value(root).equals("final");

            File outside = Files.createTempDirectory("xhyn-driver-outside-").toFile();
            Files.writeString(new File(outside, "keep").toPath(), "keep");
            try {
                File link = new File(root, "link");
                Files.createSymbolicLink(link.toPath(), outside.toPath());
                rejects(() -> DriverPackages.activate(root, "link", staged(root, "bad")));
                DriverPackages.delete(link);
                assert new File(outside, "keep").isFile();
            } finally { DriverPackages.delete(outside); }

            File data = new File(root, "data");
            assert DriverState.normalize(" Custom: My Driver ").equals("custom:My Driver");
            assert DriverState.normalize(" TURNIP_V38 ").equals("auto");
            assert DriverState.normalize("custom:../bad").equals("auto");
            DriverState.choose(data, " SYSTEM ");
            assert DriverState.readChoice(data).equals("system");
            DriverState.working(data, "system", "device", 35, "actual system fallback");
            assert DriverState.lastWorking(data, "device", 35).equals("system");
            assert DriverState.lastWorking(data, "other-device", 35).isEmpty();
            DriverState.observe(data, "custom:test", "system", "fallback");
            DriverState.forget(data, "custom:test");
            assert DriverState.observation(data, "custom:test").isEmpty();
            assert DriverState.lastWorking(data, "device", 35).equals("system");
            DriverState.forget(data, "system");
            assert DriverState.lastWorking(data, "device", 35).isEmpty();
            rejects(() -> DriverState.working(data, "system", "device", 35, "x".repeat(70000)));
            System.out.println("Driver packages: duplicates, bounds, replacement rollback, interrupted recovery, symlinks and history passed");
        } finally { DriverPackages.delete(root); }
    }
}
