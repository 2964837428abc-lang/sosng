package com.acecoach.planner;

import android.content.*;
import android.app.*;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!ReminderScheduler.ACTION.equals(intent.getAction())) return;
        String id=intent.getStringExtra("course_id");
        if(id==null || !ReminderScheduler.notificationsAllowed(context)) return;
        CourseStore store=new CourseStore(context);
        Course course="test".equals(id)?null:store.find(id);
        if(course==null && !"test".equals(id)) return;
        if(course!=null && course.startsAt()<=System.currentTimeMillis()) return;
        if(course!=null) store.prefs().edit().putString("fired_"+id,course.signature()).remove("snooze_"+id).commit();
        store.prefs().edit().putLong("last_fired_at",System.currentTimeMillis()).putString("last_fired_student",course==null?"提醒测试":course.student).commit();
        Intent service=new Intent(context,ReminderService.class).putExtra("course_id",id);
        if(ReminderScheduler.exactAllowed(context)) {
            try { context.startForegroundService(service); return; }
            catch(RuntimeException ignored) { }
        }
        context.getSystemService(NotificationManager.class).notify(ReminderService.notificationId(id),ReminderService.notification(context,id,false));
    }
}
