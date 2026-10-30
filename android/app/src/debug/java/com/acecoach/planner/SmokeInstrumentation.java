package com.acecoach.planner;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import java.time.*;
import java.util.*;
import java.io.*;
public final class SmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context context=getTargetContext();
            CourseStore store=new CourseStore(context);store.write(new ArrayList<>());
            Course c=new Course();c.student="陈同学";c.date=LocalDate.now().plusDays(1).toString();c.start="15:00";c.end="16:00";c.location="星河网球中心 · 3号场";c.notes="正手稳定性与移动步法";
            check(c.validate()==null,"valid course rejected");
            Course overlap=c.copyForWeek(0);overlap.start="15:30";overlap.end="16:30";check(c.conflicts(overlap),"overlap not detected");
            Course adjacent=c.copyForWeek(0);adjacent.start="16:00";adjacent.end="17:00";check(!c.conflicts(adjacent),"adjacent course rejected");
            check(c.copyForWeek(7).date.equals(LocalDate.parse(c.date).plusWeeks(7).toString()),"weekly repeat incorrect");
            check(store.add(Collections.singletonList(c),null)==null,"store add failed");
            check(store.add(Collections.singletonList(overlap),null)!=null,"store accepted conflict");
            check(store.all().size()==1,"conflicting write changed data");
            c.notes="已修改";check(store.add(Collections.singletonList(c),c.id)==null,"update failed");
            check(new CourseStore(context).find(c.id).notes.equals("已修改"),"update not persisted");
            String backup=store.backup();check(store.importBackup(backup)==0,"backup duplicates existing courses");
            Course student=c.copyForWeek(0);student.student="安安";student.start="18:00";student.end="19:00";student.paid=true;
            check(store.add(Collections.singletonList(student),null)==null,"second course add failed");
            check(ReminderScheduler.notificationsAllowed(context),"notification permission missing");
            check(ReminderScheduler.exactAllowed(context),"exact alarm permission missing");
            MainActivity activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            for(int tab=0;tab<4;tab++){final int current=tab;runOnMainSync(()->{activity.tab=current;activity.render();});waitForIdleSync();}
            runOnMainSync(()->{activity.tab=0;activity.render();});
            long before=System.currentTimeMillis();ReminderScheduler.test(context);
            getUiAutomation().executeShellCommand("input keyevent 3").close();
            Thread.sleep(13_000);
            check(store.prefs().getLong("last_fired_at",0)>=before,"scheduled alarm did not fire");
            boolean found=false;for(android.service.notification.StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications())
                if(n.getId()==ReminderService.notificationId("test"))found=true;
            check(found,"alarm notification not visible");
            context.startService(new Intent(context,ReminderService.class).setAction(ReminderService.STOP).putExtra("course_id","test"));
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            Thread.sleep(1500);Bitmap image=getUiAutomation().takeScreenshot();
            if(image!=null){try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"smoke.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
            result.putString("stream","PASS: native screens, course validation, overlap, adjacency, repeat dates, atomic save, persistence, backup deduplication, background exact alarm, visible notification.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
