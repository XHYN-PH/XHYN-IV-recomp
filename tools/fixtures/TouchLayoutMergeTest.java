package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;

public final class TouchLayoutMergeTest {
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("touch-merge").toFile();
        try{
            TouchLayoutDocument doc=new TouchLayoutDocument();
            doc.get("on_foot/jump").dx=17;doc.get("on_foot/aim").opacity=.35f;
            doc.get("car/brake").dy=31;doc.get("xbox/a").scale=1.8f;doc.save(root);
            TouchLayoutDocument undo=TouchLayoutDocument.read(root);
            TouchLayoutDocument incoming=new TouchLayoutDocument();
            incoming.get("on_foot/jump").dx=42;incoming.get("on_foot/jump").hidden=true;
            doc.merge(incoming);
            assert doc.entries.size()==4&&doc.get("on_foot/jump").dx==42&&doc.get("on_foot/jump").hidden;
            assert doc.get("car/brake").dy==31&&doc.get("on_foot/aim").opacity==.35f&&doc.get("xbox/a").scale==1.8f;
            incoming.get("on_foot/jump").dx=99;assert doc.get("on_foot/jump").dx==42:"Import must not alias the input";
            assert TouchLayoutDocument.read(root).get("on_foot/jump").dx==17:"Import must not implicitly save";
            assert undo.get("on_foot/jump").dx==17&&undo.entries.size()==4:"Undo checkpoint must remain independent";
            doc.save(root);TouchLayoutDocument loaded=TouchLayoutDocument.read(root);
            assert loaded.entries.size()==4&&loaded.get("on_foot/jump").dx==42&&loaded.get("car/brake").dy==31;
            System.out.println("Touch layout merge: contexts, control properties, draft isolation and explicit save passed");
        }finally{SaveData.deleteTree(root);}
    }
}
