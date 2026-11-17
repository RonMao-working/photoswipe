package com.maodun.photoswipe;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.InputStream;

/** Applies file orientation only to raw pixels. System decoders already honor valid EXIF. */
final class PhotoOrientation {
    static final class Info {
        final int orientation; final boolean hasExif;
        Info(int o,boolean e){orientation=o;hasExif=e;}
    }
    static Info read(ContentResolver cr,Uri uri,int mediaRotation){
        try(InputStream in=cr.openInputStream(uri)){
            if(in!=null){ExifInterface ex=new ExifInterface(in);String attr=ex.getAttribute(ExifInterface.TAG_ORIENTATION);
                int o=ex.getAttributeInt(ExifInterface.TAG_ORIENTATION,0);
                if(attr!=null&&o>=1&&o<=8)return new Info(o,true);
            }
        }catch(Exception ignored){}
        return new Info(fromDegrees(mediaRotation),false);
    }
    static int fromDegrees(int deg){switch(deg){case 90:return 6;case 180:return 3;case 270:return 8;default:return 1;}}
    static Bitmap raw(Bitmap b,int o){
        if(b==null||o<=1||o>8)return b;
        Matrix m=new Matrix();
        switch(o){
            case 2:m.setValues(new float[]{-1,0,0,0,1,0,0,0,1});break;
            case 3:m.setValues(new float[]{-1,0,0,0,-1,0,0,0,1});break;
            case 4:m.setValues(new float[]{1,0,0,0,-1,0,0,0,1});break;
            case 5:m.setValues(new float[]{0,1,0,1,0,0,0,0,1});break;
            case 6:m.setValues(new float[]{0,-1,0,1,0,0,0,0,1});break;
            case 7:m.setValues(new float[]{0,-1,0,-1,0,0,0,0,1});break;
            case 8:m.setValues(new float[]{0,1,0,-1,0,0,0,0,1});break;
        }
        Bitmap upright=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
        if(upright!=b)b.recycle();return upright;
    }
    /** Missing EXIF + 90/270 media fallback only with unambiguous raw aspect evidence. */
    static Bitmap system(Bitmap b,Info info,int rawW,int rawH){
        if(b==null||info.hasExif||(info.orientation!=6&&info.orientation!=8)||rawW<=0||rawH<=0)return b;
        double actual=b.getWidth()/(double)b.getHeight(),raw=rawW/(double)rawH;
        if(Math.abs(raw-1)<.01)return b;
        if(Math.abs(Math.log(actual/raw))<.025&&Math.abs(Math.log(actual*raw))>.05)return raw(b,info.orientation);
        return b;
    }
}
