package com.acecoach.planner;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.util.Locale;

public final class ReminderService extends Service {
    public static final String CHANNEL="ace_course_alarm_v1", FALLBACK="ace_course_fallback_v1";
    public static final String STOP="ace.stop", SNOOZE="ace.snooze";
    final Handler handler=new Handler(Looper.getMainLooper());
    MediaPlayer player; Vibrator vibrator; PowerManager.WakeLock wake; AudioFocusRequest focus; String currentId;
    public static int notificationId(String id) { return id==null?1001:1000+(id.hashCode()&0x0fffffff); }
    public static void channels(Context c) {
        NotificationManager n=c.getSystemService(NotificationManager.class);
        NotificationChannel alarm=new NotificationChannel(CHANNEL,"课程闹铃",NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("课前响铃、锁屏通知和提醒测试");
        alarm.setSound(null,null); alarm.enableVibration(false);
        n.createNotificationChannel(alarm);
        NotificationChannel fallback=new NotificationChannel(FALLBACK,"普通课程通知",NotificationManager.IMPORTANCE_HIGH);
        fallback.setDescription("未授权精确定时或闹铃服务不可用时的通知");
        fallback.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        fallback.enableVibration(true); n.createNotificationChannel(fallback);
    }
    static PendingIntent action(Context c,String id,String action) {
        Intent i=new Intent(c,ReminderService.class).setAction(action).setData(Uri.parse("ace://action/"+id+"/"+action)).putExtra("course_id",id);
        return PendingIntent.getService(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static Notification notification(Context c,String id,boolean ringing) {
        channels(c); Course course="test".equals(id)?null:new CourseStore(c).find(id);
        String title="test".equals(id)?"Ace 提醒测试":"网球课即将开始";
        String text="test".equals(id)?"这是测试响铃，点击“已知晓”停止。":"课程提醒";
        if(course!=null) {
            long minutes=Math.max(0,(course.startsAt()-System.currentTimeMillis()+59_999)/60_000);
            title=course.student+" · "+(minutes>=60?String.format(Locale.CHINA,"约 %d 小时后上课",minutes/60):minutes+" 分钟后上课");
            text=course.dayLabel()+" "+course.start+"–"+course.end+"\n"+course.location;
        }
        Intent open=new Intent(c,MainActivity.class).putExtra("course_id",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,id.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(c,ringing?CHANNEL:FALLBACK)
            .setSmallIcon(R.drawable.ic_notification).setColor(0xff173b2b).setContentTitle(title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(content).setOnlyAlertOnce(true)
            .setOngoing(ringing).setAutoCancel(!ringing)
            .addAction(new Notification.Action.Builder(null,"已知晓",action(c,id,STOP)).build());
        if(course!=null && course.startsAt()>System.currentTimeMillis()+5*60_000)
            b.addAction(new Notification.Action.Builder(null,"5分钟后再提醒",action(c,id,SNOOZE)).build());
        return b.build();
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) { stopSelf(); return START_NOT_STICKY; }
        String id=intent.getStringExtra("course_id");
        if(STOP.equals(intent.getAction()) || SNOOZE.equals(intent.getAction())) {
            if(SNOOZE.equals(intent.getAction()) && id!=null) ReminderScheduler.snooze(this,id);
            if(id!=null) getSystemService(NotificationManager.class).cancel(notificationId(id));
            stopSelf(); return START_NOT_STICKY;
        }
        if(id==null || !ReminderScheduler.notificationsAllowed(this)) { stopSelf(); return START_NOT_STICKY; }
        currentId=id;
        Notification n=notification(this,id,true);
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(notificationId(id),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(notificationId(id),n);
        } catch(RuntimeException ex) {
            getSystemService(NotificationManager.class).notify(notificationId(id),notification(this,id,false));
            stopSelf(); return START_NOT_STICKY;
        }
        stopSound();
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AceCoach:CourseAlarm"); wake.acquire(65_000);
        CourseStore store=new CourseStore(this);
        if(store.prefs().getBoolean("ringtone",true)) playSound();
        if(store.prefs().getBoolean("vibration",true)) {
            vibrator=getSystemService(Vibrator.class);
            if(vibrator!=null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,450,250,450,1600},0));
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(()-> {
            stopSound(); stopForeground(STOP_FOREGROUND_DETACH);
            getSystemService(NotificationManager.class).notify(notificationId(id),notification(this,id,false)); stopSelf();
        },60_000);
        return START_NOT_STICKY;
    }
    void playSound() {
        try {
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            AudioManager audio=getSystemService(AudioManager.class);
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS) stopSound();}).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(uri==null) uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player=new MediaPlayer(); player.setAudioAttributes(attrs); player.setDataSource(this,uri);
            player.setLooping(true); player.prepare(); player.start();
        } catch(Exception ignored) { if(player!=null) { player.release(); player=null; } }
    }
    void stopSound() {
        if(player!=null) { try { player.stop(); } catch(Exception ignored) {} player.release(); player=null; }
        if(vibrator!=null) { vibrator.cancel(); vibrator=null; }
        if(focus!=null) { getSystemService(AudioManager.class).abandonAudioFocusRequest(focus); focus=null; }
        if(wake!=null && wake.isHeld()) wake.release(); wake=null;
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); stopSound(); super.onDestroy(); }
}
