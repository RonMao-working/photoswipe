package com.maodun.photoswipe;

public final class AlbumNavigationTest {
    private static void eq(int expected, int actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        eq(AlbumNavigation.NEXT, AlbumNavigation.deltaForSwipe(-200));
        eq(AlbumNavigation.PREVIOUS, AlbumNavigation.deltaForSwipe(200));
        eq(0, AlbumNavigation.deltaForSwipe(0));
        eq(3, AlbumNavigation.target(2, 5, AlbumNavigation.deltaForSwipe(-200)));
        eq(1, AlbumNavigation.target(2, 5, AlbumNavigation.deltaForSwipe(200)));
        eq(0, AlbumNavigation.target(0, 5, AlbumNavigation.PREVIOUS));
        eq(4, AlbumNavigation.target(4, 5, AlbumNavigation.NEXT));
        eq(0, AlbumNavigation.target(0, 0, AlbumNavigation.NEXT));
        long[] dates = {1, 2, 3, 4, 5};
        int p=2;
        if(dates[AlbumNavigation.target(p,dates.length,AlbumNavigation.deltaForSwipe(-200))] <= dates[p]) throw new AssertionError("left must be newer");
        if(dates[AlbumNavigation.target(p,dates.length,AlbumNavigation.deltaForSwipe(200))] >= dates[p]) throw new AssertionError("right must be older");
        System.out.println("AlbumNavigationTest: 10 checks passed");
    }
}
