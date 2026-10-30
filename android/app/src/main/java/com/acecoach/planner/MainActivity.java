package com.acecoach.planner;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class MainActivity extends Activity {
    Ui ui; CourseStore store; LinearLayout shell,nav; FrameLayout holder; int tab=0; LocalDate chosen=LocalDate.now(); boolean allDates=false;
    final Handler ticker=new Handler(Looper.getMainLooper());
    final Runnable refresh=()-> { if(tab==0)render(); scheduleTick(); };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); ui=new Ui(this);store=new CourseStore(this);
        ReminderService.channels(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        shell=ui.column();shell.setBackgroundColor(Ui.BG);ui.insets(shell);
        holder=new FrameLayout(this);shell.addView(holder,new LinearLayout.LayoutParams(-1,0,1));
        nav=ui.row();nav.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));nav.setBackgroundColor(Color.WHITE);shell.addView(nav,new LinearLayout.LayoutParams(-1,ui.dp(73)));
        setContentView(shell);
        if(state!=null){tab=state.getInt("tab",0);chosen=LocalDate.parse(state.getString("day",LocalDate.now().toString()));}
        render();
    }
    @Override protected void onResume() {
        super.onResume();try{ReminderScheduler.reschedule(this);render();}catch(Exception e){message("数据读取失败",e.getMessage());}
        handleOpen();scheduleTick();
    }
    void scheduleTick(){ticker.removeCallbacksAndMessages(null);ticker.postDelayed(refresh,60_000);}
    @Override protected void onPause(){ticker.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("tab",tab);b.putString("day",chosen.toString());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleOpen();}
    void handleOpen(){
        String id=getIntent().getStringExtra("course_id");if(id!=null){getIntent().removeExtra("course_id");Course c=store.find(id);if(c!=null)new Handler(getMainLooper()).post(()->detail(c));}
    }
    List<Course> courses(){return store.all();}
    void render() {
        holder.removeAllViews();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=ui.column();page.setPadding(ui.dp(20),ui.dp(22),ui.dp(20),ui.dp(25));scroll.addView(page);holder.addView(scroll);
        try { if(tab==0)home(page);else if(tab==1)schedule(page);else if(tab==2)students(page);else settings(page); }
        catch(RuntimeException e){ui.add(page,ui.text("数据未能读取，请先导出备份。\n"+e.getMessage(),15,Ui.INK,false));}
        nav.removeAllViews();String[] titles={"总览","课表","学员","设置"};String[] icons={"⌂","▦","◉","⚙"};
        for(int i=0;i<4;i++){
            final int index=i;LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(5),0,ui.dp(5));
            if(i==tab)item.setBackground(ui.round(0xffedf4d5,14));
            ui.add(item,center(ui.text(icons[i],22,i==tab?Ui.GREEN:Ui.MUTED,true)));
            ui.add(item,center(ui.text(titles[i],11,i==tab?Ui.GREEN:Ui.MUTED,i==tab)));
            item.setContentDescription(titles[i]);item.setOnClickListener(v->{tab=index;render();});nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    TextView center(TextView t){t.setGravity(Gravity.CENTER);return t;}
    void header(LinearLayout page,String title,String subtitle) {
        LinearLayout r=ui.row();LinearLayout text=ui.column();
        ui.add(text,ui.text(subtitle,11,Ui.MUTED,false));ui.gap(text,6);ui.add(text,ui.text(title,26,Ui.INK,true));
        r.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        Button add=ui.button("＋",Ui.LIME,Ui.GREEN,()->editor(null));add.setTextSize(26);add.setContentDescription("新增课程");
        r.addView(add,new LinearLayout.LayoutParams(ui.dp(49),ui.dp(49)));ui.add(page,r);ui.gap(page,25);
    }
    void home(LinearLayout page) {
        String coach=store.prefs().getString("coach","教练");
        int hour=LocalTime.now().getHour();String greet=hour<6?"夜深了":hour<12?"早上好":hour<18?"下午好":"晚上好";
        header(page,greet+"，"+coach,LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA)));
        if(!ReminderScheduler.notificationsAllowed(this)||!ReminderScheduler.exactAllowed(this)) {
            LinearLayout notice=ui.card();notice.setBackground(ui.round(0xfffff0db,17));ui.add(notice,ui.text("完成提醒设置，上课更安心",14,0xff8c602a,true));
            ui.gap(notice,5);ui.add(notice,ui.text("点击开启通知与“闹钟和提醒”权限",12,0xff8c602a,false));notice.setOnClickListener(v->{tab=3;render();});ui.cardTo(page,notice);
        }
        List<Course> list=courses();long now=System.currentTimeMillis();
        Course next=list.stream().filter(c->c.endsAt()>now).findFirst().orElse(null);
        FrameLayout hero=new FrameLayout(this);hero.setBackground(ui.round(Ui.GREEN,25));hero.setClipToOutline(true);
        hero.addView(new Ui.CourtArt(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=ui.column();copy.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));
        ui.add(copy,ui.text("●  NEXT CLASS",10,Ui.LIME,true));ui.gap(copy,25);ui.add(copy,ui.text("下一节课程",12,0xffb4c8b9,false));ui.gap(copy,6);
        ui.add(copy,ui.text(next==null?"把下一节课安排好":next.student,27,Color.WHITE,true));ui.gap(copy,8);
        ui.add(copy,ui.text(next==null?"每一节课，都提前准备。":next.dayLabel()+"  "+next.start+"–"+next.end+"\n"+next.location,12,0xffd8e7dc,false));
        ui.gap(copy,23);ui.add(copy,ui.text(next==null?"＋ 新增你的第一节课":next.startsAt()<=now?"正在上课":countdown(next.startsAt()-now),22,Ui.LIME,true));
        hero.addView(copy,new FrameLayout.LayoutParams(-1,-2));hero.setOnClickListener(v->{if(next==null)editor(null);else detail(next);});ui.cardTo(page,hero);
        List<Course> today=list.stream().filter(c->c.date.equals(LocalDate.now().toString())).collect(Collectors.toList());
        LocalDate monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long week=list.stream().filter(c->!LocalDate.parse(c.date).isBefore(monday)&&LocalDate.parse(c.date).isBefore(monday.plusDays(7))).count();
        LinearLayout stats=ui.row();stat(stats,"今日课程",""+today.size(),"节",0xffe7f0e4);stat(stats,"本周课程",""+week,"节",0xffe9eefb);
        stat(stats,"待收课时",""+list.stream().filter(c->!c.paid).count(),"节",0xffffeedc);ui.add(page,stats);ui.gap(page,25);
        ui.section(page,"本周节奏","点击日期看课表");
        LinearLayout days=ui.row();String[] labels={"一","二","三","四","五","六","日"};
        for(int i=0;i<7;i++){
            LocalDate day=monday.plusDays(i);boolean isToday=day.equals(LocalDate.now());long count=list.stream().filter(c->c.date.equals(day.toString())).count();
            LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(12),0,ui.dp(12));item.setBackground(ui.round(isToday?Ui.GREEN:Color.WHITE,13));
            ui.add(item,center(ui.text(labels[i],11,isToday?0xffb9d1c0:Ui.MUTED,false)));ui.gap(item,8);
            ui.add(item,center(ui.text(""+day.getDayOfMonth(),16,isToday?Color.WHITE:Ui.INK,true)));ui.gap(item,5);
            ui.add(item,center(ui.text(count>0?count+"节":"·",10,isToday?Ui.LIME:Ui.MUTED,false)));item.setOnClickListener(v->{chosen=day;allDates=false;tab=1;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=ui.dp(5);days.addView(item,p);
        }
        ui.add(page,days);ui.gap(page,27);ui.section(page,"今日安排",today.size()+" 节课程");
        if(today.isEmpty())empty(page,"今天还没有课程","留一点时间给自己，也可以提前安排下一节。");
        for(Course c:today)courseCard(page,c);
    }
    String countdown(long millis){long m=(millis+59_999)/60_000;return m>=1440?"还有 "+(m/1440)+" 天 "+((m%1440)/60)+" 小时":m>=60?"还有 "+(m/60)+" 小时 "+(m%60)+" 分钟":"还有 "+m+" 分钟";}
    void stat(LinearLayout parent,String label,String number,String unit,int color){
        LinearLayout box=ui.column();box.setPadding(ui.dp(12),ui.dp(17),ui.dp(10),ui.dp(16));box.setBackground(ui.round(Color.WHITE,17));
        TextView mark=ui.text("●",17,color,true);ui.add(box,mark);ui.gap(box,10);ui.add(box,ui.text(label,10,Ui.MUTED,false));ui.gap(box,4);
        ui.add(box,ui.text(number+" "+unit,23,Ui.INK,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(parent.getChildCount()>0)p.leftMargin=ui.dp(8);parent.addView(box,p);
    }
    void empty(LinearLayout page,String title,String subtitle){LinearLayout card=ui.card();card.setPadding(ui.dp(24),ui.dp(34),ui.dp(24),ui.dp(34));ui.add(card,center(ui.text("◌",34,Ui.MUTED,false)));ui.gap(card,10);ui.add(card,center(ui.text(title,16,Ui.INK,true)));ui.gap(card,8);ui.add(card,center(ui.text(subtitle,12,Ui.MUTED,false)));ui.cardTo(page,card);}
    void courseCard(LinearLayout page,Course c){
        LinearLayout card=ui.card();LinearLayout row=ui.row();TextView time=ui.text(c.start,23,Ui.GREEN,true);row.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        ui.pill(row,c.paid?"已收课时":"待收课时",c.paid?0xffe7f1e4:0xffffefdb,c.paid?Ui.GREEN:0xffa36b2b);ui.add(card,row);ui.gap(card,3);
        ui.add(card,ui.text("至 "+c.end+" · "+c.type,11,Ui.MUTED,false));ui.divider(card);ui.add(card,ui.text(c.student,18,Ui.INK,true));ui.gap(card,6);
        ui.add(card,ui.text("⌖  "+c.location,12,Ui.MUTED,false));ui.gap(card,10);ui.add(card,ui.text("◷  "+c.dayLabel()+" · 提前"+reminderLabel(c.reminder)+"提醒",11,Ui.GREEN,false));
        card.setContentDescription(c.student+" "+c.dayLabel()+" "+c.start+" 课程详情");card.setOnClickListener(v->detail(c));ui.cardTo(page,card);
    }
    void schedule(LinearLayout page){
        header(page,"课程安排","把时间留给球场");
        LinearLayout switcher=ui.row();
        Button a=ui.button("按日期",allDates?Color.WHITE:Ui.GREEN,allDates?Ui.GREEN:Color.WHITE,()->{allDates=false;render();});
        Button b=ui.button("全部课程",allDates?Ui.GREEN:Color.WHITE,allDates?Color.WHITE:Ui.GREEN,()->{allDates=true;render();});
        switcher.addView(a,new LinearLayout.LayoutParams(0,ui.dp(45),1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,ui.dp(45),1);bp.leftMargin=ui.dp(8);switcher.addView(b,bp);ui.add(page,switcher);ui.gap(page,16);
        if(!allDates){
            CalendarView calendar=new CalendarView(this);calendar.setFirstDayOfWeek(Calendar.MONDAY);calendar.setDate(chosen.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),false,true);
            calendar.setBackground(ui.round(Color.WHITE,20));ui.add(page,calendar);ui.gap(page,22);
            calendar.setOnDateChangeListener((view,y,m,d)->{chosen=LocalDate.of(y,m+1,d);render();});
        }
        List<Course> list=courses();if(!allDates)list=list.stream().filter(c->c.date.equals(chosen.toString())).collect(Collectors.toList());
        ui.section(page,allDates?"全部课程":chosen.format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA)),list.size()+" 节");
        if(list.isEmpty())empty(page,"这天还没有安排","点击右上角 ＋，新增课程。");
        String last="";for(Course c:list){if(allDates&&!last.equals(c.date)){ui.add(page,ui.text(c.dayLabel(),12,Ui.MUTED,true));ui.gap(page,10);last=c.date;}courseCard(page,c);}
    }
    void students(LinearLayout page){
        header(page,"我的学员","每一份进步，都值得被记录");
        Map<String,List<Course>> groups=new TreeMap<>();for(Course c:courses())groups.computeIfAbsent(c.student,k->new ArrayList<>()).add(c);
        if(groups.isEmpty())empty(page,"期待你的第一位学员","新增课程后，这里会自动整理学员记录。");
        for(Map.Entry<String,List<Course>> e:groups.entrySet()){
            LinearLayout card=ui.card();LinearLayout top=ui.row();TextView initial=center(ui.text(e.getKey().substring(0,1),20,Ui.GREEN,true));initial.setBackground(ui.round(0xffedf3d7,14));top.addView(initial,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
            LinearLayout name=ui.column();name.setPadding(ui.dp(14),0,0,0);ui.add(name,ui.text(e.getKey(),18,Ui.INK,true));
            long upcoming=e.getValue().stream().filter(c->c.endsAt()>System.currentTimeMillis()).count();ui.add(name,ui.text(upcoming+" 节待上",12,Ui.MUTED,false));top.addView(name);ui.add(card,top);ui.divider(card);
            ui.add(card,ui.text("累计 "+e.getValue().size()+" 节  ·  "+String.format(Locale.CHINA,"%.1f",e.getValue().stream().mapToDouble(Course::hours).sum())+" 小时",14,Ui.GREEN,true));
            ui.gap(card,10);ui.add(card,ui.text("点击为学员安排新课程 →",11,Ui.MUTED,false));
            card.setOnClickListener(v->{Course c=new Course();c.student=e.getKey();c.location=e.getValue().get(e.getValue().size()-1).location;editorNew(c);});ui.cardTo(page,card);
        }
    }
    static String reminderLabel(int minutes){return minutes==1440?"1天":minutes>=60?(minutes/60)+"小时":minutes+"分钟";}
    void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
    void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    void settings(LinearLayout page){
        header(page,"提醒与设置","让每节课都有可靠的开始");
        boolean notifications=ReminderScheduler.notificationsAllowed(this),exact=ReminderScheduler.exactAllowed(this);
        LinearLayout ready=ui.card();ready.setBackground(ui.round(notifications&&exact?0xffeaf2d8:0xffffefdb,20));
        ui.add(ready,ui.text(notifications&&exact?"✓ 提醒已准备就绪":"先完成两项提醒设置",19,Ui.GREEN,true));ui.gap(ready,8);
        ui.add(ready,ui.text("默认课前 2 小时提醒；可响铃 60 秒并振动。请确认闹钟音量，并做一次锁屏测试。",12,Ui.GREEN,false));ui.cardTo(page,ready);
        settingCard(page,"通知权限",notifications?"已开启，可显示锁屏提醒":"尚未开启，无法展示课程提醒",notifications?"管理":"开启",this::notificationPermission);
        settingCard(page,"闹钟和提醒",exact?"已开启，按指定时间触发":"未开启，只能使用可能延迟的普通提醒",exact?"管理":"开启",this::exactPermission);
        LinearLayout sound=ui.card();ui.add(sound,ui.text("提醒方式",16,Ui.INK,true));ui.gap(sound,9);
        Switch ring=new Switch(this);ring.setText("课程提醒响铃");ring.setTextSize(14);ring.setChecked(store.prefs().getBoolean("ringtone",true));ring.setPadding(0,ui.dp(12),0,ui.dp(12));
        ring.setOnCheckedChangeListener((button,on)->store.prefs().edit().putBoolean("ringtone",on).apply());ui.add(sound,ring);
        Switch vibrate=new Switch(this);vibrate.setText("同时振动");vibrate.setTextSize(14);vibrate.setChecked(store.prefs().getBoolean("vibration",true));vibrate.setPadding(0,ui.dp(12),0,ui.dp(12));
        vibrate.setOnCheckedChangeListener((button,on)->store.prefs().edit().putBoolean("vibration",on).apply());ui.add(sound,vibrate);
        ui.gap(sound,12);ui.add(sound,ui.button("10秒后测试提醒",Ui.GREEN,Color.WHITE,()->{
            if(!ReminderScheduler.notificationsAllowed(this)||!ReminderScheduler.exactAllowed(this)){message("先完成提醒设置","请先开启上面的两项权限，再测试响铃。");return;}
            ReminderScheduler.test(this);toast("已安排 10 秒后的提醒。现在可以锁屏测试。");
        }));
        long last=store.prefs().getLong("last_fired_at",0);if(last>0){ui.gap(sound,12);ui.add(sound,ui.text("最近触发："+Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))+" · "+store.prefs().getString("last_fired_student",""),11,Ui.MUTED,false));}
        ui.cardTo(page,sound);
        LinearLayout battery=ui.card();ui.add(battery,ui.text("后台运行",16,Ui.INK,true));ui.gap(battery,8);
        ui.add(battery,ui.text("小米、OPPO、vivo 等手机可能有额外的后台限制。可在本应用的系统设置中允许自启动或后台运行。强行停止应用后，请重新打开一次。",12,Ui.MUTED,false));ui.gap(battery,14);
        ui.add(battery,ui.button("打开本应用系统设置",0xffeff3e8,Ui.GREEN,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));ui.cardTo(page,battery);
        LinearLayout profile=ui.card();ui.add(profile,ui.text("教练称呼",16,Ui.INK,true));ui.gap(profile,12);
        EditText coach=ui.input("例如：王教练",store.prefs().getString("coach",""),false);ui.add(profile,coach);ui.gap(profile,12);
        ui.add(profile,ui.button("保存称呼",Ui.GREEN,Color.WHITE,()->{store.prefs().edit().putString("coach",coach.getText().toString().trim().isEmpty()?"教练":coach.getText().toString().trim()).apply();toast("称呼已保存");}));ui.cardTo(page,profile);
        LinearLayout data=ui.card();ui.add(data,ui.text("课程备份",16,Ui.INK,true));ui.gap(data,8);
        ui.add(data,ui.text("课程保存在本机。换手机或卸载前，请先导出备份。",12,Ui.MUTED,false));ui.gap(data,15);
        ui.add(data,ui.button("导出课程备份",0xffeff3e8,Ui.GREEN,()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Ace课表备份-"+LocalDate.now()+".json");startActivityForResult(i,201);
        }));ui.gap(data,10);
        ui.add(data,ui.button("从备份恢复",0xffeff3e8,Ui.GREEN,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,202);
        }));ui.cardTo(page,data);ui.add(page,center(ui.text("Ace 网球课表 1.0.0  ·  安卓原生版",11,Ui.MUTED,false)));
    }
    void settingCard(LinearLayout page,String title,String description,String action,Runnable listener) {
        LinearLayout card=ui.card();ui.add(card,ui.text(title,16,Ui.INK,true));ui.gap(card,6);ui.add(card,ui.text(description,12,Ui.MUTED,false));ui.gap(card,14);ui.add(card,ui.button(action,Ui.GREEN,Color.WHITE,listener));ui.cardTo(page,card);
    }
    void notificationPermission(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
            && !store.prefs().getBoolean("notifications_requested",false)){
            store.prefs().edit().putBoolean("notifications_requested",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
        }else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    void exactPermission(){
        if(Build.VERSION.SDK_INT>=31)open(ReminderScheduler.exactSettings(this));
        else message("已经支持精确定时","当前安卓版本无需额外授权。");
    }
    void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){message("暂时无法打开","请在手机“设置 → 应用 → Ace 网球课表”中手动设置。");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);ReminderScheduler.reschedule(this);render();}
    void detail(Course c) {
        String reminder=LocalDateTime.ofInstant(Instant.ofEpochMilli(c.reminderAt()),ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(c.student)
            .setMessage(c.dayLabel()+"\n"+c.start+" – "+c.end+"\n"+c.location+"\n\n"+c.type+" · "+(c.paid?"已收课时":"待收课时")+"\n提醒时间："+reminder+(c.notes.isEmpty()?"":"\n\n训练备注\n"+c.notes))
            .setPositiveButton("编辑课程",(d,w)->editor(c)).setNegativeButton("关闭",null)
            .setNeutralButton("更多",(d,w)->courseActions(c)).create();dialog.show();
    }
    void courseActions(Course c){
        new AlertDialog.Builder(this).setTitle(c.student).setItems(new String[]{c.paid?"标记为待收课时":"标记为已收课时","删除本节课程"},(d,which)->{
            if(which==0){c.paid=!c.paid;String error=store.add(Collections.singletonList(c),c.id);if(error!=null)toast(error);render();}
            else new AlertDialog.Builder(this).setTitle("删除课程？").setMessage(c.dayLabel()+" "+c.start+" · "+c.student+"\n这节课的提醒也会取消。")
                .setNegativeButton("保留",null).setPositiveButton("删除",(a,b)->{store.delete(c.id);render();toast("课程和提醒已删除");}).show();
        }).show();
    }
    void editor(Course existing){showEditor(existing==null?newCourse():Course.from(existing.json()),existing!=null);}
    void editorNew(Course template){Course c=newCourse();c.student=template.student;c.location=template.location;showEditor(c,false);}
    Course newCourse(){
        Course c=new Course();LocalDate date=tab==1?chosen:LocalDate.now();LocalTime time=LocalTime.now().plusMinutes(60).withMinute(0).withSecond(0).withNano(0);
        if(!date.equals(LocalDate.now())||time.getHour()<7||time.getHour()>21){if(tab!=1&&LocalTime.now().getHour()>=21)date=date.plusDays(1);time=LocalTime.of(9,0);}
        c.date=date.toString();c.start=time.toString();c.end=time.plusHours(1).toString();return c;
    }
    void showEditor(Course draft,boolean editing){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout root=ui.column();root.setBackgroundColor(Ui.BG);ui.insets(root);
        ScrollView scroll=new ScrollView(this);LinearLayout form=ui.column();form.setPadding(ui.dp(23),ui.dp(23),ui.dp(23),ui.dp(28));scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout header=ui.row();TextView title=ui.text(editing?"编辑课程":"新增课程",26,Ui.INK,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(ui.button("×",Color.WHITE,Ui.MUTED,dialog::dismiss),new LinearLayout.LayoutParams(ui.dp(46),ui.dp(46)));ui.add(form,header);ui.gap(form,8);
        ui.add(form,ui.text("把时间、场地与提醒一次安排好",12,Ui.MUTED,false));ui.gap(form,28);
        EditText name=ui.input("学员姓名",draft.student,false);name.setContentDescription("学员姓名");ui.field(form,"学员姓名 *",name);
        Button date=ui.button(draft.date,Color.WHITE,Ui.GREEN,()->{});date.setOnClickListener(v->{
            LocalDate d=LocalDate.parse(draft.date);new DatePickerDialog(this,(picker,y,m,day)->{draft.date=LocalDate.of(y,m+1,day).toString();date.setText(draft.date);},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();
        });ui.field(form,"上课日期 *",date);
        LinearLayout times=ui.row();Button from=ui.button(draft.start,Color.WHITE,Ui.GREEN,()->{}),to=ui.button(draft.end,Color.WHITE,Ui.GREEN,()->{});
        from.setOnClickListener(v->{LocalTime t=LocalTime.parse(draft.start);new TimePickerDialog(this,(picker,h,m)->{draft.start=String.format(Locale.ROOT,"%02d:%02d",h,m);from.setText(draft.start);},t.getHour(),t.getMinute(),true).show();});
        to.setOnClickListener(v->{LocalTime t=LocalTime.parse(draft.end);new TimePickerDialog(this,(picker,h,m)->{draft.end=String.format(Locale.ROOT,"%02d:%02d",h,m);to.setText(draft.end);},t.getHour(),t.getMinute(),true).show();});
        times.addView(from,new LinearLayout.LayoutParams(0,ui.dp(51),1));TextView dash=center(ui.text("—",16,Ui.MUTED,false));times.addView(dash,new LinearLayout.LayoutParams(ui.dp(32),-2));times.addView(to,new LinearLayout.LayoutParams(0,ui.dp(51),1));ui.field(form,"开始 / 结束时间 *",times);
        EditText location=ui.input("例如：星河网球中心 · 3号场",draft.location,false);ui.field(form,"上课场地 *",location);
        String[] types={"一对一私教","双人课","小班课","体能训练","试课"};Spinner type=spinner(types,Math.max(0,Arrays.asList(types).indexOf(draft.type)));ui.field(form,"课程类型",type);
        int[] reminderValues={15,30,60,120,180,1440};String[] reminderNames={"课前15分钟","课前30分钟","课前1小时","课前2小时","课前3小时","提前1天"};
        int selected=3;for(int i=0;i<reminderValues.length;i++)if(reminderValues[i]==draft.reminder)selected=i;
        Spinner reminder=spinner(reminderNames,selected);ui.field(form,"提醒时间",reminder);
        Spinner repeat=spinner(new String[]{"仅这一次","每周一次，共4次","每周一次，共8次","每周一次，共12次"},0);repeat.setEnabled(!editing);ui.field(form,editing?"重复排课（编辑仅修改本节）":"重复排课",repeat);
        CheckBox paid=new CheckBox(this);paid.setText("这节课已收课时");paid.setChecked(draft.paid);paid.setTextColor(Ui.INK);ui.add(form,paid);ui.gap(form,15);
        EditText notes=ui.input("训练重点、学员情况等",draft.notes,true);ui.field(form,"训练备注",notes);
        TextView error=ui.text("",13,0xffb64b36,true);error.setVisibility(View.GONE);ui.add(form,error);ui.gap(form,15);
        Button save=ui.button(editing?"保存修改":"保存课程并安排提醒",Ui.GREEN,Color.WHITE,()->{
            draft.student=name.getText().toString().trim();draft.location=location.getText().toString().trim();draft.notes=notes.getText().toString().trim();
            draft.type=types[type.getSelectedItemPosition()];draft.reminder=reminderValues[reminder.getSelectedItemPosition()];draft.paid=paid.isChecked();
            int[] counts={1,4,8,12};int count=editing?1:counts[repeat.getSelectedItemPosition()];List<Course> additions=new ArrayList<>();additions.add(draft);
            for(int i=1;i<count;i++)additions.add(draft.copyForWeek(i));
            try{
                String problem=store.add(additions,editing?draft.id:null);if(problem!=null){error.setText(problem);error.setVisibility(View.VISIBLE);return;}
                dialog.dismiss();render();toast("已保存 "+count+" 节课程"+(ReminderScheduler.notificationsAllowed(this)?"，提醒已安排":"。请到设置开启提醒权限"));
            }catch(RuntimeException e){error.setText("保存失败："+e.getMessage());error.setVisibility(View.VISIBLE);}
        });save.setContentDescription("保存课程");ui.add(form,save);ui.gap(form,13);
        ui.add(form,center(ui.text("不足提前提醒时间的课程，将尽快提醒一次。",10,Ui.MUTED,false)));
        dialog.setContentView(root);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);}
    }
    Spinner spinner(String[] values,int selected){Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);spinner.setAdapter(adapter);spinner.setSelection(selected);spinner.setPadding(ui.dp(7),ui.dp(7),ui.dp(7),ui.dp(7));spinner.setMinimumHeight(ui.dp(50));spinner.setBackground(ui.border(Color.WHITE,13));return spinner;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        try{
            if(request==201){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("文件无法写入");out.write(store.backup().getBytes(StandardCharsets.UTF_8));}toast("备份已保存");}
            if(request==202){String text;try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("文件无法读取");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>2_000_000)throw new IOException("文件过大，请选择课程备份");}text=bytes.toString("UTF-8");}
                final String backup=text;new AlertDialog.Builder(this).setTitle("导入课程备份？").setMessage("将合并到本机课程。相同编号的课程不会重复导入。").setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->{try{int count=store.importBackup(backup);render();toast("已导入 "+count+" 节课程");}catch(Exception e){message("导入失败",e.getMessage());}}).show();}
        }catch(Exception e){message("操作未完成",e.getMessage());}
    }
}
