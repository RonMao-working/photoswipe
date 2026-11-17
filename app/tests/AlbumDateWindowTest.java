package com.maodun.photoswipe;
import java.util.*;
public class AlbumDateWindowTest {
    static long time(int y,int m,int d){Calendar c=Calendar.getInstance();c.set(y,m-1,d,12,0,0);return c.getTimeInMillis();}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        AlbumDateWindow w=new AlbumDateWindow(time(2026,11,12));
        check(w.start()==AlbumDateWindow.day(time(2023,12,1)).getTimeInMillis(),"36month start");
        check(w.end()==AlbumDateWindow.day(time(2026,11,30)).getTimeInMillis(),"36month end");
        check(w.dateAt(w.days()-1)==w.end(),"end index");
        for(int i=0;i<w.days();i++)check(w.indexOf(w.dateAt(i))==i,"roundtrip");
        w.include(time(2019,2,15));check(w.start()<time(2019,2,15)&&w.end()>time(2019,2,15),"old date visible");
        Calendar a=AlbumDateWindow.day(w.start()),b=AlbumDateWindow.day(w.end());
        check((b.get(Calendar.YEAR)-a.get(Calendar.YEAR))*12+b.get(Calendar.MONTH)-a.get(Calendar.MONTH)==35,"36 calendar months");
        w.include(time(2028,2,29));check(w.indexOf(time(2028,2,29))>=0,"leap date");
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));w.reset(time(2026,11,12));
        for(int i=0;i<w.days();i++)check(w.indexOf(w.dateAt(i))==i,"DST roundtrip");
        System.out.println("PASS: 36months, movable old window, leap day, all-day and DST roundtrips");
    }
}
