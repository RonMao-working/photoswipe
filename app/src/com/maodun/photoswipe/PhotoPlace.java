package com.maodun.photoswipe;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** User-authorized reverse geocoding only; no HTTP upload of photo or story. */
final class PhotoPlace {
    interface Result { void ready(String text); }
    static void resolve(Context context,double lat,double lon,Result result) {
        if(!Geocoder.isPresent()){result.ready("地点未解析 · "+coords(lat,lon));return;}
        Thread worker=new Thread(()->{
            String text;
            try {
                List<Address> list=new Geocoder(context,Locale.getDefault()).getFromLocation(lat,lon,1);
                if(list==null||list.isEmpty())text="地点未解析 · "+coords(lat,lon);
                else text=display(list.get(0))+"\n"+coords(lat,lon);
            } catch(Exception e){text="地点未解析 · "+coords(lat,lon);}
            final String answer=text;
            new Handler(Looper.getMainLooper()).post(()->result.ready(answer));
        },"photo-place");worker.start();
    }
    static String coords(double lat,double lon){return String.format(Locale.US,"纬度 %.6f°%s · 经度 %.6f°%s",Math.abs(lat),lat>=0?"N":"S",Math.abs(lon),lon>=0?"E":"W");}
    static String display(Address a){
        LinkedHashSet<String> parts=new LinkedHashSet<>();
        for(String p:new String[]{a.getCountryName(),a.getAdminArea(),a.getSubAdminArea(),a.getLocality(),a.getSubLocality()})
            if(p!=null&&!p.trim().isEmpty())parts.add(p.trim());
        StringBuilder b=new StringBuilder();for(String p:parts){if(b.length()>0)b.append(" · ");b.append(p);}
        if(b.length()==0)return "地点未解析 · "+coords(a.getLatitude(),a.getLongitude());
        return b.toString();
    }
}
