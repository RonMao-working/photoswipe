package com.maodun.photoswipe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Size;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 相册滑选：左滑删除、右滑保留、上滑星标，每步可撤销，操作后立即随机下一张。
 * 纯系统框架实现，不依赖 AndroidX。
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 1001;
    private static final int REQ_DELETE = 1002;
    private static final int REQ_FAVORITE = 1003;
    private static final int REQ_FAVORITE_WRITE = 1004;
    private static final int REQ_PHOTO_LOCATION = 1006;
    private static final int REQ_PERMANENT_DELETE = 1005;

    private static final int KEEP = 1;
    private static final int DELETE = 2;
    private static final int STAR = 3;

    private int C_BG = 0xFF0B0A09;
    private int C_CARD = 0xFF151518;
    private int C_TEXT = 0xFFF2F0EB;
    private int C_DIM = 0xFF9C9A93;
    private int C_FAINT = 0xFF6D6A62;
    private int C_DELETE = 0xFFFF6B6B;
    private int C_KEEP = 0xFF4CD38A;
    private int C_STAR = 0xFFFFC14D;
    private boolean lightMode;
    private boolean advancing, systemBusy, previewReady;
    private long renderGeneration;
    private long backVerifiedId = -1;
    private PhotoItem backRequest;
    private long backRequestGeneration = -1;
    private ReviewLedger ledger;
    private DynamicPreview dynamicPreview;

    // 胶片边框尺寸（dp）
    private static final int SETTLE_BTN_H = 42;      // 结算页四个按钮同一高度
    private static final int SETTLE_BTN_RADIUS = 22; // 同一圆角
    private static final float SETTLE_BTN_TEXT = 14f; // 同一字号

    private static final int FILM_BAND = 28;
    private static final int FILM_SIDE = 0;
    private static final int BACK_REVEAL_MS = 60;    // 备选卡只做轻淡入，过长会拖慢跟手感
    private static final float BACK_REVEAL_FROM = 0.72f;  // 起点就基本可见，缩掉"空一下"的延迟

    private ContentResolver cr;
    private SharedPreferences prefs;
    private float dens;
    private int screenW;
    private int screenH;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);

    private FrameLayout root;
    private LinearLayout topBar;
    private TextView tvProgress, chipDelete, btnFinish, btnSettings, tabSwipe;
    private View btnUndo;
    private int activeTab = 1;
    private boolean navigationTouched;
    private TextView[] tabs;
    private TextView tipLeft, tipRight;
    private GlyphView glyphLeft, glyphRight;
    private final LinkedHashSet<Long> seen = new LinkedHashSet<>();
    private boolean swapSwipe = false;
    private FrameLayout deck;
    private FrameLayout cardBack, cardFront;
    private ScaleGestureDetector zoomDetector;
    private float zoom = 1f, zoomX = 0f, zoomY = 0f;
    private ImageView ivBack, ivFront, ivFrontBg, ivBackBg, ivInfoGlass;
    private TextView tvPlaceholder, tvTime, tvPlace, tvDevice, tvFile;
    private TextView tvBadge;
    private FilmFrame filmFrame;
    private TextView stampKeep, stampDelete, stampStar;
    private LinearLayout infoBlock;
    private LinearLayout actionBar;
    private LinearLayout undoBar;
    private TextView undoLabel;
    private TextView tvLimited;
    private LinearLayout gateView;
    private ScrollView summaryView;
    private MyAlbumView myPage;
    private FavoriteDetailView favoriteDetail;
    private TextView countDelete, countStar, countKeep;
    private TextView tvLoading;

    private final List<PhotoItem> all = new ArrayList<>();
    private final ArrayDeque<PhotoItem> pending = new ArrayDeque<>();
    private PhotoItem current;
    private final Map<Long, Meta> metaCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> thumbOnly = new HashSet<>();   // 原图解码失败，只剩系统缩略图
    private final Set<Long> unreadable = new HashSet<>();  // 连缩略图都拿不到
    private final LinkedHashSet<Long> starred = new LinkedHashSet<>();      // 已确认生效的星标
    private final LinkedHashSet<Long> pendingStars = new LinkedHashSet<>(); // 本轮待结算的星标
    private final LinkedHashSet<Long> deleteBucket = new LinkedHashSet<>();
    private int keptCount = 0;
    private final List<Act> history = new ArrayList<>();
    private LruCache<String, Bitmap> cache;
    private final Set<String> decoding = new HashSet<>();
    private final ArrayList<Uri> lastDeleteUris = new ArrayList<>();

    private Runnable hideUndoRunnable;

    // ===================== 数据模型 =====================

    static final class PhotoItem {
        long id;
        Uri uri;
        String name;
        long size;
        long dateTaken;
        int width;
        int height;
        int mediaRotation;
        String signature, digest;

        String identity() { return uri.toString(); }

        PhotoItem(long id, Uri uri, String name, long size, long dateTaken, int w, int h) {
            this.id = id;
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.dateTaken = dateTaken;
            this.width = w;
            this.height = h;
        }
    }

    private static final class Meta {
        int rotation = 0;
        PhotoOrientation.Info orientation;
        String dateTime;
        String make;
        String model;
        double lat = 0, lon = 0;
        boolean hasGps = false, locationAllowed=false, exifRead=false;
        int width = 0, height = 0;
    }

    private static final class Act {
        PhotoItem item;
        int type;

        Act(PhotoItem i, int t) {
            item = i;
            type = t;
        }
    }

    // ===================== 生命周期 =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cr = getContentResolver();
        prefs = getSharedPreferences("photoswipe", MODE_PRIVATE);
        ledger = new ReviewLedger(this);
        lightMode = prefs.getBoolean("light_mode", false);
        applyPalette();
        restoreLastRequests();
        dens = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;

        cache = new LruCache<String, Bitmap>(32 * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        swapSwipe = prefs.getBoolean("swap_swipe", false);
        Set<String> saved = prefs.getStringSet("starred_ids", null);
        if (saved != null) {
            for (String s : saved) {
                try {
                    starred.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);

        buildUi();

        int access = accessState();
        if (access == 0) {
            showGate();
        } else {
            if (access == 1) tvLimited.setVisibility(View.VISIBLE);
            startDeck();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        renderGeneration++;previewReady=false;
        if(advancing){cardFront.animate().cancel();resetCardVisual();advancing=false;current=null;}
        savePending();
        saveSeen();
        if (dynamicPreview != null) dynamicPreview.stop();
    }

    @Override protected void onResume(){
        super.onResume();
        if(prefs==null)return;
        if(hasLocationPermission()){metaCache.entrySet().removeIf(e->!e.getValue().locationAllowed);}
        if(activeTab==1&&deck!=null&&accessState()>0&&!all.isEmpty()&&!systemBusy){resumeDeck();}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (favoriteDetail != null) favoriteDetail.dispose();
        if (myPage != null) myPage.dispose();
        io.shutdownNow();
    }

    /** 未结算就退出：不生效，但记录留着，下次进软件还在结算页 */
    @Override
    public void onBackPressed() {
        if (favoriteDetail != null) { closeFavoriteDetail(); return; }
        if (myPage != null) { closeMyPage(); return; }
        int pend = deleteBucket.size() + pendingStars.size();
        if (pend == 0) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("还有 " + pend + " 项没结算")
                .setMessage("待删 " + deleteBucket.size() + " 张、待星标 " + pendingStars.size()
                        + " 张还没在结算页确认。\n直接退出这些操作不会生效，记录会留到下次打开软件时的结算页。")
                .setPositiveButton("去结算", (d, w) -> showSettlement(false))
                .setNeutralButton("继续滑动", null)
                .setNegativeButton("仍然退出", (d, w) -> finish())
                .show();
    }

    // ===================== 权限 =====================

    /** 0 无权限，1 仅部分照片，2 完整访问 */
    private int accessState() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                return 2;
            }
            if (Build.VERSION.SDK_INT >= 34
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
                return 1;
            }
            return 0;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ? 2 : 0;
    }

    private void requestAccess() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (Build.VERSION.SDK_INT >= 34) perms.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= 29) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= 29 && !hasLocationPermission()) {
            perms.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }
        requestPermissions(perms.toArray(new String[0]), REQ_PERM);
    }

    private boolean needsPhotoLocation() {
        return Build.VERSION.SDK_INT >= 29 && !hasLocationPermission();
    }

    void requestPhotoLocation(){
        if(Build.VERSION.SDK_INT>=29&&!hasLocationPermission())requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION},REQ_PHOTO_LOCATION);
        else {metaCache.clear();if(favoriteDetail!=null){PhotoItem item=favoriteDetail.photoItem();closeFavoriteDetail();openFavoritePreview(item);}else if(current!=null&&activeTab==1)renderCurrent();}
    }
    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if(requestCode==REQ_PHOTO_LOCATION){metaCache.clear();if(favoriteDetail!=null){PhotoItem item=favoriteDetail.photoItem();closeFavoriteDetail();openFavoritePreview(item);}else if(current!=null&&activeTab==1)renderCurrent();return;}
        if (requestCode != REQ_PERM) return;
        int access = accessState();
        if (access == 0) {
            showGate();
        } else {
            gateView.setVisibility(View.GONE);
            if (access == 1) {
                tvLimited.setVisibility(View.VISIBLE);
                toast("当前只授权了部分照片，可在系统设置里改为「允许访问全部照片」");
            }
            startDeck();
        }
    }

    // ===================== 界面 =====================

    private void applyPalette() {
        C_BG = lightMode ? 0xFFF2F3F5 : 0xFF0B0A09;
        C_CARD = lightMode ? 0xFFF7F8FA : 0xFF151518;
        C_TEXT = lightMode ? 0xFF20232B : 0xFFF2F0EB;
        C_DIM = lightMode ? 0xFF59616D : 0xFF9C9A93;
        C_FAINT = lightMode ? 0xFF747D88 : 0xFF6D6A62;
        C_DELETE = lightMode ? 0xFFC63E49 : 0xFFFF6B6B;
        C_KEEP = lightMode ? 0xFF187447 : 0xFF4CD38A;
        C_STAR = lightMode ? 0xFFA56A00 : 0xFFFFC14D;
        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);
        getWindow().getDecorView().setSystemUiVisibility(lightMode
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
    }

    private int surfaceColor(int darkColor) {
        if (!lightMode) return darkColor;
        switch (darkColor) {
            case 0xFF3E1F20: case 0xFF251A1A: case 0xFF3A1F1F: case 0xFF2B1E1C: return 0xDDFBE5E7;
            case 0xFF3A2C10: case 0xFF34280F: case 0xFF2A2118: case 0xFF2B2118: return 0xDDFFF0D2;
            case 0xFF1E3427: return 0xDDDEF1E5;
            default: return 0xDAFFFFFF;
        }
    }

    private void rebuildTheme() {
        boolean settlement = summaryView != null && summaryView.getVisibility() == View.VISIBLE;
        renderGeneration++;
        if (dynamicPreview != null) dynamicPreview.stop();
        boolean wasMy = myPage != null;
        if (myPage != null) { myPage.dispose(); myPage = null; }
        summaryView = null; gateView = null;
        applyPalette(); buildUi();
        if (wasMy) showMyPage();
        else if (accessState() == 0) showGate();
        else if (settlement) showSettlement(false);
        else if (current != null) renderCurrent();
        else showSummary();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        if (lightMode) {
            GradientDrawable glassBackdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFFF5F8FA, 0xFFE5EDF0, 0xFFF7F4EE});
            root.setBackground(glassBackdrop);
        } else root.setBackgroundColor(C_BG);

        // 顶部栏
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        if (lightMode) topBar.setBackground(roundBg(0xD0FFFFFF, 0));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), 0, dp(12), 0);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        tp.gravity = Gravity.TOP;tp.topMargin=dp(4);
        root.addView(topBar, tp);

        tvProgress = label("我的", 15f, C_TEXT, true);
        tabSwipe = label("滑选", 15f, C_TEXT, true);
        btnFinish = label("结算", 15f, C_TEXT, true);
        tabs = new TextView[]{tvProgress, tabSwipe, btnFinish};
        for (int i=0;i<tabs.length;i++) {
            final int target=i;TextView tab=tabs[i];tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(8),dp(6),dp(8),dp(6));
            LinearLayout.LayoutParams tabLp=new LinearLayout.LayoutParams(0,-1,1f);tabLp.setMargins(dp(3),0,dp(3),0);topBar.addView(tab,tabLp);
            tab.setOnClickListener(v->switchTab(target));
        }
        btnSettings=label("设置",12f,C_DIM,true);
        chipDelete=label("",12f,C_DELETE,true);chipDelete.setVisibility(View.GONE);
        btnUndo=new UndoButton(this);btnUndo.setContentDescription("撤销上一步");btnUndo.setTooltipText("撤销上一步");btnUndo.setVisibility(View.GONE);btnUndo.setOnClickListener(v->undo());
        updateTabs();
        topBar.setElevation(dp(30));

        // 部分授权提示条
        tvLimited = label("只显示你允许访问的照片", 11f, C_STAR, false);
        tvLimited.setGravity(Gravity.CENTER);
        tvLimited.setPadding(0, dp(4), 0, dp(4));
        tvLimited.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.TOP;
        lp2.topMargin = dp(52);
        root.addView(tvLimited, lp2);

        // 顶栏与胶片之间的暖色细线，收住上半部分
        View hairTop = new View(this);
        hairTop.setBackgroundColor(lightMode ? 0xFFC7CFD8 : 0xFF2A2118);
        FrameLayout.LayoutParams htp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        htp.topMargin = dp(59);
        root.addView(hairTop, htp);

        // 卡片区：整块做成一张 35mm 胶片，照片落在中间那一格画幅里
        deck = new FrameLayout(this);
        FrameLayout.LayoutParams dp1 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dp1.topMargin = dp(60);
        dp1.bottomMargin = dp(96);
        root.addView(deck, dp1);

        filmFrame = new FilmFrame(this);
        deck.addView(filmFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        deck.setClipChildren(true); deck.setClipToPadding(true);

        cardBack = buildCard(false);
        deck.addView(cardBack, cardLp(FILM_SIDE, FILM_SIDE, FILM_BAND, FILM_BAND));
        cardBack.setScaleX(1f);
        cardBack.setScaleY(1f);
        cardBack.setAlpha(1f);
        ivBack = (ImageView) cardBack.getTag();

        cardFront = buildCard(true);
        deck.addView(cardFront, cardLp(FILM_SIDE, FILM_SIDE, FILM_BAND, FILM_BAND));
        ivFront = (ImageView) cardFront.getTag();

        tvPlaceholder = new TextView(this);
        tvPlaceholder.setText("这个格式暂时读不出来");
        tvPlaceholder.setTextColor(C_FAINT);
        tvPlaceholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvPlaceholder.setGravity(Gravity.CENTER);
        tvPlaceholder.setVisibility(View.GONE);
        tvPlaceholder.setPadding(dp(24), 0, dp(24), 0);
        cardFront.addView(tvPlaceholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 只剩系统缩略图时的角标，不遮挡画面
        tvBadge = label("仅缩略图", 9.5f, 0xFFD9C9A8, true);
        tvBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        tvBadge.setBackground(roundBg(0xCC1A1710, 6));
        tvBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.TOP | Gravity.LEFT;
        bp.leftMargin = dp(10);
        bp.topMargin = dp(12);
        cardFront.addView(tvBadge, bp);

        TextView replay = label("LIVE · 重播", 11f, lightMode ? C_TEXT : 0xFFFFFFFF, true);
        replay.setPadding(dp(12), dp(7), dp(12), dp(7));
        replay.setBackground(roundBg(0xB0303237, 18));
        replay.setVisibility(View.GONE);
        FrameLayout.LayoutParams replayLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        replayLp.setMargins(0, dp(12), dp(12), 0);
        cardFront.addView(replay, replayLp);
        dynamicPreview = new DynamicPreview(cardFront, ivFront, replay);

        LinearLayout counters = new LinearLayout(this);
        counters.setOrientation(LinearLayout.HORIZONTAL);
        counters.setGravity(Gravity.CENTER_VERTICAL);
        countDelete = label("0", 12f, 0xFFFF6B6B, true);
        countStar = label("0", 12f, 0xFFFFC14D, true);
        countKeep = label("0", 12f, 0xFF4CD38A, true);
        for (TextView t : new TextView[]{countDelete, countStar, countKeep}) {
            t.setGravity(Gravity.CENTER); t.setMinWidth(dp(20));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -1);
            nlp.leftMargin = dp(10); counters.addView(t, nlp);
        }
        counters.setPadding(dp(4), 0, dp(10), 0);
        counters.setBackgroundColor(lightMode ? 0xFF59432B : 0xFF1B150D);
        counters.setContentDescription("本轮待删除、待星标、已保留");
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, dp(FILM_BAND), Gravity.BOTTOM | Gravity.RIGHT);
        deck.addView(counters, clp);
        final FilmFrame attachedFilm=filmFrame;
        counters.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->attachedFilm.setBottomCounterWidth(r-l));

        tvLoading = label("正在读取相册…", 14f, C_DIM, false);
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setVisibility(View.GONE);
        deck.addView(tvLoading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部操作区（滑动的点击等价路径）
        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.VERTICAL);
        actionBar.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        ap.gravity = Gravity.BOTTOM;
        root.addView(actionBar, ap);

        View hairBottom = new View(this);
        hairBottom.setBackgroundColor(lightMode ? 0xFFC7CFD8 : 0xFF2A2118);
        FrameLayout.LayoutParams hbp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        hbp.gravity = Gravity.BOTTOM;
        hbp.bottomMargin = dp(96);
        root.addView(hairBottom, hbp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        FrameLayout controls=new FrameLayout(this);actionBar.addView(controls,new LinearLayout.LayoutParams(-1,dp(80)));
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);rp.topMargin=dp(2);
        controls.addView(row,rp);

        Object[] leftBtn = addActionButton(row, "左滑删除", GlyphView.CROSS, C_DELETE,
                v -> commit(swapSwipe ? KEEP : DELETE));
        addActionButton(row, "上滑星标", GlyphView.STAR, C_STAR, v -> commit(STAR));
        Object[] rightBtn = addActionButton(row, "右滑保留", GlyphView.CHECK, C_KEEP,
                v -> commit(swapSwipe ? DELETE : KEEP));
        tipLeft = (TextView) leftBtn[0];
        glyphLeft = (GlyphView) leftBtn[1];
        tipRight = (TextView) rightBtn[0];
        glyphRight = (GlyphView) rightBtn[1];
        refreshSwipeLabels();
        FrameLayout.LayoutParams undoLp=new FrameLayout.LayoutParams(dp(40),dp(48));controls.addView(btnUndo,undoLp);
        controls.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams u=(FrameLayout.LayoutParams)btnUndo.getLayoutParams();int x=Math.max(0,row.getLeft()-dp(42)),y=row.getTop()+row.getHeight()-dp(27)-dp(24);if(u.leftMargin!=x||u.topMargin!=y){u.leftMargin=x;u.topMargin=y;btnUndo.setLayoutParams(u);}});

        // 撤销条
        undoBar = new LinearLayout(this);
        undoBar.setOrientation(LinearLayout.HORIZONTAL);
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setPadding(dp(16), dp(9), dp(10), dp(9));
        GradientDrawable ub = roundBg(0xE61C1710, 18);
        ub.setStroke(dp(1), 0x33C8B896);
        undoBar.setBackground(ub);
        undoBar.setVisibility(View.GONE);
        undoBar.setElevation(dp(24));
        undoBar.setClickable(true);

        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        up.topMargin = dp(62);
        root.addView(undoBar, up);

        undoLabel = label("", 13f, C_TEXT, false);
        undoBar.addView(undoLabel);

        TextView undoBtn = label("撤销", 13f, C_STAR, true);
        undoBtn.setPadding(dp(14), dp(4), dp(4), dp(4));
        undoBar.addView(undoBtn);
        undoBtn.setOnClickListener(v -> undo());

        setContentView(root);
        attachGestures();
    }

    private FrameLayout buildCard(boolean withInfo) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(0);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(0);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(-1, -1);
        imageLp.bottomMargin = dp(100); // front/back share identical photo geometry
        card.addView(iv, imageLp);
        card.setTag(iv);

        // 照片比例与卡片不一致时，用同一张图的暗色铺底填满留白
        ImageView fill = new ImageView(this);
        fill.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        fill.setColorFilter(new ColorMatrixColorFilter(gray));
        fill.setAlpha(lightMode ? 0.32f : 0.26f);
        // Same full-bleed grayscale backdrop in both modes; do not inset or split it into a block.
        card.addView(fill, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (withInfo) {
            ivFrontBg = fill;
        } else {
            ivBackBg = fill;
        }

        if (withInfo) {
            ivInfoGlass = null;
            View scrim = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xCD000000, 0x60000000, 0x00000000});
            scrim.setBackground(g);
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(168));
            sp.gravity = Gravity.BOTTOM;
            card.addView(scrim, sp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(20), dp(10), dp(20), dp(18));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.gravity = Gravity.BOTTOM;
            card.addView(info, ip);

            tvTime = label("", 18f, 0xFFFFFFFF, true);
            tvPlace = label("", 12.5f, 0xFFE0E0E0, false);
            // 地点行只在缺地名权限时才去申请权限，其余情况与信息区一致进详情
            tvPlace.setOnClickListener(v->{if(needsPhotoLocation())requestPhotoLocation();else showDetail();});
            tvDevice = label("", 11.5f, 0xFFC5C5C5, false);
            tvFile = label("", 10.5f, 0xFFB4B4B4, false);
            infoBlock = info;
            info.addView(tvTime);
            LinearLayout.LayoutParams m1 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            m1.topMargin = dp(5);
            info.addView(tvPlace, m1);
            info.addView(tvDevice, m1);
            info.addView(tvFile, m1);

            stampKeep = stamp("保留", C_KEEP);
            FrameLayout.LayoutParams sk = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sk.gravity = Gravity.TOP | Gravity.LEFT;
            sk.leftMargin = dp(20);
            sk.topMargin = dp(22);
            stampKeep.setRotation(-12f);
            card.addView(stampKeep, sk);

            stampDelete = stamp("删除", C_DELETE);
            FrameLayout.LayoutParams sd = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sd.gravity = Gravity.TOP | Gravity.RIGHT;
            sd.rightMargin = dp(20);
            sd.topMargin = dp(22);
            stampDelete.setRotation(12f);
            card.addView(stampDelete, sd);

            stampStar = stamp("星标", C_STAR);
            FrameLayout.LayoutParams ss = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ss.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            ss.bottomMargin = dp(150);
            card.addView(stampStar, ss);
        }
        return card;
    }

    private TextView stamp(String text, int color) {
        TextView tv = label(text, 26f, color, true);
        tv.setAlpha(0f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1A000000);
        g.setStroke(dp(2), color);
        g.setCornerRadius(dp(10));
        tv.setBackground(g);
        tv.setPadding(dp(14), dp(5), dp(14), dp(5));
        return tv;
    }

    private FrameLayout.LayoutParams cardLp(int l, int r, int t, int b) {        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private Object[] addActionButton(LinearLayout row, String title, int glyphType, int color, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(13);
        cp.rightMargin = dp(13);
        row.addView(col, cp);

        TextView tip = label(title, 11f, C_FAINT, false);
        tip.setGravity(Gravity.CENTER);
        col.addView(tip);

        GlyphView b = new GlyphView(this, glyphType, color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(54));
        p.topMargin = dp(3);
        col.addView(b, p);
        b.setOnClickListener(l);
        return new Object[]{tip, b};
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setIncludeFontPadding(false);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable roundBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(surfaceColor(color));
        if (lightMode) g.setStroke(dp(1), 0xAFFFFFFF);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // ===================== 授权页 / 空状态 =====================

    private void showGate() {
        deck.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
        topBar.setVisibility(View.GONE);
        undoBar.setVisibility(View.GONE);

        if (gateView != null) {
            gateView.setVisibility(View.VISIBLE);
            return;
        }

        gateView = new LinearLayout(this);
        gateView.setOrientation(LinearLayout.VERTICAL);
        gateView.setGravity(Gravity.CENTER);
        gateView.setPadding(dp(34), dp(20), dp(34), dp(20));
        root.addView(gateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView emoji = label("相册滑选", 26f, C_TEXT, true);
        emoji.setGravity(Gravity.CENTER);
        gateView.addView(emoji);

        TextView desc = label("左滑删除 · 上滑星标 · 右滑保留\n需要读取相册的权限才能开始", 13f, C_DIM, false);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(14);
        gateView.addView(desc, p);

        TextView btn = label("授权访问照片", 15f, C_TEXT, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(roundBg(0xFF2A2A30, 26));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(48));
        bp.topMargin = dp(28);
        gateView.addView(btn, bp);
        btn.setOnClickListener(v -> requestAccess());

        TextView settings = label("已拒绝过？去系统设置里开启", 12f, C_FAINT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.topMargin = dp(8);
        gateView.addView(settings, sp2);
        settings.setOnClickListener(v -> {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", getPackageName(), null));
            try {
                startActivity(i);
            } catch (Exception e) {
                toast("请手动到系统设置中开启相册权限");
            }
        });
    }

    // ===================== 数据加载 =====================

    private void startDeck() {
        topBar.setVisibility(View.VISIBLE);
        deck.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.VISIBLE);
        cardFront.setVisibility(View.INVISIBLE);
        cardBack.setVisibility(View.INVISIBLE);

        io.execute(() -> {
            final List<PhotoItem> loaded = queryPhotos();
            main.post(() -> {
                tvLoading.setVisibility(View.GONE);
                cardFront.setVisibility(View.VISIBLE);
                all.clear();
                all.addAll(loaded);
                loadSeen();
                loadPending();
                if (!lastDeleteUris.isEmpty()) onDeleted(lastDeleteUris.size());
                if (!lastFavUris.isEmpty()) finishFavorites();
                if(all.isEmpty()){showEmpty();return;}
                refillQueue();
                if(activeTab==0){dismissMyPage();showMyPage();}
                else if(activeTab==2)showSettlement(false);
                else if(current==null)showEmpty();else renderCurrent();
                int rest = deleteBucket.size() + pendingStars.size();
                if (rest > 0 && !navigationTouched) {
                    showSettlement(true);
                    toast("上一轮还有 " + rest + " 项没结算，记录已保留在本页");
                }
            });
        });
    }

    private List<PhotoItem> queryPhotos() {
        List<PhotoItem> out = new ArrayList<>();
        Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.ORIENTATION
        };
        if (Build.VERSION.SDK_INT >= 30) {
            String[] extended = java.util.Arrays.copyOf(proj, proj.length + 1);
            extended[proj.length] = MediaStore.MediaColumns.IS_TRASHED;
            proj = extended;
        }
        Cursor c = null;
        try {
            String selection = Build.VERSION.SDK_INT >= 30 ? "is_trashed=0 AND is_pending=0" : null;
            if (Build.VERSION.SDK_INT >= 30) {
                Bundle args = new Bundle();
                args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE);
                args.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE);
                args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
                args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Images.Media.DATE_MODIFIED + " DESC");
                c = cr.query(base, proj, args, null);
            } else c = cr.query(base, proj, selection, null, MediaStore.Images.Media.DATE_MODIFIED + " DESC");
            if (c == null) return out;
            int iId = c.getColumnIndex(MediaStore.Images.Media._ID);
            int iName = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            int iSize = c.getColumnIndex(MediaStore.Images.Media.SIZE);
            int iTaken = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
            int iMod = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);
            int iW = c.getColumnIndex(MediaStore.Images.Media.WIDTH);
            int iH = c.getColumnIndex(MediaStore.Images.Media.HEIGHT);
            int iOrientation = c.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
            Set<String> sig = new HashSet<>();
            while (c.moveToNext()) {
                long id = c.getLong(iId);
                long size = iSize >= 0 ? c.getLong(iSize) : 0L;
                long taken = iTaken >= 0 ? c.getLong(iTaken) : 0L;
                if (taken <= 0 && iMod >= 0) taken = c.getLong(iMod) * 1000L;
                String name = iName >= 0 ? c.getString(iName) : ("IMG_" + id);
                if (name == null || name.startsWith(".trashed-") || name.startsWith(".pending-")) continue;
                String signature = name + "|" + size + "|" + (iTaken >= 0 ? c.getLong(iTaken) : 0L);
                if (!sig.add(signature)) continue;
                int w = iW >= 0 ? c.getInt(iW) : 0;
                int h = iH >= 0 ? c.getInt(iH) : 0;
                PhotoItem item = new PhotoItem(id, ContentUris.withAppendedId(base, id), name, size, taken, w, h);
                item.signature = signature;item.mediaRotation=iOrientation>=0?c.getInt(iOrientation):0;
                out.add(item);
            }
        } catch (Exception e) {
            // 权限不足或相册不可读
        } finally {
            if (c != null) try {
                c.close();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void showEmpty() {
        cardFront.setVisibility(View.INVISIBLE);
        cardBack.setVisibility(View.INVISIBLE);
        tvLoading.setVisibility(View.VISIBLE);
        tvLoading.setText(all.isEmpty()?"相册里没有找到照片":"本轮已浏览完 · 可到结算页明确开启新一轮");
        actionBar.setVisibility(View.GONE);
    }

    // ===================== 渲染 =====================

    private void renderCurrent() {
        PhotoItem it = current;
        if (it == null) { showEmpty(); return; }
        if(isReviewed(it)){advance();return;}
        final long generation = ++renderGeneration;
        previewReady = false;
        if (dynamicPreview != null) dynamicPreview.stop();
        Bitmap prepared=backVerifiedId==it.id&&!isReviewed(it)?cache.get(key(it.id)):null;
        if (prepared == null) ivFront.setImageBitmap(null);
        tvLoading.setText("正在核对照片…");
        tvLoading.setVisibility(prepared==null?View.VISIBLE:View.GONE);
        resetCardVisual();
        resetZoom();
        cardFront.setVisibility(View.VISIBLE);

        ivFront.setImageBitmap(prepared);
        ivFrontBg.setImageBitmap(prepared);
        fillInfo(it, metaCache.get(it.id));
        applyPreviewState(it);
        updateTopBar();
        renderBack();

        final PhotoItem target = it;
        io.execute(() -> {
            try {
                if (target.digest == null) {
                    try (InputStream stream = cr.openInputStream(target.uri)) { target.digest = ReviewLedger.sha256(stream); }
                }
            } catch (Exception e) { logOp("内容指纹不可读，保留URI去重：" + target.name); }
            final boolean duplicate = ledger.containsDigest(target.digest);
            int trashState = Build.VERSION.SDK_INT >= 30 ? readFlag(target.uri, MediaStore.MediaColumns.IS_TRASHED) : 0;
            final boolean unavailable = trashState == 1 || trashState == -1;
            if (duplicate || unavailable) {
                main.post(() -> {
                    if (generation != renderGeneration || current != target) return;
                    seen.add(target.id);
                    if (duplicate) ledger.record(target.identity(), target.signature, target.digest, false);
                    saveSeen();
                    if (unavailable) { all.remove(target); pending.remove(target); }
                    advance();
                });
                return;
            }
            meta(target);decode(target);
            final DynamicPreview.Content dynamic = DynamicPreview.load(this, target.uri, target.name);
            main.post(() -> {
                if (generation != renderGeneration || current != target || isFinishing() || activeTab!=1) {
                    if (dynamic.motion != null) dynamic.motion.delete(); return;
                }
                if(isReviewed(target)){if(dynamic.motion!=null)dynamic.motion.delete();advance();return;}
                tvLoading.setVisibility(View.GONE);
                previewReady = true;
                Bitmap b2 = cache.get(key(target.id));
                if (b2 != null) { ivFront.setImageBitmap(b2); ivFrontBg.setImageBitmap(b2); if (ivInfoGlass != null) ivInfoGlass.setImageBitmap(b2); }
                fillInfo(target, metaCache.get(target.id));
                applyPreviewState(target);
                dynamicPreview.show(dynamic);renderBack();
            });
        });

        PhotoItem nx = pending.peek();
        if (nx != null) io.execute(() -> decode(nx));
    }

    private void renderBack() {
        if(activeTab!=1)return;
        while(pending.peek()!=null&&(isReviewed(pending.peek())||current!=null&&current.digest!=null&&current.digest.equals(pending.peek().digest)))pending.poll();
        final PhotoItem nx=pending.peek();final long generation=renderGeneration;
        // Front completion re-enters here: keep the same verified/in-flight candidate.
        if(nx==backRequest&&generation==backRequestGeneration)return;
        backRequest=nx;backRequestGeneration=generation;backVerifiedId=-1;
        cardBack.animate().cancel();cardBack.setAlpha(1f);
        cardBack.setVisibility(View.INVISIBLE);
        ivBack.setImageBitmap(null);ivBackBg.setImageBitmap(null);
        if(nx==null)return;
        io.execute(()->{
            try { if(nx.digest==null)try(InputStream in=cr.openInputStream(nx.uri)){nx.digest=ReviewLedger.sha256(in);} }
            catch(Exception ignored){}
            boolean duplicate=ledger.containsDigest(nx.digest);
            int state=Build.VERSION.SDK_INT>=30?readFlag(nx.uri,MediaStore.MediaColumns.IS_TRASHED):0;
            if(duplicate||state==1||state==-1||nx.digest!=null&&current!=null&&nx.digest.equals(current.digest)){main.post(()->{if(generation==renderGeneration&&activeTab==1&&pending.peek()==nx){pending.poll();renderBack();}});return;}
            decode(nx);Bitmap bitmap=cache.get(key(nx.id));
            main.post(()->{if(generation!=renderGeneration||activeTab!=1||pending.peek()!=nx||isReviewed(nx))return;
                if(current!=null&&nx.digest!=null&&nx.digest.equals(current.digest)){pending.poll();renderBack();return;}
                backVerifiedId=nx.id;ivBack.setImageBitmap(bitmap);ivBackBg.setImageBitmap(bitmap);
                // startDeck hides this view; loading alone must restore it, no Tab round-trip.
                cardBack.setVisibility(bitmap!=null?View.VISIBLE:View.INVISIBLE);
                if(bitmap!=null){
                    // Soft reveal: the candidate grows out of the dark instead of popping in.
                    cardBack.animate().cancel();cardBack.setAlpha(BACK_REVEAL_FROM);
                    cardBack.animate().alpha(1f).setDuration(BACK_REVEAL_MS).setInterpolator(new DecelerateInterpolator()).start();
                }});
        });
    }

    private void updateTopBar() {
        tvProgress.setText("我的");
        chipDelete.setVisibility(View.GONE);
        btnUndo.setAlpha(history.isEmpty() ? 0.35f : 1f);
        btnUndo.setVisibility(activeTab==1&&!history.isEmpty()?View.VISIBLE:View.GONE);
        if (btnFinish != null) btnFinish.setText("结算");
        updateTabs();
        if (filmFrame != null) filmFrame.setFrame(seen.size(), all.size());
        if (countDelete != null) {
            countDelete.setText(String.valueOf(deleteBucket.size()));
            countStar.setText(String.valueOf(pendingStars.size()));
            countKeep.setText(String.valueOf(keptCount));
        }
    }

    private void fillInfo(PhotoItem it, Meta m) {
        String time = "时间读取中…";
        String place = "";
        String device = "";
        String file = "";

        if (m != null) {
            time = m.dateTime != null ? prettyTime(m.dateTime) : formatMs(it.dateTaken);
            if (time == null) time = "无拍摄时间";
            place = m.hasGps ? PhotoPlace.coords(m.lat,m.lon)
                    : (!hasLocationPermission()?"未授权照片位置，点此授权":(m.exifRead?"原照片无GPS，无法提供经纬度":"照片位置读取失败，点此重试"));
            device = deviceName(m);
            int w = m.width > 0 ? m.width : it.width;
            int h = m.height > 0 ? m.height : it.height;
            if (w > 0 && h > 0) device = device + " · " + w + "×" + h;
            file = it.name + " · " + bytes(it.size);
        }

        tvTime.setText(time);
        setKv(tvPlace, "地点  ", m == null ? "读取中…" : place);
        setKv(tvDevice, "设备  ", m == null ? "读取中…" : device);
        setKv(tvFile, "文件  ", m == null ? "" : file);
        tvPlace.setVisibility(View.VISIBLE);
        tvDevice.setVisibility(View.VISIBLE);
    }

    /** 厂商与机型合并，避免 "vivo vivo X100" 这种重复 */
    private String deviceName(Meta m) {
        if (m == null) return "未知设备";
        String mk = m.make == null ? "" : m.make.trim();
        String md = m.model == null ? "" : m.model.trim();
        String mkLow = mk.toLowerCase(Locale.US);
        String mdLow = md.toLowerCase(Locale.US);
        if (md.isEmpty()) return mk.isEmpty() ? "未知设备" : mk;
        if (mk.isEmpty() || mdLow.startsWith(mkLow) || mdLow.contains(mkLow) || mkLow.contains(mdLow)) return md;
        return mk + " " + md;
    }

    private void setKv(TextView tv, String k, String v) {        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(k);
        int kEnd = sb.length();
        sb.setSpan(new ForegroundColorSpan(0xFFABABAB), 0, kEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.78f), 0, kEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(v);
        tv.setText(sb);
    }

    private String prettyTime(String exif) {
        // "2024:05:12 14:23:45"
        try {
            String s = exif.trim();
            if (s.length() >= 16) {
                int y = Integer.parseInt(s.substring(0, 4));
                int mo = Integer.parseInt(s.substring(5, 7));
                int d = Integer.parseInt(s.substring(8, 10));
                String hm = s.substring(11, 16);
                if (y <= 1900) return null;
                return y + "年" + mo + "月" + d + "日  " + hm;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String formatMs(long ms) {
        if (ms <= 0) return null;
        return new SimpleDateFormat("yyyy年M月d日  HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private String bytes(long b) {
        if (b <= 0) return "未知大小";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    // ===================== 图片解码 + EXIF =====================

    private String key(long id) {
        return "p" + id;
    }

    Bitmap decodeAlbumPhoto(PhotoItem it){
        Meta m=meta(it);Bitmap b=decodeByImageDecoder(it);
        if(b==null)b=decodeByBitmapFactory(it,m);
        if(b==null)b=decodeBySystemThumbnail(it);
        if(b!=null&&Math.max(b.getWidth(),b.getHeight())>1100){float f=1100f/Math.max(b.getWidth(),b.getHeight());Bitmap s=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*f)),Math.max(1,Math.round(b.getHeight()*f)),true);if(s!=b)b.recycle();b=s;}
        return b;
    }
    private void decode(PhotoItem it) {
        String k = key(it.id);
        if (cache.get(k) != null) return;
        synchronized (decoding) {
            while(decoding.contains(k)){try{decoding.wait(200);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}
            if(cache.get(k)!=null)return;
            decoding.add(k);
        }
        try {
            Meta m = meta(it);
            // 三级解码：系统解码器（能吃 HEIF/AVIF/动图首帧并自动转正）
            //        → 位图工厂（8.x 兜底，采样后体积可控）
            //        → 系统缩略图（格式再怪，只要媒体库认得就能出图）
            Bitmap bmp = decodeByImageDecoder(it);
            boolean thumb = false;
            if (bmp == null) bmp = decodeByBitmapFactory(it, m);
            if (bmp == null) {
                bmp = decodeBySystemThumbnail(it);
                thumb = bmp != null;
            }
            synchronized (thumbOnly) {
                thumbOnly.remove(it.id);
                unreadable.remove(it.id);
                if (bmp == null) unreadable.add(it.id);
                else if (thumb) thumbOnly.add(it.id);
            }
            if (bmp != null) cache.put(k, bmp);
        } catch (Throwable ignored) {
        } finally {
            synchronized (decoding) {
                decoding.remove(k);decoding.notifyAll();
            }
        }
    }

    private Meta meta(PhotoItem it) {
        Meta cached = metaCache.get(it.id);
        if (cached != null && cached.locationAllowed==hasLocationPermission() && cached.exifRead) return cached;
        Meta m = new Meta();m.locationAllowed=hasLocationPermission();m.orientation=PhotoOrientation.read(cr,it.uri,it.mediaRotation);
        m.width = it.width;
        m.height = it.height;
        InputStream is = null;
        try {
            Uri target = it.uri;
            if (Build.VERSION.SDK_INT >= 29 && hasLocationPermission()) {
                try {
                    target = MediaStore.setRequireOriginal(it.uri);
                } catch (Throwable ignored) {
                    target = it.uri;
                }
            }
            is = cr.openInputStream(target);
            if (is != null) {
                ExifInterface ex = new ExifInterface(new BufferedInputStream(is, 8192));m.exifRead=true;
                m.rotation = rotationOf(ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
                String dt = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                if (dt == null || dt.trim().isEmpty()) dt = ex.getAttribute(ExifInterface.TAG_DATETIME);
                m.dateTime = dt;
                m.make = ex.getAttribute(ExifInterface.TAG_MAKE);
                m.model = ex.getAttribute(ExifInterface.TAG_MODEL);

                // GPS 优先手工解析（部分机型 getLatLong 对非相机写入的 EXIF 失效）
                String latStr = ex.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
                String latRef = ex.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
                String lonStr = ex.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
                String lonRef = ex.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
                if (latStr != null && lonStr != null && ("N".equalsIgnoreCase(latRef)||"S".equalsIgnoreCase(latRef)) && ("E".equalsIgnoreCase(lonRef)||"W".equalsIgnoreCase(lonRef))) {
                    double la = parseDms(latStr);
                    double lo = parseDms(lonStr);
                    if (latRef != null && latRef.trim().toUpperCase(Locale.US).startsWith("S")) la = -la;
                    if (lonRef != null && lonRef.trim().toUpperCase(Locale.US).startsWith("W")) lo = -lo;
                    if (Double.isFinite(la)&&Double.isFinite(lo)&&Math.abs(la)<=90&&Math.abs(lo)<=180) {
                        m.hasGps = true;
                        m.lat = la;
                        m.lon = lo;
                    }
                }
                if (!m.hasGps && latStr==null && lonStr==null) {
                    float[] ll = new float[2];
                    try {
                        if (ex.getLatLong(ll) && Math.abs(ll[0])<=90 && Math.abs(ll[1])<=180) {
                            m.hasGps = true;
                            m.lat = ll[0];
                            m.lon = ll[1];
                        }
                    } catch (Throwable ignored) {
                    }
                }
                int ew = ex.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                int eh = ex.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
                if (ew > 0 && eh > 0) {
                    m.width = ew;
                    m.height = eh;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (is != null) try {
                is.close();
            } catch (Exception ignored) {
            }
        }
        metaCache.put(it.id, m);
        return m;
    }

    /** 解析 "31/1,13/1,4944/100" 或 "31.2304" 形式的度分秒 */
    private double parseDms(String s) {
        if(s==null)return Double.NaN;
        try {
            String[] parts=s.trim().split(",",-1);
            if(parts.length!=1&&parts.length!=3)return Double.NaN;
            double d=rat(parts[0]);
            if(parts.length==1)return d;
            double m=rat(parts[1]),sec=rat(parts[2]);
            if(d<0||m<0||m>=60||sec<0||sec>=60)return Double.NaN;
            return d+m/60d+sec/3600d;
        }catch(Exception e){return Double.NaN;}
    }
    private double rat(String s) {
        String[] p=s.trim().split("/",-1);
        if(p.length>2)return Double.NaN;
        double n=Double.parseDouble(p[0].trim()),den=p.length==2?Double.parseDouble(p[1].trim()):1d;
        return den==0||!Double.isFinite(n)||!Double.isFinite(den)?Double.NaN:n/den;
    }

    private int rotationOf(int orientation) {        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    /** 解码目标：不超过卡片实际显示的区域，12MP/48MP 原图一律先缩再读 */
    private int[] targetSize(int srcW, int srcH) {
        int maxW = screenW;
        int maxH = (int) (screenH * 0.9);
        if (srcW <= 0 || srcH <= 0) return new int[]{maxW, maxH};
        float scale = Math.min(1f, Math.min((float) maxW / srcW, (float) maxH / srcH));
        int tw = Math.max(1, Math.round(srcW * scale));
        int th = Math.max(1, Math.round(srcH * scale));
        return new int[]{tw, th};
    }

    /** 一级：系统解码器。支持 HEIF/AVIF/WebP/GIF，自动按 EXIF 转正，按目标尺寸自动降采样 */
    private Bitmap decodeByImageDecoder(PhotoItem it) {
        if (Build.VERSION.SDK_INT < 28) return null;
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(cr, it.uri);
            Bitmap decoded=ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                Size sz = info.getSize();
                int[] t = targetSize(sz.getWidth(), sz.getHeight());
                decoder.setTargetSize(t[0], t[1]);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                decoder.setOnPartialImageListener(e -> true);
                try {
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                } catch (Throwable ignored) {
                }
            });
            return correctSystemOrientation(it,decoded);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap correctSystemOrientation(PhotoItem it,Bitmap b){
        PhotoOrientation.Info o=meta(it).orientation;
        if(o.hasExif||it.mediaRotation==0)return b;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=cr.openInputStream(it.uri)){BitmapFactory.decodeStream(in,null,bounds);}catch(Exception ignored){}
        return PhotoOrientation.system(b,o,bounds.outWidth,bounds.outHeight);
    }

    /** 二级：位图工厂。只给 8.0/8.1 与系统解码器缺席的机型用，采样按长边上限算 */
    private Bitmap decodeByBitmapFactory(PhotoItem it, Meta m) {
        int w = 0, h = 0;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream is1 = null;
        try {
            is1 = cr.openInputStream(it.uri);
            if (is1 == null) return null;
            BitmapFactory.decodeStream(is1, null, bounds);
            w = bounds.outWidth;
            h = bounds.outHeight;
        } catch (Throwable ignored) {
        } finally {
            if (is1 != null) try {
                is1.close();
            } catch (Exception ignored) {
            }
        }
        if (w <= 0 || h <= 0) {
            w = it.width;
            h = it.height;
        }
        if (w <= 0 || h <= 0) return null;

        int[] t = targetSize(w, h);
        int sample = 1;
        while (w / sample > t[0] * 1.2 || h / sample > t[1] * 1.2) {
            sample *= 2;
            if (sample >= 64) break;
        }

        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        InputStream is2 = null;
        Bitmap bmp = null;
        try {
            is2 = cr.openInputStream(it.uri);
            if (is2 == null) return null;
            bmp = BitmapFactory.decodeStream(is2, null, opt);
        } catch (Throwable ignored) {
        } finally {
            if (is2 != null) try {
                is2.close();
            } catch (Exception ignored) {
            }
        }
        if (bmp == null) return null;

        return PhotoOrientation.raw(bmp,m.orientation.orientation);
    }

    /** 三级：系统缩略图。TIFF、部分 HEIC/AVIF 变体等原图解不开时，靠它保住画面 */
    private Bitmap decodeBySystemThumbnail(PhotoItem it) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return correctSystemOrientation(it,cr.loadThumbnail(it.uri, new Size(screenW, (int) (screenH * 0.6)), null));
            } catch (Throwable ignored) {
                return null;
            }
        }
        try {
            return correctSystemOrientation(it,MediaStore.Images.Thumbnails.getThumbnail(
                    cr, it.id, MediaStore.Images.Thumbnails.MINI_KIND, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 卡片预览状态：占位文案与"仅缩略图"角标分开表达，不把内存问题误报成格式问题 */
    private void applyPreviewState(PhotoItem it) {
        tvPlaceholder.setVisibility(View.GONE);
        tvBadge.setVisibility(View.GONE);
        boolean thumb, bad;
        synchronized (thumbOnly) {
            thumb = thumbOnly.contains(it.id);
            bad = unreadable.contains(it.id);
        }
        if (bad) {
            String ext = extensionOf(it);
            String line2 = ext.isEmpty() ? "可以照常标记、删除"
                    : ext.toUpperCase(Locale.US) + " 文件 · 可以照常标记、删除";
            tvPlaceholder.setText("这个格式暂时读不出来\n" + line2);
            tvPlaceholder.setVisibility(View.VISIBLE);
        }
        tvBadge.setVisibility(thumb ? View.VISIBLE : View.GONE);
    }

    private String extensionOf(PhotoItem it) {
        String name = it.name == null ? "" : it.name;
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    // ===================== 手势 =====================

    private void attachGestures() {
        zoomDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom = clampF(zoom * d.getScaleFactor(), 1f, 4f);
                applyZoom();
                return true;
            }
        });

        cardFront.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, lastX, lastY;
            long downT;
            boolean pinched;
            VelocityTracker tracker;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                zoomDetector.onTouchEvent(ev);
                int pointers = ev.getPointerCount();
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getRawX();
                        downY = ev.getRawY();
                        lastX = ev.getRawX();
                        lastY = ev.getRawY();
                        downT = System.currentTimeMillis();
                        pinched = false;
                        cardFront.animate().cancel();
                        if (tracker == null) tracker = VelocityTracker.obtain();
                        tracker.clear();
                        tracker.addMovement(ev);
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        pinched = true;
                        resetCardVisual();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (tracker != null) tracker.addMovement(ev);
                        if (pointers >= 2 || pinched) {
                            pinched = true;
                            resetCardVisual();
                            lastX = ev.getRawX();
                            lastY = ev.getRawY();
                            return true;
                        }
                        if (zoom > 1.02f) {
                            // 放大状态下单指改为拖动画面，不再触发卡片滑走
                            zoomX += ev.getRawX() - lastX;
                            zoomY += ev.getRawY() - lastY;
                            lastX = ev.getRawX();
                            lastY = ev.getRawY();
                            applyZoom();
                            return true;
                        }
                        float dx = ev.getRawX() - downX;
                        float dy = ev.getRawY() - downY;
                        applyDrag(dx, dy);
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        float vx = 0f, vy = 0f;
                        if (tracker != null) {
                            tracker.computeCurrentVelocity(1000);
                            vx = tracker.getXVelocity();
                            vy = tracker.getYVelocity();
                            tracker.recycle();
                            tracker = null;
                        }
                        if (pinched) {
                            pinched = false;
                            resetCardVisual();
                            return true;
                        }
                        float dx = ev.getRawX() - downX;
                        float dy = ev.getRawY() - downY;
                        float dist = (float) Math.hypot(dx, dy);
                        boolean tap = dist < dp(9) && System.currentTimeMillis() - downT < 350;
                        if (zoom > 1.02f) {
                            // 放大时点击只复位，避免误触滑走
                            if (tap) {
                                resetZoom();
                                toast("已恢复原始大小");
                            }
                            return true;
                        }
                        if (tap) {
                            resetCardVisual();
                            // 只有照片下方的时间/地点/设备文字行进详情；点画面本身不弹信息
                            if (isDetailTapTarget(ev.getY())) showDetail();
                            return true;
                        }
                        decide(dx, dy, vx, vy);
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        pinched = false;
                        if (tracker != null) {
                            tracker.recycle();
                            tracker = null;
                        }
                        resetCardVisual();
                        return true;
                }
                return true;
            }
        });
    }

    /** 详情入口只认照片下方的信息文字行，点画面本身不弹信息 */
    private boolean isDetailTapTarget(float cardY) {
        if (infoBlock == null || infoBlock.getVisibility() != View.VISIBLE) return false;
        return cardY >= infoBlock.getTop() - dp(8);
    }

    /** 双指缩放后，把画面平移限制在放大出来的范围里 */
    private void applyZoom() {
        int w = Math.max(1, ivFront.getWidth());
        int h = Math.max(1, ivFront.getHeight());
        float maxX = (zoom - 1f) * w / 2f;
        float maxY = (zoom - 1f) * h / 2f;
        zoomX = Math.max(-maxX, Math.min(maxX, zoomX));
        zoomY = Math.max(-maxY, Math.min(maxY, zoomY));
        ivFront.setScaleX(zoom);
        ivFront.setScaleY(zoom);
        ivFront.setTranslationX(zoomX);
        ivFront.setTranslationY(zoomY);
        ivFrontBg.setScaleX(zoom);
        ivFrontBg.setScaleY(zoom);
        ivFrontBg.setTranslationX(zoomX);
        ivFrontBg.setTranslationY(zoomY);
    }

    /** 左右滑动对应哪个动作，随设置里的互换开关变化 */
    private int dxAction(float dx) {
        if (dx > 0) return swapSwipe ? DELETE : KEEP;
        return swapSwipe ? KEEP : DELETE;
    }

    private void refreshSwipeLabels() {
        if (tipLeft == null) return;
        tipLeft.setText(swapSwipe ? "左滑保留" : "左滑删除");
        glyphLeft.setGlyph(swapSwipe ? GlyphView.CHECK : GlyphView.CROSS, swapSwipe ? C_KEEP : C_DELETE);
        tipRight.setText(swapSwipe ? "右滑删除" : "右滑保留");
        glyphRight.setGlyph(swapSwipe ? GlyphView.CROSS : GlyphView.CHECK, swapSwipe ? C_DELETE : C_KEEP);
    }

    private void updateTabs(){
        if(tabs==null)return;
        for(int i=0;i<tabs.length;i++){
            boolean selected=i==activeTab;tabs[i].setSelected(selected);
            tabs[i].setTextColor(selected?C_TEXT:C_FAINT);
            tabs[i].setBackground(selected?roundBg(lightMode?0xDAFFFFFF:0xFF302719,18):null);
            tabs[i].setContentDescription(new String[]{"我的，收藏相册","滑选，浏览照片","结算，处理待办"}[i]+(selected?"，已选中":""));
        }
    }
    private void dismissMyPage(){
        if(favoriteDetail!=null)closeFavoriteDetail();
        if(myPage!=null){myPage.dispose();root.removeView(myPage);myPage=null;}
    }
    private void switchTab(int target){
        navigationTouched=true;
        if(systemBusy){toast("请先完成系统确认");return;}
        if(accessState()==0){showGate();return;}
        if(advancing){cardFront.animate().cancel();resetCardVisual();advancing=false;current=null;}
        if(target==activeTab){if(target==0&&favoriteDetail!=null)closeFavoriteDetail();else if(target==1&&current==null)showBrowsing();return;}
        if(target==0){showMyPage();return;}
        dismissMyPage();topBar.setVisibility(View.VISIBLE);deck.setVisibility(View.VISIBLE);
        if(target==2)showSettlement(false);else showBrowsing();
    }
    private void showBrowsing(){
        navigationTouched=true;
        if(systemBusy){toast("请先完成系统确认");return;}
        dismissMyPage();topBar.setVisibility(View.VISIBLE);deck.setVisibility(View.VISIBLE);
        activeTab=1;updateTabs();resumeDeck();
    }

    // ===================== 我的（本地资料与已确认收藏） =====================
    private void showMyPage() {
        if (systemBusy || advancing) { toast("请先完成当前操作"); return; }
        if (myPage != null) return;
        if (dynamicPreview != null) dynamicPreview.stop();
        renderGeneration++; previewReady = false;
        hideUndoBar();
        topBar.setVisibility(View.VISIBLE); deck.setVisibility(View.GONE); actionBar.setVisibility(View.GONE);
        activeTab=0;updateTabs();
        List<PhotoItem> favorites = new ArrayList<>();
        for (PhotoItem item : all) if (starred.contains(item.id)) favorites.add(item);
        myPage = new MyAlbumView(this, prefs, lightMode, favorites,
                this::closeMyPage, this::showSettings, this::openFavoritePreview);
        FrameLayout.LayoutParams albumLp=new FrameLayout.LayoutParams(-1,-1);albumLp.topMargin=dp(60);root.addView(myPage,albumLp);
        topBar.bringToFront();
    }

    private void closeMyPage() { showBrowsing(); }

    private void openFavoritePreview(PhotoItem item) {
        if (favoriteDetail != null) return;
        favoriteDetail = new FavoriteDetailView(this,item,prefs,lightMode,
                this::closeFavoriteDetail,()->{
                    releaseFromStars(item);
                    if(myPage!=null)myPage.remove(item.id);
                    closeFavoriteDetail();toast("已取消本地收藏，原照片未删除");
                },()->{if(myPage!=null)myPage.refresh();});
        final FavoriteDetailView page=favoriteDetail;
        if(myPage!=null)myPage.setVisibility(View.GONE);
        FrameLayout.LayoutParams detailLp=new FrameLayout.LayoutParams(-1,-1);detailLp.topMargin=dp(60);root.addView(page,detailLp);topBar.bringToFront();
        io.execute(()->{
            Bitmap image=decodeByImageDecoder(item);Meta m=meta(item);
            if(image==null)image=decodeByBitmapFactory(item,m);
            if(image==null)image=decodeBySystemThumbnail(item);
            final Bitmap bitmap=image;final String when=detailTime(item,m);
            final String cached=prefs.getString("place:"+item.identity(),"");
            final String coordinates=m.hasGps?PhotoPlace.coords(m.lat,m.lon):"";
            String location=m.hasGps?(cached.isEmpty()?coordinates:cached+(cached.contains("纬度")?"":"\n"+coordinates)):(hasLocationPermission()?(m.exifRead?"原照片无GPS，无法提供经纬度":"照片位置读取失败，可重试"):"未授权照片位置信息");
            main.post(()->{
                if(favoriteDetail!=page){if(bitmap!=null)bitmap.recycle();return;}
                page.content(bitmap,when,location,!m.hasGps&&m.exifRead&&hasLocationPermission());
                if(m.hasGps&&cached.isEmpty()){
                    main.postDelayed(()->{if(favoriteDetail==page)page.location(prefs.getString("place:"+item.identity(),"地点未解析 · "+PhotoPlace.coords(m.lat,m.lon)));},8000);
                    PhotoPlace.resolve(this,m.lat,m.lon,answer->{
                        if(!answer.startsWith("地点未解析"))prefs.edit().putString("place:"+item.identity(),answer).apply();
                        if(favoriteDetail==page)page.location(answer);
                    });
                }
            });
        });
    }
    private String detailTime(PhotoItem item,Meta m){
        if(m.dateTime!=null)try{SimpleDateFormat input=new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",Locale.US);input.setLenient(false);Date date=input.parse(m.dateTime);return new SimpleDateFormat("yyyy年M月d日 HH:mm:ss",Locale.CHINA).format(date);}catch(Exception ignored){}
        return item.dateTaken>0?new SimpleDateFormat("yyyy年M月d日 HH:mm:ss",Locale.CHINA).format(new Date(item.dateTaken)):"无拍摄时间";
    }
    private void closeFavoriteDetail(){
        if(favoriteDetail==null)return;favoriteDetail.dispose();root.removeView(favoriteDetail);favoriteDetail=null;
        if(myPage!=null){myPage.setVisibility(View.VISIBLE);myPage.refresh();}
    }

    private void showSettings() {
        final boolean[] choice = {swapSwipe};
        final boolean[] themeChoice = {lightMode};

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(12), dp(20), dp(2));

        TextView head = label("左右滑动的含义", 13f, C_DIM, false);
        try {
            TextView version = label("相册滑选（单机版） v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName + " · 单机模式 · 开发者：RonMao", 11f, C_FAINT, false);
            version.setPadding(0, 0, 0, dp(10)); col.addView(version);
        } catch (Exception ignored) { }
        col.addView(head);

        final TextView optA = optionRow("左滑删除 · 右滑保留", !choice[0]);
        final TextView optB = optionRow("左滑保留 · 右滑删除", choice[0]);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin = dp(8);
        col.addView(optA, op);
        LinearLayout.LayoutParams op2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op2.topMargin = dp(8);
        col.addView(optB, op2);

        optA.setOnClickListener(v -> {
            choice[0] = false;
            styleOption(optA, true);
            styleOption(optB, false);
        });
        optB.setOnClickListener(v -> {
            choice[0] = true;
            styleOption(optA, false);
            styleOption(optB, true);
        });

        TextView note = label("确认后立刻生效，底部三个按钮的图标与滑动印章跟着换；上滑星标不受影响。",
                12f, C_FAINT, false);
        note.setPadding(0, dp(10), 0, dp(2));
        col.addView(note);

        TextView modeHead = label("外观模式", 13f, C_DIM, false);
        modeHead.setPadding(0, dp(16), 0, dp(4)); col.addView(modeHead);
        TextView darkOption = optionRow("黑色 · 复古胶片", !themeChoice[0]);
        TextView lightOption = optionRow("白色 · 半透明磨玻璃", themeChoice[0]);
        col.addView(darkOption, op); col.addView(lightOption, op2);
        darkOption.setOnClickListener(v -> { themeChoice[0] = false; styleOption(darkOption, true); styleOption(lightOption, false); });
        lightOption.setOnClickListener(v -> { themeChoice[0] = true; styleOption(darkOption, false); styleOption(lightOption, true); });

        TextView check = label("删除核验与本地记录", 12.5f, lightMode ? C_DIM : 0xFFE0B46A, false);
        check.setPadding(0, dp(14), 0, dp(2));
        check.setOnClickListener(v -> showSystemCheck());
        col.addView(check);

        TextView warning = label("地点解析会向系统地名服务查询照片GPS，不上传照片或故事；离线时显示未解析。",11f,C_FAINT,false);warning.setPadding(0,dp(12),0,dp(6));col.addView(warning);
        TextView reset = label("恢复收藏确认提示",12f,C_DIM,false);reset.setPadding(0,dp(8),0,dp(8));reset.setOnClickListener(v->{prefs.edit().putBoolean("skip_star_confirm",false).commit();toast("下次确认收藏会提示");});col.addView(reset);
        ScrollView settingsScroll = new ScrollView(this); settingsScroll.addView(col);
        new AlertDialog.Builder(this, lightMode ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("设置")
                .setView(settingsScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (d, w) -> {
                    boolean themeChanged = themeChoice[0] != lightMode;
                    swapSwipe = choice[0]; lightMode = themeChoice[0];
                    prefs.edit().putBoolean("swap_swipe", swapSwipe).putBoolean("light_mode", lightMode).commit();
                    refreshSwipeLabels(); savePending();
                    if (themeChanged) rebuildTheme();
                    toast("设置已保存");
                })
                .show();
    }

    private TextView optionRow(String text, boolean selected) {
        TextView tv = label((selected ? "● " : "○ ") + text, 14f, selected ? C_TEXT : C_DIM, selected);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(16), dp(13), dp(16), dp(13));
        styleOption(tv, selected);
        return tv;
    }

    private void styleOption(TextView tv, boolean selected) {
        String text = tv.getText().toString();
        text = text.substring(2);
        tv.setText((selected ? "● " : "○ ") + text);
        tv.setTextColor(selected ? C_TEXT : C_DIM);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        GradientDrawable g = roundBg(selected ? 0xFF2B2118 : 0xFF1B1B1F, 14);
        if (selected) g.setStroke(dp(1), 0x88C8B896);
        tv.setBackground(g);
    }

    /** 自检：把系统里真正标了回收站和收藏的条数读出来，避免"以为成功" */
    private void showSystemCheck() {
        StringBuilder sb = new StringBuilder();
        sb.append("删除请求：永久删除，不进入最近删除；本应用无法恢复");
        if (Build.VERSION.SDK_INT >= 30) {
        sb.append("\n说明：以下统计来自 Android MediaStore，不能证明荣耀图库的对应相册已经显示。\n系统回收站字段为 1：").append(countSystemFlag(true)).append(" 张");
            sb.append("\n系统收藏字段为 1：").append(countSystemFlag(false)).append(" 张");
        }
        sb.append("\n机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append("（API ").append(Build.VERSION.SDK_INT).append("）");
        sb.append("\n持久记录：").append(ledger.count()).append(" 项；本轮已处理：").append(seen.size());
        sb.append("\n覆盖安装保留记录；卸载或清数据会丢失。v1.8及以前未持久保存的保留记录无法追溯恢复。");
        String log = prefs.getString("op_log", "");
        if (log != null && log.length() > 0) {
            sb.append("\n\n最近的操作：\n").append(log);
        }
        new AlertDialog.Builder(this)
                .setTitle("系统联动自检")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private int countSystemFlag(boolean trash) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        String col = trash ? MediaStore.MediaColumns.IS_TRASHED : MediaStore.MediaColumns.IS_FAVORITE;
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try {
            if (trash) {
                // 回收站里的行默认不出现在查询结果里，必须显式要求把它们也带上
                Bundle args = new Bundle();
                args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
                args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, col + "=?");
                args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[]{"1"});
                try (Cursor c = cr.query(uri, new String[]{MediaStore.MediaColumns._ID}, args, null)) {
                    return c == null ? 0 : c.getCount();
                }
            }
            try (Cursor c = cr.query(uri, new String[]{MediaStore.MediaColumns._ID},
                    col + "=?", new String[]{"1"}, null)) {
                return c == null ? 0 : c.getCount();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private void resetZoom() {
        zoom = 1f;
        zoomX = 0f;
        zoomY = 0f;
        applyZoom();
    }

    private float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void applyDrag(float dx, float dy) {
        int w = Math.max(1, cardFront.getWidth());
        int h = Math.max(1, cardFront.getHeight());
        boolean vertical = Math.abs(dy) > Math.abs(dx) * 1.25f && dy < 0;

        if (vertical) {
            cardFront.setTranslationX(dx * 0.15f);
            cardFront.setTranslationY(dy);
            cardFront.setRotation(0f);
            float p = clamp(-dy / (h * 0.26f));
            stampStar.setAlpha(p);
            stampKeep.setAlpha(0f);
            stampDelete.setAlpha(0f);
        } else {
            cardFront.setTranslationX(dx);
            cardFront.setTranslationY(dy * 0.25f);
            cardFront.setRotation(dx / w * 7f);
            int act = dxAction(dx);
            float p = clamp(Math.abs(dx) / (w * 0.34f));
            stampKeep.setAlpha(act == KEEP ? p : 0f);
            stampDelete.setAlpha(act == DELETE ? p : 0f);
            stampStar.setAlpha(0f);
        }
    }

    private void decide(float dx, float dy, float vx, float vy) {
        if (zoom > 1.02f) {
            resetCardVisual();
            return;
        }
        int w = Math.max(1, cardFront.getWidth());
        int h = Math.max(1, cardFront.getHeight());
        boolean up = Math.abs(dy) > Math.abs(dx) * 1.25f && dy < 0;

        // 距离到阈值，或甩得够快且已有位移，两种情况都算成交，轻扫不再"滑不动"
        if (up) {
            if (-dy > h * 0.12f || (-dy > dp(24) && vy < -dp(260))) commit(STAR);
            else resetCardVisual();
            return;
        }
        if (Math.abs(dx) > w * 0.18f
                || (Math.abs(dx) > dp(24) && Math.abs(vx) > dp(300) && Math.signum(vx) == Math.signum(dx))) {
            commit(dxAction(dx));
            return;
        }
        resetCardVisual();
    }

    private void resetCardVisual() {
        cardFront.animate().cancel();
        cardFront.setTranslationX(0f);
        cardFront.setTranslationY(0f);
        cardFront.setRotation(0f);
        cardFront.setAlpha(1f);
        cardFront.setScaleX(1f);
        cardFront.setScaleY(1f);
        if (stampKeep != null) {
            stampKeep.setAlpha(0f);
            stampDelete.setAlpha(0f);
            stampStar.setAlpha(0f);
        }
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ===================== 动作 =====================

    private void commit(int type) {
        if (current == null || advancing || systemBusy || !previewReady) return;
        advancing = true;
        if (dynamicPreview != null) dynamicPreview.stop();
        history.add(new Act(current, type));
        ledger.record(current.identity(), current.signature, current.digest, false);
        seen.add(current.id);
        saveSeen();
        if (type == STAR) {
            pendingStars.add(current.id);
        } else if (type == DELETE) {
            deleteBucket.add(current.id);
        } else {
            keptCount++;
        }
        savePending();
        cardFront.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        showUndoBar(type);
        updateTopBar();
        animateOff(type);
    }

    private void animateOff(int type) {
        int w = Math.max(1, cardFront.getWidth());
        int h = Math.max(1, cardFront.getHeight());
        float direction = cardFront.getTranslationX();
        if (Math.abs(direction) < dp(10)) direction = type == DELETE ? (swapSwipe ? 1 : -1) : (swapSwipe ? -1 : 1);
        final float side = direction < 0 ? -1f : 1f;
        cardFront.animate().cancel();
        cardFront.animate()
                .setDuration(240)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0.2f,1f))
                .translationX(type == STAR ? 0f : side*(w + dp(80)))
                .translationY(type == STAR ? -(h + dp(80)) : 0f)
                .rotation(type == STAR ? 0f : side*14f)
                .alpha(1f)
                .withEndAction(() -> {
                    cardFront.setVisibility(View.INVISIBLE);
                    resetCardVisual();
                    advance();
                })
                .start();
    }

    private boolean isReviewed(PhotoItem item){
        // 已收进收藏相册的照片不再回滑选；取消星标后由 releaseFromStars 放开。
        return item!=null&&(starred.contains(item.id)||seen.contains(item.id)||deleteBucket.contains(item.id)||pendingStars.contains(item.id)||ledger.contains(item.identity(),item.signature)||ledger.containsDigest(item.digest));
    }
    /** 取消星标：清掉这一轮的已看过痕迹，把照片放回滑选队列尾部 */
    private void releaseFromStars(PhotoItem item){
        if(item==null)return;
        starred.remove(item.id);persistStars();
        seen.remove(item.id);saveSeen();
        ledger.undo(item.identity());
        boolean queued=current!=null&&current.id==item.id;
        for(PhotoItem p:pending)if(p.id==item.id)queued=true;
        if(!queued&&all.contains(item))pending.addLast(item);
    }
    /** 只挑这一轮还没看过的照片；看完了才允许重来 */
    private void refillQueue() {
        List<PhotoItem> cand = new ArrayList<>();
        for (PhotoItem it : all) {
            if (isReviewed(it)) continue;
            cand.add(it);
        }
        Collections.shuffle(cand);
        pending.clear();
        pending.addAll(cand);
        current = pending.poll();
    }

    private int unseenCount() {
        int n = 0;
        for (PhotoItem it : all) {
            if (isReviewed(it)) continue;
            n++;
        }
        return n;
    }

    private void advance() {
        advancing = false;
        do { current = pending.poll(); }
        while (current != null && isReviewed(current));
        if (current == null) {
            showEmpty();updateTopBar();
        } else {
            renderCurrent();
        }
    }

    private void undo() {
        if (history.isEmpty() || systemBusy) {
            toast("还没有可撤销的操作");
            return;
        }
        cardFront.animate().cancel();
        Act a = history.remove(history.size() - 1);
        advancing = false;
        ledger.undo(a.item.identity());
        seen.remove(a.item.id);
        saveSeen();
        if (a.type == STAR) {
            pendingStars.remove(a.item.id);
        } else if (a.type == DELETE) {
            deleteBucket.remove(a.item.id);
        } else {
            keptCount = Math.max(0, keptCount - 1);
        }
        savePending();
        if (current != null) pending.addFirst(current);
        current = a.item;
        hideUndoBar();
        if (summaryView != null) summaryView.setVisibility(View.GONE);
        deck.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        renderCurrent();
        toast("已撤销，回到这张照片");
    }

    private void showUndoBar(int type) {
        String s = type == DELETE ? "已标记待删" : (type == STAR ? "已标记待星标" : "已保留");
        undoLabel.setText(s + "  ");
        undoBar.setVisibility(View.VISIBLE);
        undoBar.setAlpha(0f);
        undoBar.setTranslationY(dp(12));
        undoBar.animate().alpha(1f).translationY(0f).setDuration(160).start();

        if (hideUndoRunnable != null) main.removeCallbacks(hideUndoRunnable);
        hideUndoRunnable = this::hideUndoBar;
        main.postDelayed(hideUndoRunnable, 6000);
    }

    private void hideUndoBar() {
        if (hideUndoRunnable != null) main.removeCallbacks(hideUndoRunnable);
        undoBar.animate().alpha(0f).setDuration(140).withEndAction(() -> undoBar.setVisibility(View.GONE)).start();
    }

    private void persistStars() {
        Set<String> s = new HashSet<>();
        for (Long id : starred) s.add(String.valueOf(id));
        prefs.edit().putStringSet("starred_ids", s).commit();
    }

    /** 未结算的待删、待星记号，退出或进程被杀都要留着 */
    private void loadSeen() {
        seen.clear();
        String raw = prefs.getString("seen_ids", "");
        if (raw != null && raw.length() > 0) {
            for (String part : raw.split(",")) {
                try {
                    seen.add(Long.parseLong(part.trim()));
                } catch (Exception ignored) {
                }
            }
        }
        // 清掉相册里已经不存在（被删或被移走）的 id
        Set<Long> alive = new HashSet<>();
        for (PhotoItem it : all) {
            alive.add(it.id);
            if (seen.contains(it.id) && !ledger.contains(it.identity(), it.signature)) ledger.record(it.identity(), it.signature, null, false);
            if (ledger.contains(it.identity(), it.signature)) seen.add(it.id);
        }
        seen.retainAll(alive);
        saveSeen();
    }

    private void saveSeen() {
        StringBuilder sb = new StringBuilder();
        for (Long id : seen) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString("seen_ids", sb.toString()).commit();
    }

    private void clearSeen() {
        seen.clear();
        ledger.newRound();
        prefs.edit().remove("seen_ids").commit();
    }

    /** 操作日志：把最近几次系统交互的结果留在设置里，方便真机上核对 */
    private void logOp(String line) {
        String old = prefs.getString("op_log", "");
        String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String merged = ts + " " + line + (old == null || old.isEmpty() ? "" : "\n" + old);
        String[] lines = merged.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 12); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        prefs.edit().putString("op_log", sb.toString()).apply();
    }

    private void savePending() {
        Set<String> d = new HashSet<>();
        for (Long id : deleteBucket) d.add(String.valueOf(id));
        Set<String> st = new HashSet<>();
        for (Long id : pendingStars) st.add(String.valueOf(id));
        prefs.edit()
                .putStringSet("settle_delete", d)
                .putStringSet("settle_star", st)
                .putInt("round_kept", keptCount)
                .commit();
    }

    /** 启动时接上一轮没结算的记号，只保留还存在于相册里的 */
    private void loadPending() {
        keptCount = prefs.getInt("round_kept", 0);
        deleteBucket.clear();
        pendingStars.clear();
        Set<String> d = prefs.getStringSet("settle_delete", null);
        if (d != null) {
            for (String x : d) {
                long id = parseLong(x);
                if (id > 0) deleteBucket.add(id);
            }
        }
        Set<String> st = prefs.getStringSet("settle_star", null);
        if (st != null) {
            for (String x : st) {
                long id = parseLong(x);
                if (id > 0) pendingStars.add(id);
            }
        }
        savePending();
    }

    private long parseLong(String x) {
        try {
            return Long.parseLong(x);
        } catch (Exception e) {
            return -1;
        }
    }

    // ===================== 相片详情 =====================

    private void showDetail() {
        if (current == null) return;
        PhotoItem it = current;
        Meta m = metaCache.get(it.id);
        StringBuilder sb = new StringBuilder();
        sb.append("拍摄时间  ").append(m != null && m.dateTime != null ? m.dateTime : (formatMs(it.dateTaken) == null ? "未知" : formatMs(it.dateTaken)));
        sb.append("\n拍摄地点  ").append(m != null && m.hasGps
                ? String.format(Locale.US, "%.6f, %.6f", m.lat, m.lon)
                : "无 GPS 信息");
        sb.append("\n设备  ").append(deviceName(m));
        sb.append("\n尺寸  ").append(m != null && m.width > 0 ? (m.width + " × " + m.height) : "未知");
        sb.append("\n大小  ").append(bytes(it.size));
        sb.append("\n文件  ").append(it.name);
        sb.append("\nID  ").append(it.id);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("照片信息");
        b.setMessage(sb.toString());
        if (m != null && m.hasGps) {
            b.setNeutralButton("地图查看", (d, w) -> openMap(m.lat, m.lon));
        }
        b.setPositiveButton("在相册打开", (d, w) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(it.uri, "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e) {
                toast("没有可打开这张照片的应用");
            }
        });
        b.setNegativeButton("关闭", null);
        b.show();
    }

    private void openMap(double lat, double lon) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
        } catch (Exception e) {
            toast("没有可用的地图应用");
        }
    }

    // ===================== 真删除 =====================

    private void confirmBatchDelete() {
        if (systemBusy) { toast("正在等待系统确认，请稍候"); return; }
        if (deleteBucket.isEmpty()) {
            toast("还没有标记为删除的照片");
            return;
        }
        int n = deleteBucket.size();
        new AlertDialog.Builder(this)
                .setTitle("永久删除 " + n + " 张照片？")
                .setMessage("删除后无法复原，请确认。")
                .setPositiveButton("确认删除", (d, w) -> performDelete(collectBucketItems()))
                .setNegativeButton("取消", null)
                .show();
    }

    private List<PhotoItem> collectBucketItems() {
        List<PhotoItem> list = new ArrayList<>();
        for (Long id : deleteBucket) {
            PhotoItem it = findByPhotoId(id);
            if (it != null) list.add(it);
        }
        return list;
    }

    private PhotoItem findByPhotoId(long id) {
        for (PhotoItem p : all) if (p.id == id) return p;
        return null;
    }

    private void performDelete(List<PhotoItem> items) {
        if (items.isEmpty()) {
            toast("这些照片已经不在相册中了");
            return;
        }
        List<Uri> uris = new ArrayList<>();
        int cap = Math.min(items.size(), 2000);
        for (int i = 0; i < cap; i++) uris.add(items.get(i).uri);

        lastDeleteUris.clear();
        lastDeleteUris.addAll(uris);
        systemBusy = true;
        prefs.edit().putBoolean("request_permanent", true).commit();
        saveLastRequests();

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.PendingIntent pi = MediaStore.createDeleteRequest(cr, uris);
                logOp("永久删除请求已发出 " + uris.size() + " 张");
                startIntentSenderForResult(pi.getIntentSender(), REQ_PERMANENT_DELETE, null, 0, 0, 0);
                return;
            } catch (Exception e) {
                logOp("永久删除请求失败 " + e.getClass().getSimpleName());
                systemBusy = false;
                lastDeleteUris.clear(); saveLastRequests();
                toast("系统未接受永久删除请求，待办仍保留，请检查权限后重试");
                return;
            }
        } else {
            int ok = 0;
            for (Uri u : uris) {
                try {
                    if (cr.delete(u, null, null) > 0) ok++;
                } catch (SecurityException e) {
                    toast("系统版本较低，请在相册里手动删除");
                    break;
                } catch (Exception ignored) {
                }
            }
            onDeleted(ok);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DELETE || requestCode == REQ_PERMANENT_DELETE) {
            if (resultCode == RESULT_OK) {
                logOp("系统确认删除 OK");
                main.postDelayed(() -> onDeleted(lastDeleteUris.size()), 350);
            } else {
                logOp("系统删除被取消 resultCode=" + resultCode);
                systemBusy = false;
                lastDeleteUris.clear(); saveLastRequests();
                toast("已取消删除，待办仍保留");
            }
        } else if (requestCode == REQ_FAVORITE) {
            if (resultCode == RESULT_OK) {
                int ok = countFlag(lastFavUris, MediaStore.MediaColumns.IS_FAVORITE, lastFavValue ? 1 : 0);
                logOp("收藏请求 OK，回读匹配 " + ok + "/" + lastFavUris.size());
                if (ok < lastFavUris.size()) {
                    // 只对尚未核验成功项尝试补写；无写权限时会保留待办。
                    int w = writeFavorite(lastFavUris, lastFavValue);
                    ok = countFlag(lastFavUris, MediaStore.MediaColumns.IS_FAVORITE, lastFavValue ? 1 : 0);
                    logOp("请求后未落盘，补写更新 " + w + " 行，复核匹配 " + ok + "/" + lastFavUris.size());
                }
                finishFavorites();
            } else {
                logOp("系统收藏被取消 resultCode=" + resultCode);
                systemBusy = false;
                lastFavUris.clear(); saveLastRequests();
                toast("系统收藏未确认，待星标清单仍保留，可重试");
            }
        } else if (requestCode == REQ_FAVORITE_WRITE) {
            if (resultCode == RESULT_OK) {
                writeFavorite(lastFavUris, lastFavValue);
                finishFavorites();
            } else {
                systemBusy = false;
                lastFavUris.clear(); saveLastRequests();
                toast("系统收藏未确认，待星标清单仍保留，可重试");
            }
        }
    }

    // ===================== 系统收藏（我的收藏） =====================

    private final List<Uri> lastFavUris = new ArrayList<>();
    private boolean lastFavValue = true;

    private void requestFavorite(List<Uri> uris, boolean fav) {
        if (uris.isEmpty()) return;
        lastFavUris.clear();
        lastFavUris.addAll(uris);
        lastFavValue = fav;
        systemBusy = true; saveLastRequests();
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.PendingIntent pi = MediaStore.createFavoriteRequest(cr, uris, fav);
                startIntentSenderForResult(pi.getIntentSender(), REQ_FAVORITE, null, 0, 0, 0);
            } catch (Exception e) {
                logOp("收藏请求失败 " + e.getClass().getSimpleName() + "：" + e.getMessage());
                try {
                    android.app.PendingIntent pi = MediaStore.createWriteRequest(cr, uris);
                    logOp("改走写权限请求再写收藏");
                    startIntentSenderForResult(pi.getIntentSender(), REQ_FAVORITE_WRITE, null, 0, 0, 0);
                    return;
                } catch (Exception e2) {
                    logOp("写权限请求也失败 " + e2.getClass().getSimpleName());
                }
                systemBusy = false;
                toast("系统拒绝了收藏请求；待星标清单保留：" + e.getClass().getSimpleName());
            }
        } else {
            systemBusy = false;
            toast("系统版本低于 Android 11，没有系统收藏字段，星标只保存在本应用内");
        }
    }

    private int writeFavorite(List<Uri> uris, boolean fav) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        int ok = 0;
        for (Uri u : uris) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.IS_FAVORITE, fav ? 1 : 0);
                if (cr.update(u, cv, null, null) > 0) ok++;
            } catch (Exception ignored) {
            }
        }
        return ok;
    }

    private void onDeleted(int ignoredCount) {
        boolean permanent = prefs.getBoolean("request_permanent", false);
        final Set<Long> verified = new HashSet<>();
        int trashed = 0, absent = 0, alive = 0, unknown = 0;
        for (Uri u : lastDeleteUris) {
            int state = readFlag(u, Build.VERSION.SDK_INT >= 30 ? MediaStore.MediaColumns.IS_TRASHED : MediaStore.MediaColumns._ID);
            int fileState = permanent && state == -1 ? deletedFileState(u) : 0;
            boolean success = Build.VERSION.SDK_INT >= 30 && !permanent ? state == 1 : state == -1 && fileState == 1;
            if (state == 1 && Build.VERSION.SDK_INT >= 30 && !permanent) trashed++;
            else if (state == -1 && (!permanent || fileState == 1)) absent++;
            else if (state == -2 || fileState == -1) unknown++;
            else alive++;
            if (success) {
                long id = ContentUris.parseId(u);
                PhotoItem it = findByPhotoId(id);
                if (it != null) ledger.record(it.identity(), it.signature, it.digest, true);
                else ledger.markDeleted(u.toString());
                verified.add(id);
            }
        }
        logOp("删除逐张核验：回收站=" + trashed + "，不存在=" + absent + "，仍在=" + alive + "，不可读=" + unknown + "，总数=" + lastDeleteUris.size());
        systemBusy = false;
        purge(verified); // 仅移除核验成功项，不假装整批成功
        savePending();
        showSettlement(false);
        if (Build.VERSION.SDK_INT >= 30 && !permanent) {
            toast("系统回收站标记已写入 " + verified.size() + " 张；失败或未确认 " + (lastDeleteUris.size() - verified.size()) + " 张仍保留待办。荣耀图库可见性尚未核验");
        } else toast("已核验彻底删除 " + verified.size() + " 张；其余保留待办（无法恢复）");
        lastDeleteUris.clear();
        saveLastRequests();
    }

    /** 1=file not found with permission intact; 0=can open; -1=permission or I/O uncertainty. */
    private int deletedFileState(Uri uri) {
        if (accessState() != 2) return -1;
        try (InputStream input = cr.openInputStream(uri)) {
            return input == null ? -1 : 0;
        } catch (java.io.FileNotFoundException e) {
            return accessState() == 2 ? 1 : -1;
        } catch (Exception e) { return -1; }
    }

    /** 1/0 为字段值；-1 明确无行；-2 权限/查询错误。禁止混为删除成功。 */
    private int readFlag(Uri uri, String column) {
        try {
            Cursor cursor;
            if (Build.VERSION.SDK_INT >= 30) {
                Bundle args = new Bundle();
                args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
                args.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
                cursor = cr.query(uri, new String[]{column}, args, null);
            } else cursor = cr.query(uri, new String[]{column}, null, null, null);
            if (cursor == null) return -2;
            try (Cursor c = cursor) {
                if (!c.moveToFirst()) return -1;
                return c.isNull(0) ? -2 : c.getInt(0);
            }
        } catch (Exception e) { return -2; }
    }

    private int countFlag(List<Uri> uris, String column, int value) {
        int n = 0;
        for (Uri u : uris) if (readFlag(u, column) == value) n++;
        return n;
    }

    private void finishFavorites() {
        int success = 0;
        for (Uri u : lastFavUris) {
            if (readFlag(u, MediaStore.MediaColumns.IS_FAVORITE) == (lastFavValue ? 1 : 0)) {
                long id = ContentUris.parseId(u);
                if (lastFavValue) { starred.add(id); pendingStars.remove(id); }
                else starred.remove(id);
                history.removeIf(a -> a.item.id == id && a.type == STAR);
                success++;
            }
        }
        logOp("系统收藏逐张核验 " + success + "/" + lastFavUris.size());
        persistStars(); savePending(); systemBusy = false;
        showSettlement(false);
        toast("系统收藏标记已写入 " + success + " 张；未成功项保留待办。荣耀图库「我的收藏」可见性尚未核验");
        lastFavUris.clear(); saveLastRequests();
    }

    private void saveLastRequests() {
        Set<String> ds = new HashSet<>(), fs = new HashSet<>();
        for (Uri u : lastDeleteUris) ds.add(u.toString());
        for (Uri u : lastFavUris) fs.add(u.toString());
        prefs.edit().putStringSet("request_delete", ds).putStringSet("request_favorite", fs)
                .putBoolean("request_favorite_value", lastFavValue).commit();
    }

    private void restoreLastRequests() {
        for (String s : prefs.getStringSet("request_delete", Collections.emptySet())) lastDeleteUris.add(Uri.parse(s));
        for (String s : prefs.getStringSet("request_favorite", Collections.emptySet())) lastFavUris.add(Uri.parse(s));
        lastFavValue = prefs.getBoolean("request_favorite_value", true);
    }

    private void purge(Set<Long> ids) {
        if (ids.isEmpty()) return;
        List<PhotoItem> removed = new ArrayList<>();
        for (PhotoItem p : all) if (ids.contains(p.id)) removed.add(p);
        all.removeAll(removed);
        pending.removeAll(removed);
        deleteBucket.removeAll(ids);
        pendingStars.removeAll(ids);
        seen.removeAll(ids); saveSeen();
        starred.removeAll(ids);
        history.removeIf(a -> ids.contains(a.item.id));
        persistStars();
        savePending();
        for (Long id : ids) {
            cache.remove(key(id));
            metaCache.remove(id);
        }
        if (current != null && ids.contains(current.id)) {
            current = pending.poll();
            if (current != null) {
                renderCurrent();
            } else {
                showSummary();
            }
        } else {
            updateTopBar();
            if (summaryView != null && summaryView.getVisibility() == View.VISIBLE) showSummary();
        }
    }

    // ===================== 结算页 =====================

    private void showSummary() {
        showSettlement(false);
    }

    /**
     * 结算页：本轮标记的待删与待星都在这里确认。
     * restored = true 表示这是上次退出时留下的未结算记录。
     */
    private void showSettlement(boolean restored) {
        if(advancing){cardFront.animate().cancel();resetCardVisual();advancing=false;current=null;}
        dismissMyPage();activeTab=2;updateTabs();topBar.setVisibility(View.VISIBLE);deck.setVisibility(View.VISIBLE);
        renderGeneration++;
        advancing = false;
        if (dynamicPreview != null) dynamicPreview.stop();
        tvLoading.setVisibility(View.GONE);
        cardFront.setVisibility(View.GONE);
        cardBack.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
        chipDelete.setVisibility(View.GONE);
        btnUndo.setVisibility(View.GONE);
        btnFinish.setVisibility(View.VISIBLE);
        tvProgress.setText("我的");
        updateTopBar();
        hideUndoBar();

        if (summaryView != null) deck.removeView(summaryView);
        summaryView = new ScrollView(this);
        summaryView.setFillViewport(true);
        FrameLayout.LayoutParams svp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        svp.topMargin = dp(FILM_BAND);
        svp.bottomMargin = dp(FILM_BAND);
        deck.addView(summaryView, svp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(18));
        summaryView.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean anyPending = !deleteBucket.isEmpty() || !pendingStars.isEmpty();
        String titleText;
        if (unseenCount() == 0 && !all.isEmpty()) titleText = anyPending ? "结算这一轮" : "这一轮看完了";
        else if (!history.isEmpty()) {
            titleText = anyPending ? "结算这一轮" : "本轮没有待确认的操作";
        } else titleText = anyPending ? "还有没结算的操作" : "本轮没有待确认的操作";
        TextView title = label(titleText, 20f, C_TEXT, true);
        col.addView(title);

        String subText = unseenCount() == 0
                ? "本轮照片都刷过一遍了 · 已处理 " + seen.size() + " 张"
                : "本次处理 " + history.size() + " 张 · 本轮累计 " + seen.size() + " 张 · 还有 " + unseenCount() + " 张没刷到";
        TextView sub = label(subText, 12f, C_DIM, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(5);
        col.addView(sub, p);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        col.addView(stats, sp);
        stats.addView(statBox("待删", String.valueOf(deleteBucket.size()), C_DELETE), weight());
        stats.addView(statBox("待星标", String.valueOf(pendingStars.size()), C_STAR), weight());
        stats.addView(statBox("保留", String.valueOf(keptCount), C_KEEP), weight());

        TextView del = label(deleteBucket.isEmpty() ? "没有待删除的照片" : "确认删除 " + deleteBucket.size() + " 张",
                SETTLE_BTN_TEXT, deleteBucket.isEmpty() ? C_FAINT : C_DELETE, true);
        del.setText(deleteBucket.isEmpty() ? "没有待删除的照片" : "永久删除 " + deleteBucket.size() + " 张");
        del.setGravity(Gravity.CENTER);
        del.setBackground(roundBg(deleteBucket.isEmpty() ? 0xFF17171A : 0xFF3E1F20, SETTLE_BTN_RADIUS));
        del.setAlpha(deleteBucket.isEmpty() ? 0.5f : 1f);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SETTLE_BTN_H));
        dp2.topMargin = dp(12);
        col.addView(del, dp2);
        del.setOnClickListener(v -> {
            if (deleteBucket.isEmpty()) {
                toast("没有待删除的照片");
            } else {
                confirmBatchDelete();
            }
        });

        TextView starBtn = label(pendingStars.isEmpty() ? "没有待星标的照片" : "确认星标 " + pendingStars.size() + " 张",
                SETTLE_BTN_TEXT, pendingStars.isEmpty() ? C_FAINT : C_STAR, true);
        starBtn.setGravity(Gravity.CENTER);
        starBtn.setBackground(roundBg(pendingStars.isEmpty() ? 0xFF17171A : 0xFF3A2C10, SETTLE_BTN_RADIUS));
        starBtn.setAlpha(pendingStars.isEmpty() ? 0.5f : 1f);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SETTLE_BTN_H));
        stp.topMargin = dp(6);
        col.addView(starBtn, stp);
        starBtn.setOnClickListener(v -> confirmStars());

        boolean exhausted = unseenCount() == 0;
        TextView cont = label("继续滑选", SETTLE_BTN_TEXT, C_KEEP, true);
        cont.setGravity(Gravity.CENTER);
        cont.setBackground(roundBg(0xFF1E3427, SETTLE_BTN_RADIUS));
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SETTLE_BTN_H));
        ap2.topMargin = dp(6);
        col.addView(cont, ap2);
        cont.setOnClickListener(v -> showBrowsing());
        if(exhausted){TextView restart=label("从 0 开始新一轮",12f,C_DIM,true);restart.setPadding(0,dp(10),0,dp(10));col.addView(restart);restart.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("重新浏览一轮？").setMessage("已保留的照片会重新进入滑选。待办和已删除指纹不清除。").setNegativeButton("取消",null).setPositiveButton("重新开始",(d,w)->{clearSeen();history.clear();keptCount=0;current=null;savePending();showBrowsing();}).show());}

        TextView drop = label("放弃本轮的标记", SETTLE_BTN_TEXT, lightMode ? C_DELETE : 0xFFFF8A8A, true);
        drop.setGravity(Gravity.CENTER);
        drop.setBackground(roundBg(0xFF2B1E1C, SETTLE_BTN_RADIUS));
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SETTLE_BTN_H));
        drp.topMargin = dp(6);
        col.addView(drop, drp);
        drop.setOnClickListener(v -> confirmDropRound());

        TextView note = label("没确认就退出：本轮删除与星标都不生效，记录留到下次打开的这一页。",
                11f, C_FAINT, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(8);
        col.addView(note, np);

        addPendingList(col, "待删除清单", deleteBucket, true);
        addPendingList(col, "待星标清单", pendingStars, false);
    }

    private void addPendingList(LinearLayout col, String titleText, Set<Long> ids, boolean isDelete) {
        if (ids.isEmpty()) return;
        TextView h = label(titleText + " · " + ids.size() + " 张", 13f, C_DIM, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(14);
        col.addView(h, hp);

        TextView tip = label("点缩略图放大，可撤销这一张", 10.5f, C_FAINT, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(3);
        col.addView(tip, tp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(8);
        col.addView(grid, gp);

        final int perRow = 4;
        int shown = 0;
        int total = ids.size();
        LinearLayout row = null;
        for (Long id : ids) {
            PhotoItem it = findByPhotoId(id);
            if (it == null) {
                TextView missing = label("原图暂不可读 · 待办仍保留（请检查权限）", 11f, C_DIM, false);
                missing.setOnClickListener(v -> openPendingPreview(id, isDelete));
                grid.addView(missing); continue;
            }
            if (shown >= 40) {
                TextView more = label("… 还有 " + (total - shown) + " 张", 11f, C_FAINT, false);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mp.topMargin = dp(6);
                col.addView(more, mp);
                break;
            }
            if (shown % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (shown > 0) rp.topMargin = dp(6);
                grid.addView(row, rp);
            }

            FrameLayout cell = new FrameLayout(this);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(62), dp(62));
            cp.rightMargin = dp(6);
            row.addView(cell, cp);
            cell.setOnClickListener(v -> openPendingPreview(id, isDelete));

            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(roundBg(0xFF1C1C20, 6));
            thumb.setClipToOutline(true);
            cell.addView(thumb, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View badge = new PendingBadge(this,isDelete,C_DELETE,C_STAR);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(20),dp(20));
            bp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            bp.rightMargin = dp(3);
            bp.bottomMargin = dp(3);
            cell.addView(badge, bp);

            bindThumb(thumb, it, 120);
            shown++;
        }
    }

    /** 小缩略图：优先复用已有缓存，否则按目标边长取样解码 */
    private void bindThumb(ImageView iv, PhotoItem it, int px) {
        String k = "t" + px + "_" + it.id;
        Bitmap c = cache.get(k);
        if (c != null) {
            iv.setImageBitmap(c);
            return;
        }
        io.execute(() -> {
            Bitmap b = loadThumbBitmap(it, px);
            if (b == null) return;
            cache.put(k, b);
            main.post(() -> iv.setImageBitmap(b));
        });
    }

    private Bitmap loadThumbBitmap(PhotoItem it, int px) {
        Bitmap c = cache.get(key(it.id));
        if (c != null) return c;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Bitmap b = cr.loadThumbnail(it.uri, new Size(px, px), null);
                if (b != null) return correctSystemOrientation(it,b);
            } catch (Throwable ignored) {
            }
        }
        InputStream is = null;
        try {
            BitmapFactory.Options bound = new BitmapFactory.Options();
            bound.inJustDecodeBounds = true;
            is = cr.openInputStream(it.uri);
            if (is == null) return null;
            BitmapFactory.decodeStream(is, null, bound);
            is.close();
            is = null;
            int minSide = Math.min(bound.outWidth, bound.outHeight);
            if (minSide <= 0) return null;
            int sample = 1;
            while (minSide / sample > px * 2) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            is = cr.openInputStream(it.uri);
            Bitmap b = BitmapFactory.decodeStream(is, null, o);
            if (b == null) return null;
            return PhotoOrientation.raw(b,meta(it).orientation.orientation);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 点缩略图放大，并在这里撤销这一张的删除或星标 */
    private void openPendingPreview(long id, boolean isDelete) {
        PhotoItem it = findByPhotoId(id);
        if (it == null) {
            toast("原照片当前不可读，可能权限范围改变；待办仍保留，请检查相册权限后重试");
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(10));

        ImageView big = new ImageView(this);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        big.setBackground(roundBg(0xFF101012, 8));
        big.setClipToOutline(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        box.addView(big, bp);

        TextView name = label(it.name, 13f, C_TEXT, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(12);
        box.addView(name, np);

        Meta mm = metaCache.get(id);
        String when = (mm != null && mm.dateTime != null) ? prettyTime(mm.dateTime) : formatMs(it.dateTaken);
        if (when == null) when = "未知时间";
        TextView sub = label(when + " · " + it.width + "×" + it.height, 11f, C_DIM, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        box.addView(sub, sp);

        TextView act = label(isDelete ? "撤销删除" : "撤销星标", 15f, C_TEXT, true);
        act.setGravity(Gravity.CENTER);
        act.setBackground(roundBg(isDelete ? 0xFF3A1F1F : 0xFF34280F, 26));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ap.topMargin = dp(14);
        box.addView(act, ap);

        TextView close = label("关闭", 13f, C_DIM, false);
        close.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        cp.topMargin = dp(6);
        box.addView(close, cp);

        AlertDialog dlg = new AlertDialog.Builder(this).setView(box).create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(roundBg(0xFF16161A, 14));
        }
        bindThumb(big, it, Math.min(screenW, 1080));

        act.setOnClickListener(v -> {
            if (isDelete) deleteBucket.remove(id); else pendingStars.remove(id);
            savePending();
            dlg.dismiss();
            showSettlement(false);
            toast(isDelete ? "已撤销删除这一张" : "已撤销这一张的星标");
        });
        close.setOnClickListener(v -> dlg.dismiss());
    }

    /** 从结算页回到卡片，接着滑 */
    private void resumeDeck() {
        if (systemBusy) { toast("正在等待系统操作完成"); return; }
        activeTab=1;updateTabs();deck.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);
        if(current!=null&&isReviewed(current))current=null;
        if (summaryView != null) {
            deck.removeView(summaryView);
            summaryView = null;
        }
        activeTab=1;updateTabs();
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        btnUndo.setVisibility(View.GONE);
        btnFinish.setVisibility(View.VISIBLE);
        chipDelete.setVisibility(View.GONE);

        if (current == null) {
            refillQueue();
            if (current == null) {
                showEmpty();
                updateTopBar();
                return;
            }
        }
        renderCurrent();
    }

    /** 结算页确认星标，才真正写进持久收藏 */
    private void confirmStars() {
        if (pendingStars.isEmpty() || systemBusy) { toast("没有待星标的照片");return; }
        if(prefs.getBoolean("skip_star_confirm",false)){saveLocalStars();return;}
        android.widget.CheckBox once=new android.widget.CheckBox(this);once.setText("仅提示一次");once.setPadding(dp(18),dp(6),dp(18),dp(8));
        new AlertDialog.Builder(this).setTitle("确认收藏 "+pendingStars.size()+" 张？")
                .setMessage("收录到收藏相册。").setView(once).setNegativeButton("取消",null)
                .setPositiveButton("确认收藏",(d,w)->{if(once.isChecked())prefs.edit().putBoolean("skip_star_confirm",true).commit();saveLocalStars();}).show();
    }
    private void saveLocalStars(){
        starred.addAll(pendingStars);for(Long id:pendingStars)history.removeIf(a->a.item.id==id&&a.type==STAR);
        persistStars();pendingStars.clear();savePending();showSettlement(false);toast("已收录到收藏相册");
    }

    private void confirmDropRound() {
        if (systemBusy) { toast("请先完成或取消系统确认"); return; }
        int d = deleteBucket.size();
        int st = pendingStars.size();
        if (d + st == 0) {
            toast("本轮没有待处理的操作");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("放弃本轮的标记")
                .setMessage("将清空 " + d + " 张待删、" + st + " 张待星标的记录。这些照片不会被改动，星标也不会生效。")
                .setPositiveButton("放弃", (x, w) -> {
                    deleteBucket.clear();
                    pendingStars.clear();
                    keptCount = 0;
                    history.clear();
                    savePending();
                    toast("已放弃本轮的标记");
                    resumeDeck();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout statBox(String k, String v, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(8), dp(6), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(surfaceColor(0xFF16161A));
        g.setCornerRadius(dp(14));
        box.setBackground(g);

        TextView tv = label(v, 20f, color, true);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);
        TextView tk = label(k, 11f, C_DIM, false);
        tk.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(3);
        box.addView(tk, p);
        return box;
    }

    private void restart() {
        history.clear();
        keptCount = 0;
        deleteBucket.clear();
        pendingStars.clear();
        savePending();
        if (summaryView != null) {
            deck.removeView(summaryView);
            summaryView = null;
        }
        cardFront.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        clearSeen();
        refillQueue();
        if (current == null) {
            showEmpty();
        } else {
            renderCurrent();
        }
    }

    // ===================== 工具 =====================

    private int dp(float v) {
        return (int) (v * dens + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * 35mm 胶片边框：上下两条齿孔带 + 左右片基，中间一格留白给照片当画格。
     * 纯 Canvas 绘制，不依赖任何图片资源。
     */
    /** 叉、星、勾三个符号统一用 Canvas 画，笔画粗细与包围盒完全一致，不会因为字体不同而大小不一 */
    private final class UndoButton extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        UndoButton(Context c){super(c);setClickable(true);setFocusable(true);}
        protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f;p.setColor(lightMode?0xDAFFFFFF:0xFF2D261C);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,dp(14),p);p.setColor(C_TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.8f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path a=new Path();a.moveTo(x-dp(3),y-dp(6));a.lineTo(x-dp(8),y-dp(1));a.lineTo(x-dp(3),y+dp(3));c.drawPath(a,p);Path q=new Path();q.moveTo(x-dp(7),y-dp(1));q.cubicTo(x+dp(7),y-dp(7),x+dp(10),y+dp(8),x+dp(1),y+dp(8));c.drawPath(q,p);p.setStyle(Paint.Style.FILL);}
    }
    private static final class GlyphView extends View {

        static final int CROSS = 0, STAR = 1, CHECK = 2;

        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float dens;
        private int type;
        private int color;

        GlyphView(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
            dens = c.getResources().getDisplayMetrics().density;
            circlePaint.setColor(((MainActivity)c).lightMode ? 0xCBFFFFFF : 0xFF1C1C20);
            if (((MainActivity)c).lightMode) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                circlePaint.setShadowLayer(3 * dens, 0, 2 * dens, 0x340E2638);
            }
            edgePaint.setStyle(Paint.Style.STROKE);
            glyphPaint.setStrokeCap(Paint.Cap.ROUND);
            glyphPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setGlyph(int type, int color) {
            this.type = type;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - (((MainActivity)getContext()).lightMode ? 4 * dens : 0);

            cv.drawCircle(cx, cy, r, circlePaint);
            edgePaint.setColor((color & 0x00FFFFFF) | 0x33000000);
            edgePaint.setStrokeWidth(Math.max(1f, dens));
            cv.drawCircle(cx, cy, r - edgePaint.getStrokeWidth() / 2f, edgePaint);
            if (((MainActivity)getContext()).lightMode) {
                Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
                highlight.setShader(new android.graphics.LinearGradient(0, cy-r, 0, cy+r, 0xCCFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, r-dens, highlight);
            }

            // 叉 / 勾 / 星统一到同一视觉尺寸：包围盒相同、笔画相同，外接尺寸都是 0.87 box
            float box = Math.min(w, h) * 0.58f;
            glyphPaint.setColor(color);
            glyphPaint.setStrokeWidth(box * 0.19f);

            float l = cx - box / 2f;
            float t = cy - box / 2f;

            if (type == CROSS) {
                glyphPaint.setStyle(Paint.Style.STROKE);
                cv.drawLine(l + box * 0.16f, t + box * 0.16f, l + box * 0.84f, t + box * 0.84f, glyphPaint);
                cv.drawLine(l + box * 0.84f, t + box * 0.16f, l + box * 0.16f, t + box * 0.84f, glyphPaint);
            } else if (type == CHECK) {
                glyphPaint.setStyle(Paint.Style.STROKE);
                path.reset();
                path.moveTo(l + box * 0.17f, t + box * 0.60f);
                path.lineTo(l + box * 0.40f, t + box * 0.81f);
                path.lineTo(l + box * 0.83f, t + box * 0.19f);
                cv.drawPath(path, glyphPaint);
            } else {
                glyphPaint.setStyle(Paint.Style.FILL);
                path.reset();
                float ro = box * 0.47f;
                float ri = ro * 0.44f;
                for (int i = 0; i < 10; i++) {
                    float ang = (float) (-Math.PI / 2 + i * Math.PI / 5);
                    float rr = (i % 2 == 0) ? ro : ri;
                    float x = cx + rr * (float) Math.cos(ang);
                    float y = cy + rr * (float) Math.sin(ang);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                cv.drawPath(path, glyphPaint);
            }
        }
    }

    private static final class FilmFrame extends View {

        private static final int BASE = 0xFF1B150D;       // 片基暖黑
        private static final int EDGE = 0xFF3A2E1C;       // 片基与画格的交界
        private static final int HOLE = 0xFF6A5B41;       // 齿孔透光
        private static final int HOLE_LINE = 0xFF0A0806;  // 齿孔内描边
        private static final int INK = 0xFF8C7857;        // 片边印刷字

        private final float dens;
        private final int band;
        private final int side;

        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint holeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private String frameNo = "";
        private int bottomCounterWidth;

        void setBottomCounterWidth(int width){
            if(bottomCounterWidth!=width){bottomCounterWidth=Math.max(0,width);invalidate();}
        }

        FilmFrame(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            band = Math.round(FILM_BAND * dens);
            side = Math.round(FILM_SIDE * dens);
            boolean light = ((MainActivity)c).lightMode;
            basePaint.setColor(light ? 0xFF59432B : BASE);
            edgePaint.setColor(light ? 0xFF9B7848 : EDGE);
            holePaint.setColor(light ? 0xFFD5C19B : HOLE);
            holeLine.setColor(light ? 0xFF372615 : HOLE_LINE);
            holeLine.setStyle(Paint.Style.STROKE);
            holeLine.setStrokeWidth(Math.max(1f, dens * 0.8f));
            inkPaint.setColor(light ? 0xFFEAD3A7 : 0xFFB59A6C);
            inkPaint.setTextSize(10f * dens);
            inkPaint.setTypeface(Typeface.MONOSPACE);
            inkPaint.setLetterSpacing(0.12f);
            grainPaint.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            grainPaint.setAlpha(0x1C);
        }

        void setFrame(int index, int total) {
            String s = total <= 0 ? "0/0" : String.format(Locale.US, "%d/%d", Math.max(0, index), total);
            if (!s.equals(frameNo)) {
                frameNo = s;
                invalidate();
            }
        }

        /** 64×64 的淡噪点，平铺出胶片颗粒 */
        private Bitmap grain() {
            int n = 64;
            Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            int[] px = new int[n * n];
            java.util.Random r = new java.util.Random(20261112L);
            for (int i = 0; i < px.length; i++) {
                int a = 4 + r.nextInt(18);
                px[i] = (a << 24) | 0x00E8DCC0;
            }
            b.setPixels(px, 0, n, 0, 0, n, n);
            return b;
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= band * 2) return;

            // 片基：上下齿孔带 + 左右窄边，连成一整条胶片
            cv.drawRect(0, 0, w, band, basePaint);
            cv.drawRect(0, h - band, w, h, basePaint);
            cv.drawRect(0, 0, side, h, basePaint);
            cv.drawRect(w - side, 0, w, h, basePaint);

            // 画格上下沿：一条暖色细线把照片"嵌"进胶片
            cv.drawRect(0, band, w, band + Math.max(1f, dens), edgePaint);
            cv.drawRect(0, h - band - Math.max(1f, dens), w, h - band, edgePaint);

            float hw = 21 * dens;
            float hh = 13 * dens;
            float gap = 13 * dens;
            float radius = 4f * dens;
            float yTop = (band - hh) / 2f;
            float yBottom = h - band + (band - hh) / 2f;

            // 下带不印任何信息，只留齿孔
            float xStart = side + 7 * dens;

            float numW = inkPaint.measureText(frameNo);
            float xEndTop = w - side - 16 * dens - numW;
            // Reserve the measured counter panel plus a gap; never mask half a sprocket.
            float xEndBottom = w - Math.max(side + 6 * dens, bottomCounterWidth + 6 * dens);
            cv.drawText(frameNo, w-side-8*dens-numW, band/2f-(inkPaint.ascent()+inkPaint.descent())/2, inkPaint);

            for (float x = xStart; x + hw <= xEndBottom; x += hw + gap) {
                rect.set(x, yBottom, x + hw, yBottom + hh);
                cv.drawRoundRect(rect, radius, radius, holePaint);
                cv.drawRoundRect(rect, radius, radius, holeLine);
            }
            for (float x = side + 7 * dens; x + hw <= xEndTop; x += hw + gap) {
                rect.set(x, yTop, x + hw, yTop + hh);
                cv.drawRoundRect(rect, radius, radius, holePaint);
                cv.drawRoundRect(rect, radius, radius, holeLine);
            }

            // 颗粒只压在片基上，照片画格保持干净
            cv.drawRect(0, 0, w, band, grainPaint);
            cv.drawRect(0, h - band, w, h, grainPaint);
        }
    }
}
