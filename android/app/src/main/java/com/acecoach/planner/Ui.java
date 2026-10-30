package com.acecoach.planner;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;

final class Ui {
    static final int BG=0xfff5f6f0, INK=0xff17271d, GREEN=0xff183f2d, LIME=0xffd9f24f, MUTED=0xff768279, LINE=0xffe5e9df;
    final Context c;
    Ui(Context context) { c=context; }
    int dp(float value) { return (int)(value*c.getResources().getDisplayMetrics().density+.5f); }
    GradientDrawable round(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    GradientDrawable border(int color,int radius) { GradientDrawable d=round(color,radius); d.setStroke(dp(1),LINE); return d; }
    TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setLineSpacing(dp(2),1f); return t;
    }
    LinearLayout column() { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l=new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    void gap(LinearLayout l,int height) { View v=new View(c); l.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    void add(LinearLayout l,View view) { l.addView(view,new LinearLayout.LayoutParams(-1,-2)); }
    LinearLayout card() {
        LinearLayout l=column(); l.setPadding(dp(19),dp(19),dp(19),dp(19)); l.setBackground(border(0xffffffff,21)); return l;
    }
    void cardTo(LinearLayout parent,View card) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(13); parent.addView(card,p);
    }
    Button button(String label,int color,int textColor,Runnable action) {
        Button b=new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(textColor);
        b.setTypeface(Typeface.create("sans-serif-medium",0)); b.setBackground(round(color,14)); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setPadding(dp(15),dp(8),dp(15),dp(8)); b.setElevation(0); b.setStateListAnimator(null);
        b.setOnClickListener(v->action.run()); return b;
    }
    EditText input(String hint,String value,boolean multiline) {
        EditText e=new EditText(c); e.setTextSize(15); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setHint(hint); e.setText(value);
        e.setPadding(dp(13),dp(12),dp(13),dp(12)); e.setBackground(border(0xfffafbf7,13));
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);}else e.setSingleLine(true);
        return e;
    }
    void field(LinearLayout parent,String title,View input) {
        add(parent,text(title,12,MUTED,true)); gap(parent,7); add(parent,input); gap(parent,18);
    }
    void section(LinearLayout parent,String title,String subtitle) {
        LinearLayout row=row(); TextView t=text(title,18,INK,true); row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        if(subtitle!=null) row.addView(text(subtitle,11,MUTED,false));
        add(parent,row); gap(parent,14);
    }
    void pill(LinearLayout parent,String value,int bg,int color) {
        TextView t=text(value,11,color,true); t.setPadding(dp(9),dp(5),dp(9),dp(5)); t.setBackground(round(bg,8)); parent.addView(t);
    }
    void divider(LinearLayout p) { View v=new View(c); v.setBackgroundColor(LINE); LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,dp(1));q.topMargin=dp(14);q.bottomMargin=dp(14);p.addView(v,q); }
    void insets(View view) {
        view.setOnApplyWindowInsetsListener((v,i)-> {
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets a=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(a.left,a.top,a.right,a.bottom);}
            else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());
            return i;
        });
    }
    static final class CourtArt extends View {
        final Paint paint=new Paint(3);
        CourtArt(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(),h=getHeight();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0x1dffffff);
            canvas.drawRect(w*.54f,-h*.2f,w*1.04f,h*1.3f,paint);canvas.drawLine(w*.54f,h*.5f,w*1.04f,h*.5f,paint);
            canvas.drawLine(w*.79f,-h*.2f,w*.79f,h*1.3f,paint);canvas.drawLine(w*.54f,h*.23f,w*1.04f,h*.23f,paint);
            canvas.drawLine(w*.54f,h*.77f,w*1.04f,h*.77f,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0x12d9f24f);canvas.drawCircle(w,h*.84f,h*.3f,paint);
        }
    }
}
