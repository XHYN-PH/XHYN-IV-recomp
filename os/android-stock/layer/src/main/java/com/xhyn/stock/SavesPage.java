package com.xhyn.stock;

import android.widget.*;
import java.io.*;
import java.util.*;

final class SavesPage {
    private final PanelActivity a;SavesPage(PanelActivity a){this.a=a;}
    void show() throws IOException {
        a.shell("Saves & backups");File root=StockApi.saves(a);
        Ui.add(a.body,Ui.text(a,"Active save folder\n"+root.getAbsolutePath()+"\nExit the game before importing, exporting or deleting saves.",13,Ui.MUTED));
        Ui.action(a,a.body,"Export all saves & achievements",()->a.export("save-export","XHYN-IV-saves.zip"));
        Ui.action(a,a.body,"Restore full backup",()->Ui.dialog(a,"Restore a backup?","A validated XHYN save ZIP replaces the active save data. A verified recovery snapshot is kept before replacement. Exit the game first.","Choose ZIP",()->a.choose("save-import")));
        if(SaveRecovery.last(a)!=null)Ui.action(a,a.body,"Recover data from before the last full restore",()->Ui.dialog(a,"Recover previous data?","Replace the active save folder with its previous full-restore snapshot. Current saves are protected first.","Recover",()->a.job("Recovering previous saves",()->a.files(()->{SaveRecovery.restoreLast(a,root);return true;}),done->a.render())));
        LinearLayout slots=Ui.column(a);Ui.add(a.body,slots);
        a.job("Reading save slots",()->a.files(()->{SaveRecovery.recover(a);return SaveData.slots(root);}),list->{
            if(list.isEmpty())Ui.add(slots,Ui.text(a,"No Xbox save slots found. Start the game once so stock save migration can finish, then reopen this page.",14,Ui.MUTED));
            for(SaveData.Slot slot:list) {
                LinearLayout card=Ui.card(a);card.addView(Ui.text(a,slot.label(),14,Ui.TEXT));card.addView(Ui.text(a,slot.details(),12,Ui.MUTED));
                Ui.action(a,card,"Export slot",()->{a.pendingSlot=slot.relative;a.export("slot-export",slot.file.getName()+"-slot.zip");});
                Ui.action(a,card,"Replace slot",()->Ui.dialog(a,"Replace this slot?","Choose a matching XHYN slot ZIP. A recovery copy is made before replacement.","Choose ZIP",()->{a.pendingSlot=slot.relative;a.choose("slot-import");}));
                Ui.action(a,card,"Delete slot",()->Ui.dialog(a,"Delete this slot?","A recovery copy will be kept beside the save folder.","Delete",()->a.job("Deleting slot",()->a.files(()->{StockApi.offline(a);SaveData.deleteSlot(root,slot.relative);return true;}),done->a.render())));
                Ui.add(slots,card);
            }
            try {
                File[] recoveries=SaveData.recoveryFolder(root).listFiles((d,n)->n.endsWith(".zip"));
                if(recoveries!=null)for(File backup:recoveries)Ui.action(a,slots,"Recover: "+backup.getName(),()->Ui.dialog(a,"Recover slot?","Replace this slot using its local recovery copy. Current data will be protected first.","Recover",()->a.job("Recovering slot",()->a.files(()->{
                    StockApi.offline(a);String identity=SaveData.recoverySlot(backup);
                    // importSlot may replace the recovery file; keep an independent read handle/copy.
                    byte[] zip;try(InputStream in=new FileInputStream(backup)){zip=FilesEx.read(in,256L*1024*1024);}
                    SaveData.importSlot(root,identity,new ByteArrayInputStream(zip));return true;
                }),done->a.render())));
            }catch(RuntimeException e){Ui.error(a,e);}
        });
    }
}
