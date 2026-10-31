package com.acecoach.planner;
import android.content.*;
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
            && !android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;
        CourseStore store=new CourseStore(context);
        try {
            ReminderScheduler.reschedule(context);
            store.prefs().edit().putLong("last_restore_at",System.currentTimeMillis()).putString("last_restore_action",action).remove("last_restore_error").commit();
            android.util.Log.i("AceCoach","Restored reminders: "+action+", courses="+store.all().size());
        } catch(RuntimeException error) {
            store.prefs().edit().putString("last_restore_error",error.toString()).commit();
            android.util.Log.e("AceCoach","Could not restore reminders",error);
        }
    }
}
