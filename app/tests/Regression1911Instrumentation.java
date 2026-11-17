package com.maodun.photoswipe;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.BitmapDrawable;import android.net.Uri;import android.os.*;import android.provider.MediaStore;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
/** API34 native startup/candidate/film regression, never bundled into product. */
public class Regression1911Instrumentation extends Instrumentation {
 MainActivity a;int checks;boolean baseline;StringBuilder log=new StringBuilder();List<MainActivity.PhotoItem> fixtures=new ArrayList<>();
 Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 Object call(Object o,String n,Object... args)throws Exception{for(Method m:o.getClass().getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(n);}
 void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;log.append("PASS ").append(s).append('\n');}
 void action(String n,Object... args){main(()->{try{call(a,n,args);}catch(Exception e){throw new RuntimeException(e);}});}
 MainActivity.PhotoItem current()throws Exception{return (MainActivity.PhotoItem)field(a,"current");}
 Deque<MainActivity.PhotoItem> queue()throws Exception{return (Deque<MainActivity.PhotoItem>)field(a,"pending");}
 Bitmap bitmap(String n)throws Exception{android.graphics.drawable.Drawable d=((ImageView)field(a,n)).getDrawable();return d instanceof BitmapDrawable?((BitmapDrawable)d).getBitmap():null;}
 void ready()throws Exception{for(int i=0;i<900;i++){if((Boolean)field(a,"previewReady")&&current()!=null&&queue().peek()!=null&&((Long)field(a,"backVerifiedId"))==queue().peek().id&&bitmap("ivBack")!=null)return;Thread.sleep(20);}throw new AssertionError("candidate not ready");}
 void drained()throws Exception{ExecutorService io=(ExecutorService)field(a,"io");CountDownLatch done=new CountDownLatch(2),release=new CountDownLatch(1);for(int i=0;i<2;i++)io.execute(()->{done.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(Exception ignored){}});if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("IO drain timeout");release.countDown();Thread.sleep(100);waitForIdleSync();}
 void seed(int first)throws Exception{drained();main(()->{try{set(a,"current",fixtures.get(first));Deque<MainActivity.PhotoItem> q=queue();q.clear();for(int i=first+1;i<fixtures.size();i++)q.add(fixtures.get(i));call(a,"renderCurrent");}catch(Exception e){throw new RuntimeException(e);}});ready();}
 void publish()throws Exception{String prefix="qa1911-"+SystemClock.uptimeMillis()+"-";for(int i=0;i<6;i++){ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,prefix+i+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PhotoSwipe-QA");v.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);Bitmap b=Bitmap.createBitmap(320,480,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);int tint=(int)(SystemClock.uptimeMillis()%251);c.drawColor(Color.rgb((40+i*25+tint)%256,(80+i*10+tint)%256,(160-i*12+tint)%256));Paint p=new Paint();p.setColor(Color.WHITE);p.setTextSize(36);c.drawText("QA "+i,40,80,p);try(java.io.OutputStream out=a.getContentResolver().openOutputStream(uri)){b.compress(Bitmap.CompressFormat.JPEG,95,out);}b.recycle();v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);a.getContentResolver().update(uri,v,null,null);}
 for(MainActivity.PhotoItem item:(List<MainActivity.PhotoItem>)call(a,"queryPhotos"))if(item.name.startsWith(prefix))fixtures.add(item);fixtures.sort((x,y)->x.name.compareTo(y.name));check(fixtures.size()==6,"six unique QA MediaStore files discovered");}
 void shot(String name)throws Exception{main(()->{try{View root=(View)field(a,"root");Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){throw new RuntimeException(e);}});}
 void filmCheck(int width,boolean light)throws Exception{Object f=field(a,"filmFrame");View film=(View)f;float den=a.getResources().getDisplayMetrics().density;int[] left={0};main(()->{try{((TextView)field(a,"countDelete")).setText(width==0?"5":"1780");((TextView)field(a,"countStar")).setText(width==0?"0":"100");((TextView)field(a,"countKeep")).setText(width==0?"1472":"999999");}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(100);main(()->{View panel=(View)((TextView)getUnchecked("countDelete")).getParent();left[0]=panel.getLeft();});int reserve=(Integer)field(f,"bottomCounterWidth");check(reserve==film.getWidth()-left[0],"measured counter width tracks digits "+light+"/"+width);
 Bitmap b=Bitmap.createBitmap(film.getWidth(),film.getHeight(),Bitmap.Config.ARGB_8888);main(()->film.draw(new Canvas(b)));int y=b.getHeight()-Math.round(14*den),base=light?0xFF59432B:0xFF1B150D;boolean clean=true;for(int x=Math.max(0,left[0]-Math.round(6*den));x<left[0];x++){int color=b.getPixel(x,y);if(Math.abs(Color.red(color)-Color.red(base))>8||Math.abs(Color.green(color)-Color.green(base))>8||Math.abs(Color.blue(color)-Color.blue(base))>8)clean=false;}check(clean,"counter left seam has only film base no half hole "+light+"/"+width);b.recycle();}
 Object getUnchecked(String n){try{return field(a,n);}catch(Exception e){throw new RuntimeException(e);}}
 void coldReset()throws Exception{main(()->{try{set(a,"backVerifiedId",Long.valueOf(-1));set(a,"backRequest",null);set(a,"backRequestGeneration",Long.valueOf(-1));((ImageView)field(a,"ivBack")).setImageBitmap(null);((ImageView)field(a,"ivBackBg")).setImageBitmap(null);((View)field(a,"cardBack")).setVisibility(View.INVISIBLE);}catch(Exception e){throw new RuntimeException(e);}});}
 void fadeChecks()throws Exception{coldReset();runOnMainSync(()->{try{call(a,"renderCurrent");}catch(Exception e){throw new RuntimeException(e);}});final View back=(View)field(a,"cardBack");float min=1f;boolean visible=false;long start=SystemClock.uptimeMillis();while(SystemClock.uptimeMillis()-start<2500){float al=back.getAlpha();if(al<min)min=al;if(back.getVisibility()==View.VISIBLE)visible=true;Thread.sleep(3);}
 check(visible,"candidate card becomes visible during reveal");check(min<0.9f,"candidate fades in instead of popping");Thread.sleep(150);check(back.getAlpha()==1f,"candidate reveal settles fully opaque");}
 void starChecks()throws Exception{seed(1);MainActivity.PhotoItem it=fixtures.get(0);List<MainActivity.PhotoItem> album=(List<MainActivity.PhotoItem>)field(a,"all");((Set<Long>)field(a,"starred")).add(it.id);
 check((Boolean)call(a,"isReviewed",it),"collected photo is skipped by the slide deck");
 album.add(it);action("releaseFromStars",it);
 check(!(Boolean)call(a,"isReviewed",it),"unstarred photo is allowed back into the deck");
 check(queue().contains(it),"unstarred photo waits at the end of the candidate queue");
 check(!((Set<Long>)field(a,"starred")).contains(it.id),"unstar drops the photo from the collected set");
 album.remove(it);queue().remove(it);}
 void tapChecks()throws Exception{final float[] y=new float[2];main(()->{try{View card=(View)field(a,"cardFront");View info=(View)field(a,"infoBlock");y[0]=card.getHeight()*0.45f;y[1]=info.getTop()+a.getResources().getDisplayMetrics().density*6f;}catch(Exception e){throw new RuntimeException(e);}});
 check(!(Boolean)call(a,"isDetailTapTarget",y[0]),"tap on photo surface never opens photo info");
 check((Boolean)call(a,"isDetailTapTarget",y[1]),"tap on info text row opens photo info");
 MainActivity.PhotoItem frontBefore=current();
 main(()->{try{View card=(View)field(a,"cardFront");long t=SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,card.getWidth()/2f,y[0],0);card.dispatchTouchEvent(d);d.recycle();MotionEvent u=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,card.getWidth()/2f,y[0],0);card.dispatchTouchEvent(u);u.recycle();}catch(Exception e){throw new RuntimeException(e);}});
 check(current()==frontBefore&&((Float)field(a,"zoom"))==1f&&!((Boolean)call(a,"isReviewed",frontBefore)),"photo tap leaves card, zoom and queue untouched");}
 public void onCreate(Bundle args){super.onCreate(args);baseline=args!=null&&"true".equals(args.getString("baseline"));start();}
 public void onStart(){Bundle result=new Bundle();try{a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1800);
 action("showBrowsing");drained();coldReset();action("renderCurrent");ready();check(bitmap("ivBack")!=null,"startup candidate has decoded pixels");
 if(baseline){check(((View)field(a,"cardBack")).getVisibility()==View.INVISIBLE,"v1910 repro: decoded startup candidate remains invisible");result.putString("stream",log+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,result);return;}
 check(((View)field(a,"cardBack")).getVisibility()==View.VISIBLE,"startup candidate visible without visiting My tab");check(queue().peek().id!=current().id,"startup candidate identity differs from current");Bitmap before=bitmap("ivBack");action("renderBack");check(bitmap("ivBack")==before&&((View)field(a,"cardBack")).getVisibility()==View.VISIBLE,"same-generation request keeps ready candidate pixels");tapChecks();fadeChecks();
 publish();seed(0);Set<Long> processed=new HashSet<>();for(int i=0;i<3;i++){MainActivity.PhotoItem front=current(),next=queue().peek();check(processed.add(front.id)&&bitmap("ivBack")!=null&&((View)field(a,"cardBack")).getVisibility()==View.VISIBLE,"first consecutive swipe has visible next card "+i);action("commit",1);Thread.sleep(700);ready();check(current()==next,"next card promoted after native KEEP animation "+i);check(!queue().contains(front)&&((Boolean)call(a,"isReviewed",front)),"committed QA photo absent from candidate queue "+i);}
 for(int i=0;i<3;i++){action("undo");Thread.sleep(200);ready();}MainActivity.PhotoItem want=fixtures.get(0),got=current();check(got==want,"QA decisions undone without deleting original files got="+(got==null?"null":got.id)+" want="+want.id);
 seed(0);MainActivity.PhotoItem front=current();fixtures.get(1).digest=front.digest;action("renderCurrent");ready();check(queue().peek()==fixtures.get(2),"candidate same bytes as current skipped before display");fixtures.get(1).digest=null;
 seed(0);action("showMyPage");Thread.sleep(250);check((Integer)field(a,"activeTab")==0&&((View)field(a,"deck")).getVisibility()==View.GONE,"leaving slide keeps old async candidate offscreen");action("showBrowsing");ready();check(((View)field(a,"cardBack")).getVisibility()==View.VISIBLE,"My to Slide restores verified next card");
 seed(0);drained();ExecutorService io=(ExecutorService)field(a,"io");CountDownLatch blocked=new CountDownLatch(2),release=new CountDownLatch(1);for(int i=0;i<2;i++)io.execute(()->{blocked.countDown();try{release.await(4,TimeUnit.SECONDS);}catch(Exception ignored){}});check(blocked.await(2,TimeUnit.SECONDS),"QA holds both reader threads for stale callback test");action("renderCurrent");action("showSettlement",false);release.countDown();Thread.sleep(700);check((Integer)field(a,"activeTab")==2&&((View)field(a,"cardBack")).getVisibility()==View.GONE,"late candidate result cannot revive settlement card");action("showBrowsing");ready();check(((View)field(a,"cardBack")).getVisibility()==View.VISIBLE,"settlement return requeues valid candidate");
 filmCheck(0,false);filmCheck(1,false);action("updateTopBar");shot("qa1911_slide_dark.png");action("showSettlement",false);check((Integer)field(field(a,"filmFrame"),"bottomCounterWidth")>0,"settlement shares measured sprocket boundary");shot("qa1911_settle_dark.png");
 main(()->{try{set(a,"lightMode",true);call(a,"rebuildTheme");}catch(Exception e){throw new RuntimeException(e);}});filmCheck(0,true);filmCheck(1,true);action("showBrowsing");ready();shot("qa1911_slide_light.png");starChecks();swipeChecks();main(()->{try{set(a,"lightMode",false);call(a,"rebuildTheme");}catch(Exception e){throw new RuntimeException(e);}});action("startDeck");

 result.putString("stream",log+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",log+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
 void swipeOnce(final float dx,final float dy,final float vx,final float vy){main(()->{try{call(a,"decide",dx,dy,vx,vy);}catch(Exception e){throw new RuntimeException(e);}});}
 void swipeChecks()throws Exception{
  View card=(View)field(a,"cardFront");
  int w=Math.max(1,card.getWidth());
  seed(0);MainActivity.PhotoItem it=current();int hs=((java.util.List)field(a,"history")).size();
  swipeOnce((float)(w*0.20),0f,0f,0f);Thread.sleep(500);ready();
  check(current()!=it&&((java.util.List)field(a,"history")).size()==hs+1,"20 percent horizontal drag commits");
  seed(0);it=current();
  swipeOnce((float)(w*0.10),0f,0f,0f);Thread.sleep(250);ready();
  check(current()==it,"short slow drag snaps back without committing");
  seed(0);it=current();hs=((java.util.List)field(a,"history")).size();
  swipeOnce((float)(w*0.07),0f,2000f,0f);Thread.sleep(500);ready();
  check(current()!=it&&((java.util.List)field(a,"history")).size()==hs+1,"fast flick commits below distance threshold");
 }

}
