package com.maodun.photoswipe;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native month grid: hollow circles mean confirmed favorites, filled cells mean selection. */
final class FavoriteCalendar extends LinearLayout {
    interface Pick { void select(long day); }
    private final Calendar month, selected;
    private final Set<Long> favoriteDays;
    private final TextView title;
    private final LinearLayout weeks;
    private final float density;
    private final int ink=0xFF62513A;
    private final Pick pick;
    private AlertDialog dialog;
    FavoriteCalendar(Context c,long value,Set<Long> days,Pick pick){
        super(c);this.pick=pick;favoriteDays=days;density=getResources().getDisplayMetrics().density;
        month=AlbumDateWindow.day(value);selected=(Calendar)month.clone();month.set(Calendar.DAY_OF_MONTH,1);
        setOrientation(VERTICAL);setPadding(dp(14),dp(10),dp(14),dp(10));setBackgroundColor(0xFFF4ECDB);
        LinearLayout nav=new LinearLayout(c);nav.setGravity(Gravity.CENTER_VERTICAL);addView(nav,new LayoutParams(-1,dp(44)));
        TextView prev=text("‹",25),next=text("›",25);title=text("",16);prev.setPadding(dp(15),0,dp(15),0);next.setPadding(dp(15),0,dp(15),0);nav.addView(prev);nav.addView(title,new LayoutParams(0,-1,1));nav.addView(next);
        prev.setContentDescription("日历上个月");next.setContentDescription("日历下个月");
        prev.setOnClickListener(v->{month.add(Calendar.MONTH,-1);render();});next.setOnClickListener(v->{month.add(Calendar.MONTH,1);render();});
        title.setOnClickListener(v->chooseMonth());title.setContentDescription("选择年月");
        LinearLayout dow=new LinearLayout(c);addView(dow,new LayoutParams(-1,dp(28)));
        for(String s:new String[]{"日","一","二","三","四","五","六"})dow.addView(text(s,11),new LayoutParams(0,-1,1));
        weeks=new LinearLayout(c);weeks.setOrientation(VERTICAL);addView(weeks);
        TextView note=text("圆圈日期有收藏 · 点击年月可快速跳转",10);note.setPadding(0,dp(8),0,0);addView(note);render();
    }
    void show(){dialog=new AlertDialog.Builder(getContext()).setTitle("收藏日历").setView(this).setNegativeButton("取消",null).create();dialog.show();}
    private int dp(float d){return Math.round(d*density);}
    private TextView text(String s,int size){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);return t;}
    private void chooseMonth(){
        LinearLayout box=new LinearLayout(getContext());box.setPadding(dp(16),0,dp(16),0);box.setGravity(Gravity.CENTER);
        NumberPicker y=new NumberPicker(getContext()),m=new NumberPicker(getContext());y.setMinValue(1900);y.setMaxValue(2100);y.setValue(Math.max(1900,Math.min(2100,month.get(Calendar.YEAR))));m.setMinValue(1);m.setMaxValue(12);m.setValue(month.get(Calendar.MONTH)+1);box.addView(y);box.addView(m);
        new AlertDialog.Builder(getContext()).setTitle("选择年月").setView(box).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{month.set(Calendar.YEAR,y.getValue());month.set(Calendar.MONTH,m.getValue()-1);render();}).show();
    }
    private void render(){
        title.setText(new SimpleDateFormat("yyyy年M月",Locale.CHINA).format(month.getTime()));weeks.removeAllViews();
        int offset=month.get(Calendar.DAY_OF_WEEK)-1,count=month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for(int r=0;r<6;r++){LinearLayout row=new LinearLayout(getContext());weeks.addView(row,new LayoutParams(-1,dp(43)));
            for(int col=0;col<7;col++){int d=r*7+col-offset+1;if(d<1||d>count){row.addView(new View(getContext()),new LayoutParams(0,-1,1));continue;}
                Calendar day=(Calendar)month.clone();day.set(Calendar.DAY_OF_MONTH,d);long stamp=day.getTimeInMillis();boolean fav=favoriteDays.contains(stamp),sel=stamp==selected.getTimeInMillis();DayCell cell=new DayCell(getContext(),d,fav,sel);cell.setContentDescription(new SimpleDateFormat("yyyy年M月d日",Locale.CHINA).format(day.getTime())+(fav?" 有收藏":" 无收藏"));row.addView(cell,new LayoutParams(0,-1,1));cell.setOnClickListener(v->{pick.select(stamp);if(dialog!=null)dialog.dismiss();});
            }
            if((r+1)*7>=offset+count)break;
        }
    }
    private final class DayCell extends View {
        final int day;final boolean favorite,active;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        DayCell(Context c,int day,boolean favorite,boolean active){super(c);this.day=day;this.favorite=favorite;this.active=active;setClickable(true);}
        protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(16);
            if(active){p.setColor(0xFFE0CFA8);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,r,p);}
            if(favorite){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.3f));p.setColor(0xFF9A7848);c.drawCircle(cx,cy,r,p);}
            p.setStyle(Paint.Style.FILL);p.setColor(ink);p.setTextSize(dp(13));p.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf(day),cx,cy-(p.ascent()+p.descent())/2,p);
        }
    }
}
