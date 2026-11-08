package com.xhyn.stock;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Read grants for diagnostic ZIPs only. No arbitrary external-file access. */
public final class LayerFileProvider extends ContentProvider {
    static Uri uri(Context c,File file) {return new Uri.Builder().scheme("content").authority(c.getPackageName()+".layer.files").appendPath(file.getName()).build();}
    private File checked(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();if(uri.getPathSegments().size()!=1||name==null||!name.matches("XHYN-IV-diagnostics-[a-fA-F0-9-]{36}\\.zip"))throw new FileNotFoundException("Invalid diagnostic file.");
        File root=new File(getContext().getCacheDir(),"layer-share");File f=new File(root,name);
        try{if(!root.getCanonicalFile().equals(f.getCanonicalFile().getParentFile())||java.nio.file.Files.isSymbolicLink(f.toPath())||!f.isFile())throw new FileNotFoundException("Diagnostic file expired.");}
        catch(IOException e){throw new FileNotFoundException(FilesEx.error(e));}return f;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/zip";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!mode.equals("r"))throw new FileNotFoundException("Read-only export.");return ParcelFileDescriptor.open(checked(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        try{File f=checked(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor out=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=columns[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():columns[i].equals(OpenableColumns.SIZE)?f.length():null;out.addRow(values);return out;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
