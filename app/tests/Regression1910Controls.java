package com.maodun.photoswipe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.location.Address;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Standalone emulator-only test APK, never bundled into the production application. */
public final class Regression1910Controls extends Instrumentation {
    private Activity activity;
    private int checks;
    private final StringBuilder report=new StringBuilder();
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;report.append("PASS ").append(name).append('\n');}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);Thread.sleep(1800);
            Context c=getTargetContext();SharedPreferences p=c.getSharedPreferences("photoswipe",0);
            String testUri="instrumentation-only-memory";
            String old=p.getString(MemoryNotes.key(testUri),null);
            MemoryNotes.save(p,testUri,"山河故事"+new String(Character.toChars(0x1F30D))+"甲乙丙丁戊己庚辛壬癸"+"春夏秋冬春夏秋冬春夏秋冬春夏秋冬春夏秋冬春夏秋冬春夏秋冬");
            String stored=MemoryNotes.get(p,testUri);
            check(stored.codePointCount(0,stored.length())==40,"40 Unicode code points persisted");
            check(MemoryNotes.excerpt(p,testUri).codePointCount(0,MemoryNotes.excerpt(p,testUri).length())==10,"10 code-point excerpt");
            MemoryNotes.save(p,testUri,"");check(MemoryNotes.excerpt(p,testUri).isEmpty(),"empty story has no excerpt");
            if(old==null)p.edit().remove(MemoryNotes.key(testUri)).commit();else p.edit().putString(MemoryNotes.key(testUri),old).commit();
            Address address=new Address(Locale.CHINA);address.setCountryName("中国");address.setAdminArea("上海市");address.setLocality("上海市");address.setSubLocality("徐汇区");address.setThoroughfare("不应导出街道");
            String place=PhotoPlace.display(address);
            check(place.contains("徐汇区")&&!place.contains("街道")&&place.indexOf("上海市")==place.lastIndexOf("上海市"),"district place formatting avoids streets and duplicates");
            final FrameLayout[] host={null};final ZoomPhotoView[] zoom={null};final MyAlbumView[] album={null};final FavoriteDetailView[] detail={null};
            Bitmap image=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);new Canvas(image).drawColor(Color.BLUE);
            final Bitmap img=image;
            main(()->{host[0]=new FrameLayout(activity);activity.setContentView(host[0]);zoom[0]=new ZoomPhotoView(activity);host[0].addView(zoom[0],new FrameLayout.LayoutParams(600,900));zoom[0].setImage(img);});
            long down=SystemClock.uptimeMillis();
            main(()->send(zoom[0],down,SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,new float[]{230},new float[]{450}));
            main(()->send(zoom[0],down,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{50,550},new float[]{450,450}));
            for(int i=1;i<=14;i++){final int step=i;Thread.sleep(25);main(()->send(zoom[0],down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,new float[]{50-step*45,550+step*45},new float[]{450,450}));}
            main(()->{send(zoom[0],down,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{76,524},new float[]{450,450});send(zoom[0],down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,new float[]{76},new float[]{450});});
            report.append("zoom=").append(zoom[0].zoom()).append(" size=").append(zoom[0].getWidth()).append("x").append(zoom[0].getHeight()).append('\n');
            check(zoom[0].zoom()>2,"two-pointer MotionEvent passes real ScaleGestureDetector");
            float large=zoom[0].zoom();main(()->swipe(zoom[0],200,400,300,450));check(zoom[0].zoom()==large,"single-finger pan does not reset zoom");
            main(()->{long d=SystemClock.uptimeMillis();send(zoom[0],d,d,MotionEvent.ACTION_DOWN,new float[]{300},new float[]{450});send(zoom[0],d,d+30,MotionEvent.ACTION_UP,new float[]{300},new float[]{450});});
            check(zoom[0].zoom()==1,"tap resets zoom without swipe decision");
            Set<Long> days=new HashSet<>();Calendar date=AlbumDateWindow.day(System.currentTimeMillis());long selected=date.getTimeInMillis();date.add(Calendar.DAY_OF_MONTH,-1);days.add(date.getTimeInMillis());
            final FavoriteCalendar[] calendar={null};main(()->{host[0].removeAllViews();calendar[0]=new FavoriteCalendar(activity,selected,days,t->{});host[0].addView(calendar[0]);});
            check(hasDescription(calendar[0]," 有收藏"),"calendar marks existing favorite date");
            check(hasDescription(calendar[0]," 无收藏"),"calendar selection does not fabricate favorite dates");
            List<MainActivity.PhotoItem> photos=new ArrayList<>();
            for(int j=0;j<6;j++)photos.add(new MainActivity.PhotoItem(70001+j,Uri.parse("content://media/external/images/media/1000000073"),"pair-test-"+j,1,1000000000000L+j*100000000000L,600,900));
            main(()->{host[0].removeAllViews();album[0]=new MyAlbumView(activity,p,false,photos,()->{},()->{},i->{});host[0].addView(album[0],new FrameLayout.LayoutParams(-1,-1));});Thread.sleep(1400);
            View paper=(View)field(album[0],"paper");
            check(((Integer)field(album[0],"page"))==2,"album defaults newest page");
            runOnMainSync(()->swipe(paper,120,300,700,300));Thread.sleep(180);
            runOnMainSync(()->{try{float progress=(Float)field(album[0],"turnProgress");report.append("curl progress=").append(progress).append('\n');if(progress<=0||progress>=1)throw new IllegalStateException("no intermediate frame");Bitmap frame=Bitmap.createBitmap(paper.getWidth(),paper.getHeight(),Bitmap.Config.ARGB_8888);paper.draw(new Canvas(frame));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"qa194_curl.png"))){frame.compress(Bitmap.CompressFormat.PNG,100,out);}frame.recycle();}catch(Exception e){throw new RuntimeException(e);}});checks++;report.append("PASS real animator intermediate frame\n");Thread.sleep(750);
            check(((Integer)field(album[0],"page"))==1,"right swipe goes to older previous page");
            main(()->swipe(paper,700,300,120,300));Thread.sleep(900);
            check(((Integer)field(album[0],"page"))==2,"left swipe goes to newer next page");
            main(()->swipe(paper,700,300,120,300));Thread.sleep(650);check(((Integer)field(album[0],"page"))==2,"newest boundary does not wrap");
            main(()->{album[0].dispose();host[0].removeAllViews();detail[0]=new FavoriteDetailView(activity,photos.get(2),p,false,()->{},()->{},()->{});host[0].addView(detail[0],new FrameLayout.LayoutParams(-1,-1));detail[0].content(img,"2026年11月13日 11:20:30","中国 · 上海市 · 徐汇区");});
            EditText story=(EditText)field(detail[0],"story");
            main(()->story.setText("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO"));
            check(story.getText().toString().length()==40,"detail input limits 41 characters to 40");
            check(MemoryNotes.get(p,photos.get(2).identity()).length()==40,"TextWatcher commits story");
            main(()->story.setText("山河故事，独家记忆。"));
            Bitmap exported=FavoriteDetailView.renderExport(img,"2026年11月13日 11:20:30","中国 · 上海市 · 徐汇区",story.getText().toString());
            check(exported.getWidth()==1080&&exported.getHeight()>900,"content export has complete image and text dimensions");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getCacheDir(),"qa194_content.png"))){check(exported.compress(Bitmap.CompressFormat.PNG,100,out),"content export PNG serializes");}exported.recycle();
            main(()->{detail[0].dispose();host[0].removeAllViews();});p.edit().remove(MemoryNotes.key(photos.get(2).identity())).commit();
            result.putString("stream",report.toString()+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream",report.toString()+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
    private static boolean hasDescription(View v,String part){if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasDescription(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static void swipe(View view,float x1,float y1,float x2,float y2){long d=SystemClock.uptimeMillis();send(view,d,d,0,new float[]{x1},new float[]{y1});for(int i=1;i<=10;i++)send(view,d,d+i*20,2,new float[]{x1+(x2-x1)*i/10},new float[]{y1+(y2-y1)*i/10});send(view,d,d+230,1,new float[]{x2},new float[]{y2});}
    private static void send(View v,long down,long now,int action,float[] x,float[] y){MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[x.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[x.length];for(int i=0;i<x.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=x[i];coords[i].y=y[i];coords[i].pressure=1;coords[i].size=1;}MotionEvent e=MotionEvent.obtain(down,now,action,x.length,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);v.dispatchTouchEvent(e);e.recycle();}
}
