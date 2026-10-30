package com.acecoach.planner;

import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Course {
    public String id = UUID.randomUUID().toString();
    public String student = "", date = LocalDate.now().toString(), start = "09:00", end = "10:00";
    public String location = "", type = "一对一私教", notes = "";
    public int reminder = 120;
    public boolean paid = false;
    public long startsAt() { return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(start)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public long endsAt() { return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(end)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public long reminderAt() { return startsAt() - reminder * 60_000L; }
    public double hours() { return (endsAt() - startsAt()) / 3_600_000d; }
    public String signature() { return id + ":" + date + ":" + start + ":" + reminder; }
    public String dayLabel() {
        LocalDate d = LocalDate.parse(date), today = LocalDate.now();
        String prefix = d.equals(today) ? "今天" : d.equals(today.plusDays(1)) ? "明天" : d.format(DateTimeFormatter.ofPattern("M月d日"));
        return prefix + " · 周" + "一二三四五六日".charAt(d.getDayOfWeek().getValue()-1);
    }
    public boolean conflicts(Course other) {
        return !id.equals(other.id) && date.equals(other.date) && start.compareTo(other.end)<0 && end.compareTo(other.start)>0;
    }
    public String validate() {
        if (student.trim().isEmpty()) return "请填写学员姓名";
        if (location.trim().isEmpty()) return "请填写上课场地";
        try { if (endsAt() <= startsAt()) return "结束时间应晚于开始时间，不支持跨夜课程"; }
        catch (RuntimeException ex) { return "日期或时间格式不正确"; }
        if (!Arrays.asList(15,30,60,120,180,1440).contains(reminder)) return "提醒时间不正确";
        return null;
    }
    public JSONObject json() {
        JSONObject j = new JSONObject();
        try {
            j.put("id",id).put("student",student).put("date",date).put("startTime",start).put("endTime",end)
                .put("location",location).put("type",type).put("notes",notes).put("reminderMinutes",reminder).put("paid",paid);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return j;
    }
    public static Course from(JSONObject j) {
        Course c=new Course();
        c.id=j.optString("id",c.id); c.student=j.optString("student"); c.date=j.optString("date");
        c.start=j.optString("startTime"); c.end=j.optString("endTime"); c.location=j.optString("location");
        c.type=j.optString("type","一对一私教"); c.notes=j.optString("notes"); c.reminder=j.optInt("reminderMinutes",120); c.paid=j.optBoolean("paid");
        return c;
    }
    public Course copyForWeek(int offset) {
        Course c=from(json()); c.id=UUID.randomUUID().toString(); c.date=LocalDate.parse(date).plusWeeks(offset).toString(); return c;
    }
}
