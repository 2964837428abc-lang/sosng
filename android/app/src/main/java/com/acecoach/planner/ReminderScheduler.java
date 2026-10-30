package com.acecoach.planner;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

public final class ReminderScheduler {
    public static final String ACTION="com.acecoach.planner.REMIND";
    public static boolean exactAllowed(Context c) {
        return Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    public static boolean notificationsAllowed(Context c) {
        ReminderService.channels(c);
        NotificationManager n=c.getSystemService(NotificationManager.class);
        NotificationChannel channel=n.getNotificationChannel(ReminderService.CHANNEL);
        return n.areNotificationsEnabled() && (channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static PendingIntent pending(Context c,String id) {
        Intent i=new Intent(c,ReminderReceiver.class).setAction(ACTION).setData(Uri.parse("ace://reminder/"+id)).putExtra("course_id",id);
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void put(Context c,String id,long when) {
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        PendingIntent fire=pending(c,id);
        if(exactAllowed(c)) {
            Intent show=new Intent(c,MainActivity.class).putExtra("course_id",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open=PendingIntent.getActivity(c,id.hashCode(),show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            try { alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(when,open),fire); return; }
            catch(SecurityException ignored) { }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,fire);
    }
    public static long nextTime(Context c,Course course) {
        long now=System.currentTimeMillis();
        if(course.startsAt()<=now) return 0;
        CourseStore store=new CourseStore(c);
        long snooze=store.prefs().getLong("snooze_"+course.id,0);
        if(snooze>now) return snooze;
        if(course.signature().equals(store.prefs().getString("fired_"+course.id,""))) return 0;
        return Math.max(course.reminderAt(),now+2000);
    }
    public static void reschedule(Context c) {
        for(Course course:new CourseStore(c).all()) {
            cancel(c,course.id);
            long when=nextTime(c,course);
            if(when>0 && notificationsAllowed(c)) put(c,course.id,when);
        }
    }
    public static void cancel(Context c,String id) {
        PendingIntent p=pending(c,id);
        c.getSystemService(AlarmManager.class).cancel(p);
    }
    public static void snooze(Context c,String id) {
        Course course=new CourseStore(c).find(id);
        if(course==null) return;
        long when=System.currentTimeMillis()+5*60_000L;
        if(when>=course.startsAt()) return;
        new CourseStore(c).prefs().edit().putLong("snooze_"+id,when).commit();
        put(c,id,when);
    }
    public static void test(Context c) { put(c,"test",System.currentTimeMillis()+10_000L); }
    public static Intent exactSettings(Context c) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+c.getPackageName()));
    }
}
