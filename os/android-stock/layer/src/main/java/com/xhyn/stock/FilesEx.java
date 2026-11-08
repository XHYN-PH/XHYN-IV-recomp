package com.xhyn.stock;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded reads and durable atomic writes used across both Android processes. */
final class FilesEx {
    static byte[] read(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[32768]; int n;
        while ((n = in.read(b)) != -1) {
            if ((long) out.size() + n > limit) throw new IOException("File exceeds its size limit.");
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }
    static String text(File f, int limit) throws IOException {
        if (!f.exists()) return "";
        regular(f);
        try (InputStream in = new FileInputStream(f)) { return new String(read(in, limit), StandardCharsets.UTF_8); }
    }
    static void regular(File f) throws IOException {
        if (Files.isSymbolicLink(f.toPath()) || !f.isFile()) throw new IOException("Expected a regular file: " + f.getName());
    }
    static void atomic(File f, byte[] bytes) throws IOException {
        Files.createDirectories(f.getParentFile().toPath());
        if (Files.isSymbolicLink(f.toPath())) throw new IOException("Symbolic links are not supported.");
        File temp = File.createTempFile(".xhyn-", ".tmp", f.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); out.getFD().sync(); }
            Files.move(temp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { temp.delete(); }
    }
    static void atomic(File f, String text) throws IOException { atomic(f, text.getBytes(StandardCharsets.UTF_8)); }
    static void copyAtomic(File from,File to,long limit) throws IOException {
        regular(from);Files.createDirectories(to.getParentFile().toPath());
        if(Files.isSymbolicLink(to.toPath()))throw new IOException("Symbolic links are not supported.");
        File temp=File.createTempFile(".xhyn-copy-",".tmp",to.getParentFile());
        try {
            long total=0;byte[] b=new byte[65536];int n;
            try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(temp)) {
                while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("File exceeds its size limit.");out.write(b,0,n);}out.getFD().sync();
            }
            if(!sha(from).equals(sha(temp)))throw new IOException("Copy verification failed.");
            Files.move(temp.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{temp.delete();}
    }
    static File within(File root, String relative) throws IOException {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\")) throw new IOException("Invalid relative path.");
        File current = root;
        if (Files.isSymbolicLink(root.toPath())) throw new IOException("Symbolic links are not supported.");
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.chars().anyMatch(Character::isISOControl))
                throw new IOException("Invalid path component.");
            current = new File(current, part);
            if (Files.isSymbolicLink(current.toPath())) throw new IOException("Symbolic links are not supported.");
        }
        if (!current.getCanonicalFile().toPath().startsWith(root.getCanonicalFile().toPath())) throw new IOException("Path leaves its folder.");
        return current;
    }
    static String sha(File f) throws IOException {
        regular(f);
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] b = new byte[65536]; int n;
            try (InputStream in = new FileInputStream(f)) { while ((n = in.read(b)) != -1) hash.update(b, 0, n); }
            StringBuilder s = new StringBuilder(); for (byte v : hash.digest()) s.append(String.format(Locale.ROOT, "%02x", v & 255));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    static String error(Throwable e) {
        while (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null) e = e.getCause();
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private FilesEx() {}
}
