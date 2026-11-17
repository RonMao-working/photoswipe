package com.maodun.photoswipe;

import java.util.Calendar;

/** Local calendar arithmetic: 36 calendar months, not a fixed 730-day approximation. */
final class AlbumDateWindow {
    private Calendar start, end;
    AlbumDateWindow(long now) { reset(now); }
    static Calendar day(long time) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c;
    }
    void reset(long now) {
        end = day(now); end.set(Calendar.DAY_OF_MONTH, 1); end.add(Calendar.MONTH, 1); end.add(Calendar.DAY_OF_MONTH, -1);
        start = (Calendar)end.clone(); start.set(Calendar.DAY_OF_MONTH, 1); start.add(Calendar.MONTH, -35);
    }
    void include(long date) {
        if (date >= start() && date <= end()) return;
        start = day(date); start.set(Calendar.DAY_OF_MONTH, 1); start.add(Calendar.MONTH, -17);
        end = (Calendar)start.clone(); end.add(Calendar.MONTH, 36); end.add(Calendar.DAY_OF_MONTH, -1);
    }
    long start() { return start.getTimeInMillis(); }
    long end() { return end.getTimeInMillis(); }
    int days() { Calendar c=(Calendar)start.clone(); int n=1; while(c.before(end)){c.add(Calendar.DAY_OF_MONTH,1);n++;}return n; }
    long dateAt(int index) { Calendar c=(Calendar)start.clone(); c.add(Calendar.DAY_OF_MONTH, Math.max(0,Math.min(days()-1,index))); return c.getTimeInMillis(); }
    int indexOf(long time) {
        Calendar c=(Calendar)start.clone(); long t=day(time).getTimeInMillis(); int n=0;
        while(c.getTimeInMillis()<t && c.before(end)){c.add(Calendar.DAY_OF_MONTH,1);n++;}return n;
    }
}
