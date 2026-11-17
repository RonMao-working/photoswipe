package com.maodun.photoswipe;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
/** Emulator-only verification of actual TextView baselines, paper bottom and rendered output. */
public final class Regression1916Instrumentation extends Instrumentation {
 MainActivity activity;FavoriteDetailView page;int checks;StringBuilder report=new StringBuilder();
 Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;report.append("PASS ").append(name).append('\n');}
 void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1200);
  FrameLayout host=new FrameLayout(activity);main(()->activity.setContentView(host));
  SharedPreferences prefs=activity.getSharedPreferences("qa-memory-1916",0);prefs.edit().clear().commit();
  MainActivity.PhotoItem item=new MainActivity.PhotoItem(191600,Uri.parse("content://photoswipe-qa/memory1916"),"synthetic",1,1,600,900);
  Bitmap image=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);new Canvas(image).drawColor(0xFF467681);
  final String time="2026年11月17日 12:00:00",place="上海市静安区\n纬度 31.230000°N · 经度 121.470000°E";
  main(()->{page=new FavoriteDetailView(activity,item,prefs,false,()->{},()->{},()->{});host.addView(page,new FrameLayout.LayoutParams(-1,-1));page.content(image,time,place);});
  EditText story=(EditText)field(page,"story");LinearLayout panel=(LinearLayout)field(page,"storyPanel");View entry=(View)field(page,"storyEntry");TextView date=(TextView)field(page,"date"),location=(TextView)field(page,"place");
  check(panel.getVisibility()==View.GONE,"empty story reserves no paper area");check(entry.getVisibility()==View.VISIBLE,"compact story entry stays available");
  ViewGroup paper=(ViewGroup)location.getParent();float density=activity.getResources().getDisplayMetrics().density;
  check(paper.getHeight()-location.getBottom()==Math.round(12*density),"metadata ends at narrow paper padding");
  int first=date.getTop()+date.getBaseline(),second=location.getTop()+location.getLayout().getLineBaseline(0),third=location.getTop()+location.getLayout().getLineBaseline(1);
  check(Math.abs((second-first)-(third-second))<=2,"screen time place coordinates have equal baseline spacing");
  main(()->entry.performClick());check(panel.getVisibility()==View.VISIBLE&&story.hasFocus(),"compact entry expands an editable story");
  main(()->story.setText("这段记忆要完整保留"));check(MemoryNotes.get(prefs,item.identity()).equals("这段记忆要完整保留"),"story remains automatically saved");
  main(()->{story.setText("");page.requestFocus();});check(panel.getVisibility()==View.GONE,"clearing and leaving editor collapses blank area");
  TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setTextSize(29);
  StaticLayout metadata=new StaticLayout(MemoryLayout.metadata(time,place),tp,948,Layout.Alignment.ALIGN_NORMAL,1f,12,false);
  check(metadata.getLineCount()==3,"export metadata contains time place and coordinates rows");check(metadata.getLineBaseline(1)-metadata.getLineBaseline(0)==metadata.getLineBaseline(2)-metadata.getLineBaseline(1),"export baselines are exactly equally spaced");
  Bitmap blank=FavoriteDetailView.renderExport(image,time,place,"");
  int fitHeight=1400;check(blank.getHeight()==MemoryLayout.exportHeight(fitHeight,metadata.getHeight(),0),"actual PNG height contains no story reservation");
  Bitmap whitespace=FavoriteDetailView.renderExport(image,time,place," \n ");check(whitespace.sameAs(blank),"whitespace-only story exports like empty story");
  Bitmap filled=FavoriteDetailView.renderExport(image,time,place,"这段记忆要完整保留");check(filled.getHeight()>blank.getHeight(),"written story expands output for real content only");
  Bitmap noPlace=FavoriteDetailView.renderExport(image,time,"","");check(noPlace.getHeight()<blank.getHeight(),"noGPS unfilled location reserves no rows");
  blank.recycle();whitespace.recycle();filled.recycle();noPlace.recycle();main(()->{page.dispose();activity.finish();});prefs.edit().clear().commit();
  out.putString("stream",report+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",report+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
