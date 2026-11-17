package com.maodun.photoswipe;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Dedicated detail-image viewport. Zoom affects only photo, never the paper or text. */
final class ZoomPhotoView extends View {
    private Bitmap image;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector detector;
    private float scale=1,tx,ty,lastX,lastY,downX,downY;
    private boolean pinched;
    ZoomPhotoView(Context c){super(c);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
        public boolean onScale(ScaleGestureDetector d){scale=Math.max(1,Math.min(4,scale*d.getScaleFactor()));bound();invalidate();return true;}
    });setContentDescription("照片，双指缩放，放大后单指移动，轻点复位");}
    void setImage(Bitmap b){image=b;reset();}
    Bitmap image(){return image;}
    float zoom(){return scale;}
    void reset(){scale=1;tx=ty=0;invalidate();}
    private void bound(){
        if(image==null)return;float fit=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
        float mx=Math.max(0,(image.getWidth()*fit*scale-getWidth())/2),my=Math.max(0,(image.getHeight()*fit*scale-getHeight())/2);
        tx=Math.max(-mx,Math.min(mx,tx));ty=Math.max(-my,Math.min(my,ty));
    }
    protected void onDraw(Canvas c){c.drawColor(0xFFE7E0D2);if(image==null){p.setColor(0xFF8B7A5F);p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);c.drawText("照片读取中…",20,getHeight()/2f,p);return;}
        float fit=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());float w=image.getWidth()*fit*scale,h=image.getHeight()*fit*scale;
        c.save();c.clipRect(0,0,getWidth(),getHeight());c.drawBitmap(image,null,new RectF((getWidth()-w)/2+tx,(getHeight()-h)/2+ty,(getWidth()+w)/2+tx,(getHeight()+h)/2+ty),p);c.restore();
    }
    public boolean onTouchEvent(MotionEvent e){getParent().requestDisallowInterceptTouchEvent(true);detector.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=lastY=e.getY();pinched=false;return true;}
        if(e.getPointerCount()>1){pinched=true;return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(scale>1&&!pinched){tx+=e.getX()-lastX;ty+=e.getY()-lastY;bound();invalidate();}lastX=e.getX();lastY=e.getY();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!pinched&&Math.abs(e.getX()-downX)<6&&Math.abs(e.getY()-downY)<6&&scale>1){performClick();}getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;
    }
    public boolean performClick(){super.performClick();reset();return true;}
}
