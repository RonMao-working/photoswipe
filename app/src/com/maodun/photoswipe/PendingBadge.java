package com.maodun.photoswipe;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Both pending actions share the old star badge's rounded-square frame and normalized geometry. */
final class PendingBadge extends View {
    private final boolean delete;
    private final int color;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    PendingBadge(Context c,boolean delete,int red,int gold){super(c);this.delete=delete;color=delete?red:gold;setContentDescription(delete?"待删红叉角标":"待星五角星角标");}
    protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),s=Math.min(w,h),cx=w/2f,cy=h/2f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xDD6A5320);c.drawRoundRect(new RectF(0,0,w,h),s*.2f,s*.2f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,s*.035f));p.setColor(0x80F5D9A3);c.drawRoundRect(new RectF(.6f,.6f,w-.6f,h-.6f),s*.2f,s*.2f,p);
        float box=s*.58f;p.setColor(color);p.setStrokeWidth(box*.16f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(delete){p.setStyle(Paint.Style.STROKE);float a=box*.36f;c.drawLine(cx-a,cy-a,cx+a,cy+a,p);c.drawLine(cx+a,cy-a,cx-a,cy+a,p);}
        else{p.setStyle(Paint.Style.FILL);Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?box*.5f:box*.22f;float x=cx+r*(float)Math.cos(a),y=cy+r*(float)Math.sin(a);if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();c.drawPath(star,p);}
    }
}
