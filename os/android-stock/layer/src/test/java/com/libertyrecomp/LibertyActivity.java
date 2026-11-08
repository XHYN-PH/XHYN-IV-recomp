package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Test double for the stock argument parser. The real DEX method is checked before packaging. */
public class LibertyActivity extends android.app.Activity {
    public static String[] d(File file)throws IOException {
        if(!file.isFile())return new String[0];return Files.readAllLines(file.toPath(),StandardCharsets.UTF_8).stream().map(String::trim).filter(s->!s.isEmpty()&&!s.startsWith("#")).toArray(String[]::new);
    }
}
