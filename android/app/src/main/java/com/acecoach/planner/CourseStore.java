package com.acecoach.planner;

import android.content.*;
import org.json.*;
import java.util.*;

public final class CourseStore {
    final Context context;
    public CourseStore(Context c) { context=c.getApplicationContext(); }
    public SharedPreferences prefs() { return context.getSharedPreferences("ace_native_v1",Context.MODE_PRIVATE); }
    public List<Course> all() {
        List<Course> list=new ArrayList<>();
        try { JSONArray j=new JSONArray(prefs().getString("courses","[]")); for(int i=0;i<j.length();i++) list.add(Course.from(j.getJSONObject(i))); }
        catch(Exception e) { throw new IllegalStateException("课程数据无法读取，请保留数据并导出备份",e); }
        list.sort(Comparator.comparingLong(Course::startsAt)); return list;
    }
    public Course find(String id) { for(Course c:all()) if(c.id.equals(id)) return c; return null; }
    public void write(List<Course> list) {
        JSONArray a=new JSONArray(); for(Course c:list) a.put(c.json());
        if(!prefs().edit().putString("courses",a.toString()).commit()) throw new IllegalStateException("保存失败，请检查手机存储空间");
    }
    public String add(List<Course> additions, String replaceId) {
        List<Course> list=all(); list.removeIf(c->c.id.equals(replaceId));
        for(Course candidate:additions) {
            String error=candidate.validate(); if(error!=null) return error;
            for(Course existing:list) if(candidate.conflicts(existing)) return candidate.dayLabel()+" "+candidate.start+" 与 "+existing.student+" 的课程重叠";
            list.add(candidate);
        }
        write(list);
        if(replaceId!=null) ReminderScheduler.cancel(context,replaceId);
        ReminderScheduler.reschedule(context); return null;
    }
    public void delete(String id) {
        List<Course> list=all(); list.removeIf(c->c.id.equals(id)); write(list); ReminderScheduler.cancel(context,id);
        prefs().edit().remove("fired_"+id).remove("snooze_"+id).apply();
    }
    public String backup() {
        try {
            JSONObject j=new JSONObject(); j.put("app","Ace Coach"); j.put("version",1);
            JSONArray a=new JSONArray(); for(Course c:all()) a.put(c.json());
            j.put("courses",a); return j.toString(2);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public int importBackup(String text) throws Exception {
        JSONObject root=new JSONObject(text);
        if(!"Ace Coach".equals(root.optString("app"))) throw new IllegalArgumentException("请选择 Ace 导出的备份文件");
        JSONArray a=root.getJSONArray("courses"); if(a.length()>5000) throw new IllegalArgumentException("备份文件课程过多");
        List<Course> list=all(); Set<String> ids=new HashSet<>(); for(Course c:list) ids.add(c.id);
        int count=0;
        for(int i=0;i<a.length();i++) {
            Course c=Course.from(a.getJSONObject(i)); String e=c.validate(); if(e!=null) throw new IllegalArgumentException("第"+(i+1)+"节："+e);
            if(ids.add(c.id)) { list.add(c); count++; }
        }
        write(list); ReminderScheduler.reschedule(context); return count;
    }
}
