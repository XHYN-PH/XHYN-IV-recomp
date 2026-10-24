package com.libertyrecomp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Staged driver packages with recoverable replacement of the installed directory. */
final class DriverPackages {
    private static final String BACKUP = ".previous-";
    interface Move { void move(File from, File to) throws IOException; }

    static boolean filename(String name) {
        if (name == null || name.isEmpty() || name.length() > 200 || name.startsWith(".")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || c == '/' || c == '\\' || c == ':') return false;
        }
        return true;
    }

    private static void directory(File root) throws IOException {
        Files.createDirectories(root.toPath());
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory())
            throw new IOException("Driver folder must be a regular directory.");
    }

    static File stage(File root, InputStream input, long limit) throws IOException {
        directory(root);
        File stage = Files.createTempDirectory(root.toPath(), ".import-").toFile();
        boolean complete = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            Set<String> names = new HashSet<>();
            byte[] buffer = new byte[65536];
            long total = 0;
            int entries = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 512) throw new IOException("Too many driver files.");
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.length() > 1024) throw new IOException("Invalid driver archive path.");
                String[] parts = path.split("/", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (entry.isDirectory() && i == parts.length - 1 && parts[i].isEmpty()) continue;
                    if (!filename(parts[i])) throw new IOException("Invalid driver archive path.");
                }
                if (entry.isDirectory()) continue;
                String name = parts[parts.length - 1];
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate driver filename: " + name);
                File file = new File(stage, name);
                long fileBytes = 0;
                try (FileOutputStream out = new FileOutputStream(file)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n; fileBytes += n;
                        if (total > limit || (name.equalsIgnoreCase("meta.json") && fileBytes > 65536))
                            throw new IOException("Driver archive exceeds its size limit.");
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
            }
            complete = true;
            return stage;
        } finally {
            if (!complete) delete(stage);
        }
    }

    private static File backup(File root, String name) { return new File(root, BACKUP + name); }

    private static void move(File from, File to) throws IOException {
        try { Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from.toPath(), to.toPath()); }
    }

    static void recover(File root, String name) throws IOException {
        directory(root);
        if (!filename(name)) throw new IOException("Invalid driver package name.");
        File target = new File(root, name), old = backup(root, name);
        if (Files.isSymbolicLink(target.toPath()) || Files.isSymbolicLink(old.toPath()))
            throw new IOException("Driver packages cannot be symbolic links.");
        if (!old.exists()) return;
        if (!old.isDirectory() || (target.exists() && !target.isDirectory()))
            throw new IOException("Invalid driver recovery folder.");
        if (!target.exists()) move(old, target);
        else delete(old); // A fully prepared replacement was already published.
    }

    static void recoverAll(File root) throws IOException {
        if (!root.exists()) return;
        directory(root);
        File[] entries = root.listFiles();
        if (entries == null) throw new IOException("Cannot read driver folder.");
        for (File entry : entries) if (entry.getName().startsWith(BACKUP))
            recover(root, entry.getName().substring(BACKUP.length()));
    }

    static File activate(File root, String name, File staged) throws IOException {
        return activate(root, name, staged, DriverPackages::move);
    }

    static File activate(File root, String name, File staged, Move mover) throws IOException {
        recover(root, name);
        if (Files.isSymbolicLink(staged.toPath()) || !staged.isDirectory()
                || !root.getCanonicalFile().equals(staged.getCanonicalFile().getParentFile())
                || !staged.getName().startsWith(".")) throw new IOException("Invalid driver staging folder.");
        File target = new File(root, name), old = backup(root, name);
        if (target.exists() && !target.isDirectory()) throw new IOException("Invalid installed driver folder.");
        boolean replaced = target.exists();
        if (replaced) mover.move(target, old);
        try {
            mover.move(staged, target);
        } catch (IOException failure) {
            if (replaced && !target.exists()) {
                try { mover.move(old, target); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        // Cleanup failure does not turn a successful replacement into a failed import.
        try { delete(old); } catch (IOException ignored) { /* Recovered on the next visit. */ }
        return target;
    }

    static void delete(File file) throws IOException {
        if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read driver folder.");
            for (File child : children) delete(child);
        }
        Files.deleteIfExists(file.toPath());
    }

    private DriverPackages() {}
}
