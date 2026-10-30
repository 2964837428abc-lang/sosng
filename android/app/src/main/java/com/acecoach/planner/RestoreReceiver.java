package com.acecoach.planner;
import android.content.*;
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        try { ReminderScheduler.reschedule(context); } catch(RuntimeException ignored) { }
    }
}
