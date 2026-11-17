package com.maodun.photoswipe;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only tests: real SeekBar MotionEvents, Animator frames, and download click. */
public final class Regression197Instrumentation extends Instrumentation {
 MainActivity activity;int checks;StringBuilder report=new StringBuilder();
 Object field(Object o,String s)throws Exception{Field f=o.getClass().getDeclaredField(s);f.setAccessible(true);return f.get(o);}
 void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;report.append("PASS ").append(s).append('\n');}
 void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 long date(int y,int m){Calendar c=Calendar.getInstance();c.set(y,m-1,15,12,0,0);return c.getTimeInMillis();}
 boolean hasText(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),s))return true;return false;}
 View description(View v,String s){if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains(s))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View n=description(((ViewGroup)v).getChildAt(i),s);if(n!=null)return n;}return null;}
 void event(View v,long down,long when,int act,float x,float y){MotionEvent e=MotionEvent.obtain(down,when,act,x,y,0);v.dispatchTouchEvent(e);e.recycle();}
 void scrub(SeekBar axis,boolean forward)throws Exception{long d=SystemClock.uptimeMillis();float w=axis.getWidth(),h=axis.getHeight()/2f;float start=forward?20:w-20,end=forward?w-20:20;runOnMainSync(()->event(axis,d,d,0,start,h));for(int j=1;j<=16;j++){float x=start+(end-start)*j/16;Thread.sleep(45);runOnMainSync(()->event(axis,d,SystemClock.uptimeMillis(),2,x,h));}runOnMainSync(()->event(axis,d,SystemClock.uptimeMillis(),1,end,h));}
 void settled(MyAlbumView a)throws Exception{for(int n=0;n<140;n++){if(!(Boolean)field(field(a,"paper"),"turning")&&field(a,"waitingPage")==null&&field(a,"scrubTarget")==null)return;Thread.sleep(50);}throw new AssertionError("scrub did not settle");}
 void snapshot(View v,String name)throws Exception{runOnMainSync(()->{try{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){throw new RuntimeException(e);}});}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();MyAlbumView[] album={null};FavoriteDetailView[] detail={null};try{
 activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1800);
 float density=activity.getResources().getDisplayMetrics().density;TextView[] tabs=(TextView[])field(activity,"tabs");
 check(tabs[0].getHeight()==Math.round(48*density),"top tabs have 48dp full touch height");
 main(()->tabs[0].performClick());MyAlbumView product=(MyAlbumView)field(activity,"myPage");
 check(!hasText(product,"返回浏览"),"my album redundant return removed");
 check(((LinearLayout)product.getChildAt(0)).getHeight()==Math.round(40*density),"compact album title 40dp");
 check(((TextView)field(product,"jumpInfo")).getVisibility()==View.GONE,"bottom gesture instructions hidden");
 View paperProduct=(View)field(product,"paper");report.append("product paper ").append(paperProduct.getWidth()).append('x').append(paperProduct.getHeight()).append('\n');
 check(paperProduct.getHeight()>1400,"expanded album viewport on 1080x2400 emulator");
 main(()->tabs[2].performClick());check((Integer)field(activity,"activeTab")==2,"short tab click still opens settlement");main(()->tabs[0].performClick());check((Integer)field(activity,"activeTab")==0,"short tab click still opens my");
 FrameLayout host=new FrameLayout(activity);main(()->{try{((MyAlbumView)field(activity,"myPage")).dispose();}catch(Exception e){throw new RuntimeException(e);}activity.setContentView(host);});
 SharedPreferences p=activity.getSharedPreferences("photoswipe",0);List<MainActivity.PhotoItem> photos=new ArrayList<>();
 for(int j=0;j<18;j++){int month=1+j*2;photos.add(new MainActivity.PhotoItem(197000+j,Uri.parse("content://media/external/images/media/1000000073"),"timeline-test-"+j,1,date(2024+((month-1)/12),((month-1)%12)+1),600,900));}
 main(()->{album[0]=new MyAlbumView(activity,p,false,photos,()->{},()->{},i->{});host.addView(album[0],new FrameLayout.LayoutParams(-1,-1));});Thread.sleep(1500);
 MyAlbumView a=album[0];SeekBar axis=(SeekBar)field(a,"axis");
 check(axis.getContentDescription().toString().contains("36个月"),"seekbar accessibility says 36 months");
 AlbumDateWindow window=(AlbumDateWindow)field(a,"window");Calendar st=AlbumDateWindow.day(window.start()),en=AlbumDateWindow.day(window.end());check((en.get(Calendar.YEAR)-st.get(Calendar.YEAR))*12+en.get(Calendar.MONTH)-st.get(Calendar.MONTH)==35,"window spans 36 calendar months");
 String caption=MyAlbumView.inlineCaption("2026.11.13","甲乙\n丙丁\r戊己庚辛壬癸");check(!caption.contains("\n")&&!caption.contains("\r")&&caption.startsWith("2026.11.13"),"date and excerpt remain one line");
 android.text.TextPaint tp=new android.text.TextPaint();tp.setTextSize(30);String fit=android.text.TextUtils.ellipsize(caption+caption,tp,180,android.text.TextUtils.TruncateAt.END).toString();check(tp.measureText(fit)<=181&&fit.endsWith("…"),"long caption ellipsized within one line width");
 View paper=(View)field(a,"paper");int initial=(Integer)field(a,"animationSteps");
 // Hold and move real SeekBar events. Capture actual in-progress paper after targeting a far date.
 long down=SystemClock.uptimeMillis();runOnMainSync(()->event(axis,down,down,0,axis.getWidth()-20,axis.getHeight()/2f));
 runOnMainSync(()->event(axis,down,down+50,2,20,axis.getHeight()/2f));Thread.sleep(70);
 check((Boolean)field(paper,"turning")||(a.getHandler()!=null&&field(a,"waitingPage")!=null),"drag starts loading or real fast-turn animator");
 for(int q=0;q<40&&!((Boolean)field(paper,"turning"));q++)Thread.sleep(15);
 check((Boolean)field(paper,"turning"),"timeline animator actually running");
 for(int q=0;q<100;q++){float progress=(Float)field(a,"turnProgress");if((Boolean)field(paper,"turning")&&progress>.18f&&progress<.8f){report.append("fast curl progress=").append(progress).append('\n');break;}Thread.sleep(4);}
 snapshot(paper,"qa197_fastcurl.png");
 runOnMainSync(()->event(axis,down,SystemClock.uptimeMillis(),1,20,axis.getHeight()/2f));settled(a);
 check((Integer)field(a,"page")==0,"axis drag lands oldest target after release");
 check((Integer)field(a,"animationSteps")-initial>=3,"far timeline drag traverses multiple real page animations");
 scrub(axis,true);settled(a);check((Integer)field(a,"page")==17,"reverse timeline drag lands newest target");
 scrub(axis,false);scrub(axis,true);settled(a);check((Integer)field(a,"page")==17,"rapid direction change follows latest released target");
 snapshot(paper,"qa197_caption.png");
 int frames=(Integer)field(a,"animationSteps");main(()->a.dispose());Thread.sleep(250);check((Integer)field(a,"animationSteps")==frames&&!((Boolean)field(a,"disposed")).equals(false),"leaving album cancels pending fast turns");
 final Bitmap img=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);new Canvas(img).drawColor(Color.BLUE);
 main(()->{host.removeAllViews();detail[0]=new FavoriteDetailView(activity,photos.get(0),p,false,()->{},()->{},()->{});host.addView(detail[0],new FrameLayout.LayoutParams(-1,-1));detail[0].content(img,"2026年11月13日 15:00:00","测试地点 · 纬度31.226667°N");});
 check(!hasText(detail[0],"保存这一页"),"detail has no save text button");View download=description(detail[0],"下载，保存这一页到相册");check(download!=null&&download.getWidth()==Math.round(48*density)&&download.getHeight()==Math.round(48*density),"download icon has meaningful accessibility and 48dp hit area");
 long before=0;try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},null,null,"_id DESC")){if(c.moveToFirst())before=c.getLong(0);}final long previous=before;
 main(()->download.performClick());for(int q=0;q<100&&(Boolean)field(detail[0],"exporting");q++)Thread.sleep(50);
 Uri saved=null;try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name"},"_id>?",new String[]{String.valueOf(previous)},"_id DESC")){while(c.moveToNext())if(c.getString(1).startsWith("相册滑选-记忆-")){saved=android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));break;}}
 check(saved!=null,"real download click creates published public album PNG");Bitmap exported;try(java.io.InputStream in=activity.getContentResolver().openInputStream(saved)){exported=BitmapFactory.decodeStream(in);}check(exported!=null&&exported.getWidth()==1080,"downloaded content image decodes at 1080 width");
 try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"qa197_download.png"))){exported.compress(Bitmap.CompressFormat.PNG,100,f);}exported.recycle();main(()->detail[0].dispose());
 out.putString("stream",report+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",report+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
