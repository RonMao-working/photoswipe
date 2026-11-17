package com.maodun.photoswipe;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.database.Cursor;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only product state regression. Never included in src or release APK. */
public final class Regression195Instrumentation extends Instrumentation {
    MainActivity a;int n;StringBuilder log=new StringBuilder();
    Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    void invoke(String name)throws Exception{call(a,name,new Class[]{});}
    void onMain(Runnable r){runOnMainSync(r);}
    void action(String name)throws Exception{final Throwable[] error={null};onMain(()->{try{invoke(name);}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    void check(boolean ok,String s){if(!ok)throw new AssertionError(s);n++;log.append("PASS ").append(s).append('\n');}
    void ready()throws Exception{for(int i=0;i<160;i++){if((Boolean)get(a,"previewReady")&&(Integer)get(a,"activeTab")==1&&get(a,"current")!=null)return;Thread.sleep(50);}throw new AssertionError("preview not ready");}
    MainActivity.PhotoItem current()throws Exception{return (MainActivity.PhotoItem)get(a,"current");}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle b=new Bundle();try{
        Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);a=(MainActivity)startActivitySync(i);Thread.sleep(2000);action("showBrowsing");ready();
        TextView[] tabs=(TextView[])get(a,"tabs");check(tabs.length==3&&tabs[0].getText().toString().equals("我的")&&tabs[1].getText().toString().equals("滑选")&&tabs[2].getText().toString().equals("结算"),"three ordered tabs");
        check(tabs[0].getWidth()==tabs[1].getWidth()&&Math.abs(tabs[1].getWidth()-tabs[2].getWidth())<=1,"equal tab widths");
        MainActivity.PhotoItem first=current();onMain(()->tabs[2].performClick());check((Integer)get(a,"activeTab")==2,"settlement tab selected");
        onMain(()->tabs[0].performClick());check((Integer)get(a,"activeTab")==0&&tabs[0].isShown(),"my from settlement keeps visible tabs");
        action("closeMyPage");ready();check((Integer)get(a,"activeTab")==1&&current().id==first.id,"my return goes to swipe preserving unprocessed current");
        // Commit and immediately navigate before the 240ms animator completes.
        final long kept=first.id;onMain(()->{try{call(a,"commit",new Class[]{int.class},1);tabs[2].performClick();}catch(Exception e){throw new RuntimeException(e);}});
        action("showBrowsing");ready();check(current().id!=kept,"keep plus animation-time navigation cannot restore committed photo");
        Set<Long> handled=new HashSet<>();handled.add(kept);Set<String> digests=new HashSet<>();digests.add(first.digest);
        for(int z=0;z<8;z++){ready();MainActivity.PhotoItem p=current();check(!handled.contains(p.id)&&!digests.contains(p.digest),"unique current identity and bytes "+z);handled.add(p.id);digests.add(p.digest);
            onMain(()->{try{call(a,"commit",new Class[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(420);
            if(z%2==0){onMain(()->tabs[0].performClick());action("closeMyPage");}else{onMain(()->tabs[2].performClick());action("showBrowsing");}
        }
        ready();check(!handled.contains(current().id)&&!digests.contains(current().digest),"processed photos excluded after repeated tab returns");
        MainActivity.PhotoItem cachedCurrent=current();((Map<?,?>)get(a,"metaCache")).clear();action("renderCurrent");ready();
        check(((Map<?,?>)get(a,"metaCache")).containsKey(cachedCurrent.id),"bitmap cache hit still refreshes metadata after permission change");
        check(!((TextView)get(a,"tvTime")).getText().toString().contains("读取中"),"refreshed metadata does not stay loading");
        TextView undo=(TextView)get(a,"btnUndo");check(undo.isShown(),"persistent undo entry remains after toast");
        MainActivity.PhotoItem beforeUndo=current();action("undo");ready();MainActivity.PhotoItem restored=current();check(handled.contains(restored.id)&&restored.id!=beforeUndo.id,"explicit undo allows intended previous photo");
        onMain(()->{try{call(a,"commit",new Class[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(400);ready();
        double zero=(Double)call(a,"parseDms",new Class[]{String.class},"0/1,0/1,0/1");double bad=(Double)call(a,"parseDms",new Class[]{String.class},"1/0,0/1,0/1");double invalid=(Double)call(a,"parseDms",new Class[]{String.class},"bad,0/1,0/1");
        check(zero==0&&Double.isNaN(bad)&&Double.isNaN(invalid),"GPS zero valid and malformed values never fabricated as zero");
        check(Double.isNaN((Double)call(a,"parseDms",new Class[]{String.class},"31/1,99/1,0/1")),"invalid DMS minute rejected");
        check(PhotoPlace.coords(31.2266667,121.469).contains("31.226667")&&PhotoPlace.coords(31.2266667,121.469).contains("121.469000"),"six decimal labeled coordinate fallback");
        MainActivity.PhotoItem gps=null;try(Cursor c=getTargetContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","_size","width","height"},"_display_name=?",new String[]{"qa195_gps_1.jpg"},null)){if(c.moveToFirst())gps=new MainActivity.PhotoItem(c.getLong(0),ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0)),c.getString(1),c.getLong(2),0,c.getInt(3),c.getInt(4));}
        check(gps!=null,"GPS synthetic source exists");Object meta=call(a,"meta",new Class[]{MainActivity.PhotoItem.class},gps);
        check((Boolean)get(meta,"hasGps")&&Math.abs((Double)get(meta,"lat")-31.2266667)<.000001,"original photo GPS parsed with permission");
        Bitmap cross=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),star=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        onMain(()->{PendingBadge x=new PendingBadge(a,true,Color.RED,Color.YELLOW),s=new PendingBadge(a,false,Color.RED,Color.YELLOW);x.layout(0,0,100,100);s.layout(0,0,100,100);x.draw(new Canvas(cross));s.draw(new Canvas(star));});
        boolean same=true;for(int y=0;y<100;y++)for(int x=0;x<100;x++)if((x<18||x>81||y<18||y>81)&&cross.getPixel(x,y)!=star.getPixel(x,y))same=false;
        check(same,"cross and star badge outer frame pixel-identical");cross.recycle();star.recycle();
        // Force current/neighbor cache misses then turn: loading is not a permission error.
        onMain(()->tabs[0].performClick());MyAlbumView album=(MyAlbumView)get(a,"myPage");Thread.sleep(1800);
        LruCache<Long,Bitmap> cache=(LruCache<Long,Bitmap>)get(album,"images");List<?> pages=(List<?>)get(album,"pages");
        if(pages.size()>1){int page=(Integer)get(album,"page"),dest=page>0?page-1:1;onMain(()->{cache.evictAll();try{call(album,"turnTo",new Class[]{int.class,boolean.class},dest,true);}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(1500);check((Integer)get(album,"page")==dest,"album waits current and destination cache misses then turns");
            View paper=(View)get(album,"paper");Bitmap frame=Bitmap.createBitmap(paper.getWidth(),paper.getHeight(),Bitmap.Config.ARGB_8888);onMain(()->paper.draw(new Canvas(frame)));java.io.File f=new java.io.File(getTargetContext().getExternalFilesDir(null),"qa195_album_cache.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(f)){frame.compress(Bitmap.CompressFormat.PNG,100,out);}frame.recycle();
        }
        action("closeMyPage");ready();check(!handled.contains(current().id),"album return still skips reviewed photos");
        b.putString("stream",log+"TOTAL "+n+" checks\n");finish(Activity.RESULT_OK,b);
    }catch(Throwable e){b.putString("stream",log+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,b);}}
}
