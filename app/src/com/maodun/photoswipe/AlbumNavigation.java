package com.maodun.photoswipe;

/** Album pages are ordered oldest first. This mapping is separate from review swipe settings. */
final class AlbumNavigation {
    static final int PREVIOUS = -1; // older
    static final int NEXT = 1;      // newer
    private AlbumNavigation() {}
    static int deltaForSwipe(float deltaX) {
        return deltaX < 0 ? NEXT : deltaX > 0 ? PREVIOUS : 0;
    }
    static int target(int page, int count, int delta) {
        if (count <= 0) return 0;
        return Math.max(0, Math.min(count - 1, page + delta));
    }
}
