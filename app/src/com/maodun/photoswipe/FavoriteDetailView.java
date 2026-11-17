package com.maodun.photoswipe;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Album detail and clean content export; controls are siblings, never in export rendering. */
final class FavoriteDetailView extends LinearLayout {
    private final MainActivity.PhotoItem item;
    private final SharedPreferences prefs;
    private final float density;
    private final TextView date,place,count,status;
    private final EditText story;
    private final ZoomPhotoView photo;
    private String dateValue="时间读取中…",placeValue="地点读取中…";
    private boolean disposed, exporting, manualPlace, metadataReady;
    private AlertDialog placeDialog;
    private Bitmap ownedImage;
    private final Runnable changed;
    FavoriteDetailView(Context c,MainActivity.PhotoItem item,SharedPreferences prefs,boolean light,Runnable close,Runnable remove,Runnable changed){
        super(c);this.item=item;this.prefs=prefs;this.changed=changed;density=getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);setPadding(dp(16),dp(8),dp(16),dp(12));setBackgroundColor(light?0xFFF0ECE4:0xFF17130F);
        LinearLayout nav=new LinearLayout(c);nav.setGravity(Gravity.CENTER_VERTICAL);addView(nav,new LayoutParams(-1,dp(48)));
        TextView back=button("返回相册");nav.addView(back);TextView title=text("独家记忆",17,light?0xFF3F3427:0xFFF1E6D1);title.setGravity(Gravity.CENTER);nav.addView(title,new LayoutParams(0,-2,1));
        View save=new DownloadButton(c);nav.addView(save,new LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->{persist();close.run();});save.setOnClickListener(v->savePage());
        LinearLayout paper=new LinearLayout(c);paper.setOrientation(VERTICAL);paper.setPadding(dp(14),dp(14),dp(14),dp(12));paper.setBackground(paperBackground());paper.setElevation(dp(4));addView(paper,new LayoutParams(-1,0,1));
        photo=new ZoomPhotoView(c);paper.addView(photo,new LayoutParams(-1,0,1));
        date=text(dateValue,11,0xFF78674F);place=text(placeValue,11,0xFF78674F);date.setPadding(0,dp(10),0,dp(3));paper.addView(date);paper.addView(place,new LayoutParams(-1,-2));place.setMinHeight(dp(32));place.setGravity(Gravity.CENTER_VERTICAL);place.setOnClickListener(v->{if(manualPlace)editPlace();else if(c instanceof MainActivity)((MainActivity)c).requestPhotoLocation();});
        View line=new View(c);line.setBackgroundColor(0xFFDDCFB4);LayoutParams lp=new LayoutParams(-1,dp(1));lp.topMargin=dp(12);paper.addView(line,lp);
        story=new EditText(c);story.setTextColor(0xFF514332);story.setTextSize(14);story.setHintTextColor(0xFFA49275);story.setHint(MemoryNotes.HINT);story.setBackgroundColor(Color.TRANSPARENT);story.setPadding(0,dp(12),0,dp(3));story.setMaxLines(3);story.setMinLines(2);
        story.setFilters(new InputFilter[]{(source,start,end,dest,dstart,dend)->{
            String before=dest.subSequence(0,dstart).toString(),after=dest.subSequence(dend,dest.length()).toString();int remain=40-before.codePointCount(0,before.length())-after.codePointCount(0,after.length());String incoming=source.subSequence(start,end).toString();return incoming.codePointCount(0,incoming.length())>remain?MemoryNotes.first(incoming,Math.max(0,remain)):null;
        }});story.setText(MemoryNotes.get(prefs,item.identity()));paper.addView(story,new LayoutParams(-1,-2));
        count=text("",10,0xFF9B896C);count.setGravity(Gravity.RIGHT);paper.addView(count);updateCount();
        story.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int f){}public void onTextChanged(CharSequence s,int a,int b,int n){updateCount();persist();}public void afterTextChanged(Editable e){}});
        status=text("文字自动保存 · 双指放大查看",10,light?0xFF80725F:0xFFB2A28A);status.setPadding(0,dp(8),0,dp(7));addView(status);
        LinearLayout actions=new LinearLayout(c);addView(actions,new LayoutParams(-1,dp(43)));
        TextView unstar=button("取消收藏"),open=button("在系统相册打开");actions.addView(unstar,new LayoutParams(0,-1,1));LayoutParams op=new LayoutParams(0,-1,1);op.leftMargin=dp(8);actions.addView(open,op);
        unstar.setOnClickListener(v->new AlertDialog.Builder(c).setTitle("取消这张收藏？").setMessage("原照片不会删除。").setNegativeButton("取消",null).setPositiveButton("取消收藏",(d,w)->{persist();remove.run();}).show());
        open.setOnClickListener(v->{try{c.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){Toast.makeText(c,"原照片暂不可打开",Toast.LENGTH_SHORT).show();}});
    }
    private final class DownloadButton extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        DownloadButton(Context c){super(c);setContentDescription("下载，保存这一页到相册");setFocusable(true);setClickable(true);if(Build.VERSION.SDK_INT>=26)setTooltipText("保存到相册");}
        protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f;paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFEDE2CD);canvas.drawCircle(cx,cy,dp(18),paint);paint.setColor(0xFF69543B);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.8f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path arrow=new Path();arrow.moveTo(cx,cy-dp(9));arrow.lineTo(cx,cy+dp(3));arrow.moveTo(cx-dp(5),cy-dp(2));arrow.lineTo(cx,cy+dp(3));arrow.lineTo(cx+dp(5),cy-dp(2));arrow.moveTo(cx-dp(8),cy+dp(4));arrow.lineTo(cx-dp(8),cy+dp(9));arrow.lineTo(cx+dp(8),cy+dp(9));arrow.lineTo(cx+dp(8),cy+dp(4));canvas.drawPath(arrow,paint);}
    }
    MainActivity.PhotoItem photoItem(){return item;}
    private int dp(float v){return Math.round(v*density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView button(String s){TextView t=text(s,12,0xFF69543B);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),dp(8),dp(10),dp(8));GradientDrawable b=new GradientDrawable();b.setColor(0xFFEDE2CD);b.setCornerRadius(dp(19));t.setBackground(b);return t;}
    private GradientDrawable paperBackground(){GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFFD1C1A3,0xFFF9F3E5,0xFFF3E8D3});b.setCornerRadius(dp(4));b.setStroke(dp(1),0xFFCCB993);return b;}
    private void updateCount(){String s=story.getText().toString();count.setText(s.codePointCount(0,s.length())+" / 40");}
    private void persist(){MemoryNotes.save(prefs,item.identity(),story.getText().toString());changed.run();}
    void content(Bitmap b,String time,String location){content(b,time,location,false);}
    void content(Bitmap b,String time,String location,boolean editable){
        if(disposed){if(b!=null)b.recycle();return;}ownedImage=b;photo.setImage(b);dateValue=time;date.setText(time);
        manualPlace=editable;metadataReady=true;
        placeValue=editable?MemoryNotes.getPlace(prefs,item.identity()):location;showPlace();
    }
    private void showPlace(){
        place.setText(manualPlace?(placeValue.isEmpty()?"地点未填写 · 点击填写地点":placeValue):placeValue);
        place.setContentDescription(manualPlace?"编辑地点，"+(placeValue.isEmpty()?"未填写":placeValue):placeValue);
    }
    private void editPlace(){
        if(disposed||!manualPlace||placeDialog!=null)return;
        EditText input=new EditText(getContext());input.setSingleLine(true);input.setTextSize(16);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("填写拍摄地点");input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        input.setText(placeValue);input.setSelection(input.length());
        LinearLayout box=new LinearLayout(getContext());box.setPadding(dp(20),dp(8),dp(20),0);box.addView(input,new LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle("编辑地点").setView(box)
                .setNegativeButton("取消",null).setPositiveButton("保存",null).create();placeDialog=dialog;
        dialog.setOnDismissListener(d->{if(placeDialog==dialog)placeDialog=null;});
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String value=MemoryNotes.cleanPlace(input.getText().toString());
                if(!MemoryNotes.savePlace(prefs,item.identity(),value)){Toast.makeText(getContext(),"地点保存失败，请重试",Toast.LENGTH_SHORT).show();return;}
                placeValue=value;showPlace();changed.run();dialog.dismiss();
            });
            input.requestFocus();
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }
    void location(String text){if(disposed||manualPlace)return;placeValue=text;showPlace();}
    void dispose(){if(disposed)return;persist();disposed=true;if(placeDialog!=null)placeDialog.dismiss();photo.setImage(null);releaseImage();}
    private synchronized void releaseImage(){if(disposed&&!exporting&&ownedImage!=null){ownedImage.recycle();ownedImage=null;}}
    private void savePage(){
        if(exporting)return;
        if(photo.image()==null){Toast.makeText(getContext(),"照片尚未读取完成",Toast.LENGTH_SHORT).show();return;}
        if(!metadataReady){Toast.makeText(getContext(),"时间地点正在读取，请稍后保存",Toast.LENGTH_SHORT).show();return;}
        persist();final String note=story.getText().toString().trim(),when=dateValue,where=placeValue;final Bitmap original=photo.image();exporting=true;status.setText("正在保存…");
        new Thread(()->{
            Uri uri=null;File legacyFile=null;Bitmap output=null;boolean ok=false;
            try{
                output=renderExport(original,when,where,note);String filename="相册滑选-记忆-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date())+".png";
                ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,filename);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                if(Build.VERSION.SDK_INT>=29){values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/相册滑选");values.put(MediaStore.Images.Media.IS_PENDING,1);uri=getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("insert failed");try(OutputStream out=getContext().getContentResolver().openOutputStream(uri)){if(out==null||!output.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("write failed");}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);if(getContext().getContentResolver().update(uri,values,null,null)<1)throw new IOException("publish failed");}
                else {File dir=new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),"相册滑选");if(!dir.exists()&&!dir.mkdirs())throw new IOException("directory failed");legacyFile=new File(dir,filename);try(OutputStream out=new FileOutputStream(legacyFile)){if(!output.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("write failed");}android.media.MediaScannerConnection.scanFile(getContext(),new String[]{legacyFile.getAbsolutePath()},new String[]{"image/png"},null);}
                ok=true;
            }catch(Exception e){if(uri!=null)try{getContext().getContentResolver().delete(uri,null,null);}catch(Exception ignored){}if(legacyFile!=null)legacyFile.delete();}
            finally{if(output!=null)output.recycle();}
            final boolean success=ok;post(()->{exporting=false;releaseImage();if(!disposed){status.setText(success?"已保存到相册 · Pictures/相册滑选":"保存失败，请检查存储权限");Toast.makeText(getContext(),success?"这一页已保存到手机相册":"保存失败，未生成可用图片",Toast.LENGTH_SHORT).show();}});
        },"memory-export").start();
    }
    /** Complete fit image and metadata, no buttons, hint, editing border, counter or system UI. */
    static Bitmap renderExport(Bitmap image,String time,String place,String story){
        int width=1080,pad=66;float fit=Math.min((width-pad*2f)/image.getWidth(),1400f/image.getHeight());int imageH=Math.round(image.getHeight()*fit);
        TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setColor(0xFF756249);tp.setTextSize(29);
        StaticLayout where=place==null||place.trim().isEmpty()?null:new StaticLayout(place,tp,width-pad*2,Layout.Alignment.ALIGN_NORMAL,1.15f,0,false);
        int placeHeight=where==null?0:where.getHeight();
        TextPaint notePaint=new TextPaint(tp);notePaint.setTextSize(39);notePaint.setColor(0xFF54442F);
        StaticLayout memory=new StaticLayout(story,notePaint,width-pad*2,Layout.Alignment.ALIGN_NORMAL,1.25f,0,false);
        int h=pad+imageH+40+40+placeHeight+(where==null?16:36)+(story.isEmpty()?0:memory.getHeight()+28)+pad;
        Bitmap output=Bitmap.createBitmap(width,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(output);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        c.drawColor(0xFFC5B392);p.setColor(0xFFF5ECD9);c.drawRect(18,18,width-18,h-18,p);p.setColor(0x30735A36);p.setStrokeWidth(1);for(int y=20;y<h-20;y+=12)c.drawLine(20,y,width-20,y,p);
        float iw=image.getWidth()*fit;RectF dst=new RectF((width-iw)/2,pad,(width+iw)/2,pad+imageH);p.setShadowLayer(9,0,5,0x40000000);p.setColor(0xFFFFFCF4);c.drawRect(dst.left-15,dst.top-15,dst.right+15,dst.bottom+15,p);p.clearShadowLayer();c.drawBitmap(image,null,dst,p);
        int y=pad+imageH+52;c.drawText(time,pad,y,tp);y+=20;
        if(where!=null){c.save();c.translate(pad,y);where.draw(c);c.restore();y+=placeHeight+34;}else y+=14;
        if(!story.isEmpty()){p.setColor(0xFFCCBA99);c.drawRect(pad,y,width-pad,y+2,p);y+=25;c.save();c.translate(pad,y);memory.draw(c);c.restore();}return output;
    }
}
