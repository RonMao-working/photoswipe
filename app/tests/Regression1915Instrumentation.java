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

/** Isolated synthetic memory pages; never touches user review/star records or originals. */
public final class Regression1915Instrumentation extends Instrumentation {
 MainActivity activity; FavoriteDetailView page; FrameLayout host; SharedPreferences prefs;
 MainActivity.PhotoItem item; int checks; StringBuilder report=new StringBuilder();
 Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');}
 void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 void open(boolean editable,String location){main(()->{if(page!=null)page.dispose();host.removeAllViews();page=new FavoriteDetailView(activity,item,prefs,false,()->{},()->{},()->{});host.addView(page,new FrameLayout.LayoutParams(-1,-1));Bitmap image=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);new Canvas(image).drawColor(0xFF467681);page.content(image,"2026年11月17日 12:00:00",location,editable);});}
 void edit(String value,boolean save)throws Exception{
  main(()->{try{((TextView)field(page,"place")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
  AlertDialog dialog=(AlertDialog)field(page,"placeDialog");check(dialog!=null&&dialog.isShowing(),"tap address opens editing dialog");
  EditText input=dialog.findViewById(android.R.id.edit);if(input==null)input=findEdit(dialog.getWindow().getDecorView()); final EditText editor=input;
  check(editor!=null,"place editor is present");
  main(()->{editor.setText(value);dialog.getButton(save?AlertDialog.BUTTON_POSITIVE:AlertDialog.BUTTON_NEGATIVE).performClick();});
 }
 EditText findEdit(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){EditText e=findEdit(((android.view.ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
 View download(View v){if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("下载"))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View found=download(((android.view.ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
 Bitmap saved(String evidence)throws Exception{
  long before=0;try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},null,null,"_id DESC")){if(c!=null&&c.moveToFirst())before=c.getLong(0);}
  main(()->download(page).performClick());for(int i=0;i<200&&(Boolean)field(page,"exporting");i++)Thread.sleep(50);
  check(!(Boolean)field(page,"exporting"),"download completes");Uri found=null;
  try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","is_pending"},"_id>?",new String[]{String.valueOf(before)},"_id DESC")){while(c!=null&&c.moveToNext())if(c.getString(1).startsWith("相册滑选-记忆-")){check(c.getInt(2)==0,"download is published to public album");found=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));break;}}
  check(found!=null,"download click produces public image");Bitmap bitmap;try(java.io.InputStream in=activity.getContentResolver().openInputStream(found)){bitmap=BitmapFactory.decodeStream(in);}
  check(bitmap!=null&&bitmap.getWidth()==1080,"saved PNG decodes at 1080 width");
  try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),evidence))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}return bitmap;
 }
 void snapshot(String name){main(()->{try{Bitmap b=Bitmap.createBitmap(host.getWidth(),host.getHeight(),Bitmap.Config.ARGB_8888);host.draw(new Canvas(b));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){throw new RuntimeException(e);}});}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1800);
  check("1.9.15".equals(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName),"version is 1.9.15");
  prefs=activity.getSharedPreferences("qa-memory-1915",0);prefs.edit().clear().commit();
  item=new MainActivity.PhotoItem(191500,Uri.parse("content://photoswipe-qa/memory1915"),"synthetic",1,1794888000000L,600,900);
  host=new FrameLayout(activity);main(()->activity.setContentView(host));
  open(true,"原照片无GPS，无法提供经纬度");
  check(((TextView)field(page,"place")).getText().toString().contains("点击填写地点"),"no-GPS row invites manual place entry");
  check("".equals(field(page,"placeValue")),"empty address is not the no-GPS placeholder");snapshot("qa1915_empty_page.png");
  Bitmap empty=saved("qa1915_empty_export.png");
  edit("上海市静安区 · 苏州河畔",true);
  check("上海市静安区 · 苏州河畔".equals(MemoryNotes.getPlace(prefs,item.identity())),"manual address persists locally");
  check(((TextView)field(page,"place")).getText().toString().equals("上海市静安区 · 苏州河畔"),"saved address replaces placeholder");
  main(()->page.location("迟到的地理解析"));check(field(page,"placeValue").equals("上海市静安区 · 苏州河畔"),"late location result cannot replace manual address");
  edit("不应保存",false);check(field(page,"placeValue").equals("上海市静安区 · 苏州河畔"),"cancel leaves previous address intact");
  open(true,"无GPS");check(field(page,"placeValue").equals("上海市静安区 · 苏州河畔"),"reopening restores written address");snapshot("qa1915_written_page.png");
  Bitmap written=saved("qa1915_written_export.png");check(written.getHeight()>empty.getHeight(),"unwritten export omits address line and its space");
  Bitmap source=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);new Canvas(source).drawColor(0xFF467681);
  Bitmap expected=FavoriteDetailView.renderExport(source,"2026年11月17日 12:00:00","",""),expectedWritten=FavoriteDetailView.renderExport(source,"2026年11月17日 12:00:00","上海市静安区 · 苏州河畔","");
  check(expected.sameAs(empty),"real empty-address download exactly matches export with no location text");check(expectedWritten.sameAs(written),"real edited-address download includes the handwritten caption");
  edit("  \n \t ",true);check(MemoryNotes.getPlace(prefs,item.identity()).isEmpty(),"clearing address removes local value");open(true,"无GPS");check(field(page,"placeValue").equals(""),"cleared address remains empty on reopen");
  open(false,"上海市静安区 · 纬度31.230000°N，经度121.470000°E");check(!(Boolean)field(page,"manualPlace"),"GPS page remains read-only metadata");check(((TextView)field(page,"place")).getText().toString().contains("纬度"),"existing GPS text preserved");main(()->page.location("新的GPS地名"));check(field(page,"placeValue").equals("新的GPS地名"),"GPS geocoder update still works");
  Class<?> meta=Class.forName("com.maodun.photoswipe.MainActivity$Meta");Constructor<?> ctor=meta.getDeclaredConstructor();ctor.setAccessible(true);Object m=ctor.newInstance();Method time=MainActivity.class.getDeclaredMethod("detailTime",MainActivity.PhotoItem.class,meta);time.setAccessible(true);
  String fallback=(String)time.invoke(activity,item,m);check(!fallback.contains("文件时间")&&fallback.contains("2026年"),"fallback date has no file-time suffix");Field dt=meta.getDeclaredField("dateTime");dt.setAccessible(true);dt.set(m,"2025:10:04 09:10:11");check(((String)time.invoke(activity,item,m)).equals("2025年10月4日 09:10:11"),"EXIF time formatting preserved");
  check(MemoryNotes.cleanPlace("  北京\n海淀\t公园  ").equals("北京 海淀 公园"),"manual place trims blank and line breaks");
  source.recycle();empty.recycle();written.recycle();expected.recycle();expectedWritten.recycle();
  main(()->{page.dispose();activity.finish();});prefs.edit().clear().commit();out.putString("stream",report+"TOTAL "+checks+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",report+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
