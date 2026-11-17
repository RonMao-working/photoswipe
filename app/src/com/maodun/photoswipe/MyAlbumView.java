package com.maodun.photoswipe;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native local profile and paged album. Only visible-page images are decoded. */
final class MyAlbumView extends LinearLayout {
    interface Preview { void open(MainActivity.PhotoItem item); }
    private final SharedPreferences prefs;
    private final boolean light;
    private final int text, dim;
    private final float density;
    private final List<List<MainActivity.PhotoItem>> pages = new ArrayList<>();
    private final AlbumDateWindow window = new AlbumDateWindow(System.currentTimeMillis());
    private final ThreadPoolExecutor loader = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
    private final LruCache<Long, Bitmap> images = new LruCache<Long, Bitmap>(24*1024*1024) {
        protected int sizeOf(Long k, Bitmap b) { return b.getByteCount(); }
    };
    private final Set<Long> loading = new HashSet<>();
    private final Set<Long> failed = new HashSet<>();
    private Integer waitingPage;
    private final TextView name, bio, pageInfo, selectedDate, range, jumpInfo;
    private android.animation.ValueAnimator curl;
    private Bitmap turnFrom, turnNext;
    private float turnProgress;
    private int turnDirection;
    private final SeekBar axis;
    private View tickView;
    private final Paper paper;
    private final Preview preview;
    private int page;
    private long requestedDate = System.currentTimeMillis();
    private boolean disposed, syncing;
    private Integer queuedPage;
    private boolean axisDragging, fastTurning;
    private Integer scrubTarget;
    private int animationSteps, turnDestination;
    private boolean scrubReleased;

    MyAlbumView(Context context, SharedPreferences prefs, boolean light,
                List<MainActivity.PhotoItem> photos, Runnable back, Runnable settings, Preview preview) {
        super(context); this.prefs=prefs; this.light=light; this.preview=preview;
        density=getResources().getDisplayMetrics().density;
        text=light?0xFF302C25:0xFFF1E8D8; dim=light?0xFF756C5C:0xFFADA08D;
        setOrientation(VERTICAL); setPadding(dp(10), dp(2), dp(10), dp(6));
        setBackgroundColor(light?0xFFF0ECE4:0xFF16130F);
        LinearLayout nav=row(); addView(nav,new LayoutParams(-1,dp(40)));
        TextView title=label("收藏相册",15,true);title.setGravity(Gravity.CENTER_VERTICAL);nav.addView(title,new LayoutParams(-2,-1));
        TextView config=button("设置");config.setGravity(Gravity.CENTER);config.setPadding(0,0,0,0);nav.addView(config,new LayoutParams(dp(52),dp(36)));config.setOnClickListener(v->settings.run());
        post(()->{Rect hit=new Rect();config.getHitRect(hit);hit.offset(nav.getLeft(),nav.getTop());hit.top=Math.max(0,hit.centerY()-dp(24));hit.bottom=hit.top+dp(48);setTouchDelegate(new TouchDelegate(hit,config));});
        name=label("",20,true); bio=label("",12,false);
        AlbumInfoButton info=new AlbumInfoButton(context);nav.addView(info,1,new LayoutParams(dp(32),dp(40)));nav.addView(new View(context),2,new LayoutParams(0,1,1));
        info.setContentDescription("收藏相册说明");info.setTooltipText("收藏相册说明");
        info.setOnClickListener(v->new AlertDialog.Builder(getContext(),light?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert).setTitle("收藏相册").setMessage("上滑星标的照片，确认后收录到收藏相册").setPositiveButton("知道了",null).show());
        ArrayList<MainActivity.PhotoItem> sorted=new ArrayList<>(photos);
        Collections.sort(sorted,(a,b)->{int r=Long.compare(a.dateTaken,b.dateTaken);return r!=0?r:Long.compare(a.id,b.id);});
        for(int i=0;i<sorted.size();) {
            MainActivity.PhotoItem item=sorted.get(i++); ArrayList<MainActivity.PhotoItem> p=new ArrayList<>();p.add(item);
            if(i<sorted.size())p.add(sorted.get(i++));
            pages.add(p);
        }
        paper=new Paper(context); addView(paper,new LayoutParams(-1,0,1));
        LinearLayout turn=row();addView(turn,new LayoutParams(-1,dp(38)));
        TextView prev=button("上一页"),next=button("下一页");turn.addView(prev);
        pageInfo=label("",12,false);pageInfo.setGravity(Gravity.CENTER);turn.addView(pageInfo,new LayoutParams(0,-2,1));turn.addView(next);
        prev.setContentDescription("上一页，时间更早");next.setContentDescription("下一页，时间更近");
        prev.setOnClickListener(v->move(AlbumNavigation.PREVIOUS));next.setOnClickListener(v->move(AlbumNavigation.NEXT));
        LinearLayout date=row(); addView(date,new LayoutParams(-1,dp(34)));
        selectedDate=label("",13,true);date.addView(selectedDate,new LayoutParams(0,-2,1));
        TextView calendar=button("日历跳转");date.addView(calendar);calendar.setOnClickListener(v->calendar());
        axis=new SeekBar(context);
        FrameLayout timeline=new FrameLayout(context);addView(timeline,new LayoutParams(-1,dp(57)));
        tickView = new View(context){
            final Paint tick=new Paint(Paint.ANTI_ALIAS_FLAG);
            protected void onDraw(Canvas c){
                tick.setColor(dim);tick.setTextSize(dp(9));tick.setStrokeWidth(dp(.6f));
                float left=dp(16),width=getWidth()-dp(32);
                Calendar start=AlbumDateWindow.day(window.start());
                for(int i=0;i<36;i++){Calendar m=(Calendar)start.clone();m.add(Calendar.MONTH,i);float x=left+width*window.indexOf(m.getTimeInMillis())/Math.max(1,window.days()-1);c.drawLine(x,dp(29),x,dp(33),tick);
                    if(i%6==0){String s=new SimpleDateFormat("yy.MM",Locale.CHINA).format(m.getTime());c.drawText(s,x,dp(48),tick);}}
            }
        };
        timeline.addView(tickView,new FrameLayout.LayoutParams(-1,-1));
        timeline.addView(axis,new FrameLayout.LayoutParams(-1,dp(34)));
        axis.setContentDescription("36个月按日时间轴，拖动跳转收藏相册");
        range=label("",10,false);range.setTextColor(dim);range.setGravity(Gravity.CENTER);range.setMaxLines(2);addView(range,new LayoutParams(-1,-2));
        jumpInfo=label("",10,false);jumpInfo.setTextColor(dim);jumpInfo.setVisibility(GONE);addView(jumpInfo);
        axis.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int progress,boolean user){if(user&&!syncing)jump(window.dateAt(progress),true);}
            public void onStartTrackingTouch(SeekBar s){axisDragging=true;scrubReleased=false;}
            public void onStopTrackingTouch(SeekBar s){axisDragging=false;scrubReleased=true;finishScrub();}
        });
        page=Math.max(0,pages.size()-1);
        if(!pages.isEmpty())requestedDate=pages.get(page).get(0).dateTaken;
        updateDateAxis(false);updatePage();
    }
    private boolean sameDay(long a,long b){return a>0&&b>0&&AlbumDateWindow.day(a).getTimeInMillis()==AlbumDateWindow.day(b).getTimeInMillis();}
    private int dp(float x){return Math.round(x*density);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private TextView label(String s,float size,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(text);t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView button(String s){TextView b=label(s,12,true);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setBackground(bg(light?0xACFFFFFF:0xFF2D261C,16));return b;}
    private final class AlbumInfoButton extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        AlbumInfoButton(Context c){super(c);setClickable(true);setFocusable(true);}
        protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f;p.setColor(dim);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.8f));c.drawCircle(x,y,dp(6),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y-dp(3),dp(.8f),p);c.drawRoundRect(x-dp(.65f),y-dp(.7f),x+dp(.65f),y+dp(3.5f),dp(.5f),dp(.5f),p);}
    }
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private String date(long t){return t<=0?"拍摄日期未知":new SimpleDateFormat("yyyy年M月d日",Locale.CHINA).format(new Date(t));}
    private String month(long t){return new SimpleDateFormat("yyyy.MM",Locale.CHINA).format(new Date(t));}
    private void refreshProfile(){name.setText(prefs.getString("profile_name","我的相册"));bio.setText(prefs.getString("profile_bio","把值得留下的瞬间，收进相册。"));}
    private void editProfile(){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);box.setPadding(dp(20),dp(8),dp(20),0);
        EditText n=new EditText(getContext()),b=new EditText(getContext());n.setSingleLine(true);n.setHint("昵称");b.setHint("个人简介");b.setMaxLines(3);
        n.setText(prefs.getString("profile_name","我的相册"));b.setText(prefs.getString("profile_bio","把值得留下的瞬间，收进相册。"));
        n.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        b.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});box.addView(n);box.addView(b);
        new AlertDialog.Builder(getContext()).setTitle("本地资料 · 不联网").setView(box).setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,w)->{String nick=n.getText().toString().trim();prefs.edit().putString("profile_name",nick.isEmpty()?"我的相册":nick).putString("profile_bio",b.getText().toString().trim()).commit();refreshProfile();}).show();
    }
    private void calendar(){
        Set<Long> days=new HashSet<>();
        for(List<MainActivity.PhotoItem> p:pages)for(MainActivity.PhotoItem item:p)if(item.dateTaken>0)days.add(AlbumDateWindow.day(item.dateTaken).getTimeInMillis());
        new FavoriteCalendar(getContext(),requestedDate>0?requestedDate:System.currentTimeMillis(),days,
                date->{window.include(date);jump(date);updateDateAxis(false);}).show();
    }
    private void jump(long time){jump(time,false);}
    private void jump(long time,boolean scrub){
        requestedDate=time; int best=-1;long distance=Long.MAX_VALUE;
        for(int i=0;i<pages.size();i++)for(MainActivity.PhotoItem item:pages.get(i)){long t=item.dateTaken;if(t<=0)continue;long diff=Math.abs(AlbumDateWindow.day(t).getTimeInMillis()-time);if(diff<distance){distance=diff;best=i;}}
        selectedDate.setText(date(time));
        if(best>=0){long actual=pages.get(best).get(0).dateTaken;for(MainActivity.PhotoItem item:pages.get(best))if(item.dateTaken>0 && Math.abs(AlbumDateWindow.day(item.dateTaken).getTimeInMillis()-time)<Math.abs(AlbumDateWindow.day(actual).getTimeInMillis()-time))actual=item.dateTaken;setJumpMessage(sameDay(actual,time)?"":"当日无收藏 · 跳到最近的 "+date(actual));if(scrub){queuedPage=null;scrubReleased=false;if(scrubTarget==null||scrubTarget!=best){cancelTurn();waitingPage=null;discardQueuedLoads();}scrubTarget=best;continueScrub();}else{cancelTurn();waitingPage=null;discardQueuedLoads();scrubTarget=null;fastTurning=false;turnTo(best,true);}}
        else setJumpMessage("暂无带拍摄日期的收藏");
    }
    private void setJumpMessage(String message){jumpInfo.setText(message);selectedDate.setContentDescription(date(requestedDate)+(message.isEmpty()?"":"，"+message));}
    private void cancelTurn(){
        if(curl!=null){curl.removeAllListeners();curl.removeAllUpdateListeners();curl.cancel();curl=null;}
        if(paper.turning&&turnProgress>=.5f)page=turnDestination;
        paper.turning=false;clearTurn();
    }
    private void discardQueuedLoads(){
        for(Runnable r:loader.getQueue().toArray(new Runnable[0]))if(r instanceof AlbumLoad&&loader.remove(r))loading.remove(((AlbumLoad)r).item.id);
    }
    private void finishScrub(){
        if(disposed||scrubTarget==null)return;
        if(paper.turning&&turnDestination==scrubTarget){curl.end();scrubTarget=null;fastTurning=false;return;}
        cancelTurn();waitingPage=null;discardQueuedLoads();continueScrub();
    }
    private void continueScrub(){
        if(disposed||scrubTarget==null||paper.turning||waitingPage!=null)return;
        int dest=Math.max(0,Math.min(pages.size()-1,scrubTarget));
        if(dest==page){scrubTarget=null;fastTurning=false;updatePage();return;}
        fastTurning=true;turnTo(dest,!scrubReleased);
        if(scrubReleased&&waitingPage==null){scrubTarget=null;fastTurning=false;}
    }
    private void updateDateAxis(boolean include){
        if(include && requestedDate>0)window.include(requestedDate);
        syncing=true;axis.setMax(window.days()-1);axis.setProgress(window.indexOf(requestedDate));syncing=false;
        selectedDate.setText(date(requestedDate));range.setText(month(window.start())+" — "+month(window.end())+"  ·  36个月窗口，可用日历移至更早日期");
        if (tickView != null) tickView.invalidate();
    }
    private void updatePage(){
        pageInfo.setText(pages.isEmpty()?"暂无收藏":"第 "+(page+1)+" / "+pages.size()+" 页");
        paper.setContentDescription(pages.isEmpty()?"确认星标后，照片会进入这里":"收藏相册第"+(page+1)+"页，"+date(pages.get(page).get(0).dateTaken));
        if(!axisDragging&&!fastTurning)for(int near=Math.max(0,page-1);near<=Math.min(pages.size()-1,page+1);near++)for(MainActivity.PhotoItem item:pages.get(near))load(item);
        paper.invalidate();
    }
    private void turnTo(int target,boolean animate){
        if(disposed||pages.isEmpty())return;target=Math.max(0,Math.min(pages.size()-1,target));
        if(paper.turning){queuedPage=target;return;}if(target==page){waitingPage=null;return;}
        final int dest=target;
        if(!animate||paper.getWidth()==0){
            boolean ready=true;for(MainActivity.PhotoItem i:pages.get(dest)){if(images.get(i.id)==null&&!failed.contains(i.id))ready=false;load(i);}
            if(!ready){waitingPage=dest;setJumpMessage("照片读取中…");return;}
            waitingPage=null;page=dest;updatePage();return;
        }
        boolean ready=true;
        for(int source:new int[]{page,dest})for(MainActivity.PhotoItem i:pages.get(source)){if(images.get(i.id)==null&&!failed.contains(i.id))ready=false;load(i);}
        if(!ready){waitingPage=dest;setJumpMessage("照片读取中…");return;}
        waitingPage=null;
        turnFrom=paper.snapshot(page);turnNext=paper.snapshot(dest);turnDirection=target>page?-1:1;turnProgress=0;
        turnDestination=dest;paper.turning=true;animationSteps++;curl=android.animation.ValueAnimator.ofFloat(0,1);curl.setDuration(fastTurning?70:520);
        curl.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        curl.addUpdateListener(a->{turnProgress=(float)a.getAnimatedValue();paper.invalidate();});
        curl.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){
            if(disposed)return;page=dest;paper.turning=false;clearTurn();updatePage();
            if(scrubTarget!=null){post(MyAlbumView.this::continueScrub);}
            else if(queuedPage!=null){int q=queuedPage;queuedPage=null;fastTurning=false;turnTo(q,true);}
        }});curl.start();
    }
    private void clearTurn(){if(turnFrom!=null){turnFrom.recycle();turnFrom=null;}if(turnNext!=null){turnNext.recycle();turnNext=null;}}
    void refresh(){updatePage();}
    void remove(long id){
        cancelTurn();waitingPage=null;scrubTarget=null;queuedPage=null;
        ArrayList<MainActivity.PhotoItem> remaining=new ArrayList<>();for(List<MainActivity.PhotoItem> p:pages)for(MainActivity.PhotoItem i:p)if(i.id!=id)remaining.add(i);
        pages.clear();for(int i=0;i<remaining.size();){ArrayList<MainActivity.PhotoItem> pair=new ArrayList<>();pair.add(remaining.get(i++));if(i<remaining.size())pair.add(remaining.get(i++));pages.add(pair);}
        page=Math.max(0,Math.min(page,pages.size()-1));updatePage();
    }
    private void move(int delta){
        if(pages.isEmpty())return;int dest=AlbumNavigation.target(page,pages.size(),delta);
        requestedDate=pages.get(dest).get(0).dateTaken;updateDateAxis(true);setJumpMessage("");scrubTarget=null;fastTurning=false;turnTo(dest,true);
    }
    private void load(MainActivity.PhotoItem item){
        if(images.get(item.id)!=null||loading.contains(item.id)||failed.contains(item.id)||disposed)return;loading.add(item.id);
        loader.execute(new AlbumLoad(item));
    }
    private final class AlbumLoad implements Runnable {
        final MainActivity.PhotoItem item;
        AlbumLoad(MainActivity.PhotoItem i){item=i;}
        public void run(){Bitmap b=null;
            try{
                if(getContext() instanceof MainActivity)b=((MainActivity)getContext()).decodeAlbumPhoto(item);
                else if(Build.VERSION.SDK_INT>=28)b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContext().getContentResolver(),item.uri),(d,i,s)->{int w=i.getSize().getWidth(),h=i.getSize().getHeight();float f=Math.min(1,1100f/Math.max(w,h));d.setTargetSize(Math.max(1,(int)(w*f)),Math.max(1,(int)(h*f)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
            }catch(Throwable ignored){}
            final Bitmap result=b;post(()->{loading.remove(item.id);if(disposed){if(result!=null)result.recycle();return;}if(result!=null)images.put(item.id,result);else failed.add(item.id);paper.invalidate();if(waitingPage!=null){if(scrubTarget!=null){waitingPage=null;continueScrub();}else{int dest=waitingPage;turnTo(dest,true);}}});
        }
    }
    void dispose(){disposed=true;cancelTurn();loader.shutdownNow();images.evictAll();}
    static String inlineCaption(String date,String excerpt){return date+(excerpt.isEmpty()?"":"  ·  "+excerpt.replace('\n',' ').replace('\r',' '));}
    private final class Paper extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final List<RectF> hits=new ArrayList<>();
        boolean turning;float downX,downY;
        Paper(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){
            if(turning&&turnFrom!=null&&turnNext!=null){drawCurl(c);return;}
            drawPage(c,page,true);
        }
        Bitmap snapshot(int index){Bitmap b=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);drawPage(new Canvas(b),index,false);return b;}
        private void drawCurl(Canvas c){
            float w=getWidth(),h=getHeight(),t=turnProgress;
            p.setAlpha((int)(255*(0.35f+0.65f*t)));c.drawBitmap(turnNext,0,0,p);p.setAlpha(255);
            int cols=40,rows=12;float[] vertices=new float[(cols+1)*(rows+1)*2];int n=0;
            float cutoff=w*(1-t),radius=Math.max(w*.06f,w*.18f*(float)Math.sin(Math.PI*t));
            for(int y=0;y<=rows;y++)for(int x=0;x<=cols;x++){
                float u=w*x/cols,v=h*y/rows,local=turnDirection<0?u:w-u;
                float px=local,z=0;
                if(local>cutoff){float ang=Math.min((float)Math.PI/2,(local-cutoff)/Math.max(1,w-cutoff)*(float)Math.PI/2);px=cutoff+radius*(float)Math.sin(ang);z=radius*(1-(float)Math.cos(ang));}
                float shrink=1-z/(w*4);vertices[n++]=turnDirection<0?px:w-px;vertices[n++]=h/2+(v-h/2)*shrink-z*.06f;
            }
            p.setAlpha((int)(255*Math.min(1,(1-t)*3)));c.drawBitmapMesh(turnFrom,cols,rows,vertices,0,null,0,p);p.setAlpha(255);
            float fold=turnDirection<0?cutoff:w-cutoff,sh=radius*.45f;
            p.setShader(new LinearGradient(fold-sh,0,fold+sh,0,new int[]{0x001A1108,0x500F0903,0xB0F3E7D0,0x00F3E7D0},new float[]{0,.48f,.6f,1},Shader.TileMode.CLAMP));
            c.drawRect(Math.max(0,fold-sh),dp(10),Math.min(w,fold+sh),h-dp(10),p);p.setShader(null);
        }
        private void drawPage(Canvas c,int index,boolean recordHits){
            float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            RectF book=new RectF(dp(7),dp(9),w-dp(6),h-dp(8));
            p.setColor(0xFF77624A);p.setShadowLayer(dp(5),0,dp(3),0x40000000);c.drawRoundRect(book,dp(5),dp(5),p);p.clearShadowLayer();
            for(int i=3;i>=0;i--){p.setColor(i%2==0?0xFFE5D8BC:0xFFCABB9C);c.drawRoundRect(new RectF(book.left+dp(7),book.top+dp(4),book.right-dp(i*2),book.bottom-dp(i*2)),dp(3),dp(3),p);}
            RectF paperRect=new RectF(book.left+dp(9),book.top+dp(4),book.right-dp(7),book.bottom-dp(7));
            p.setShader(new LinearGradient(paperRect.left,0,paperRect.right,0,new int[]{0xFFB4A181,0xFFF5EDDA,0xFFF0E4CB},new float[]{0,0.08f,1},Shader.TileMode.CLAMP));c.drawRect(paperRect,p);p.setShader(null);
            p.setColor(0x208A7656);p.setStrokeWidth(1);for(float y=paperRect.top;y<paperRect.bottom;y+=dp(5))c.drawLine(paperRect.left,y,paperRect.right,y,p);
            if(recordHits)hits.clear();
            if(pages.isEmpty()){
                p.setColor(0xFF806E53);p.setTextSize(dp(17));p.setTypeface(Typeface.DEFAULT_BOLD);String s="收藏每一个值得留下的瞬间";c.drawText(s,(w-p.measureText(s))/2,h/2,p);
                p.setTextSize(dp(12));p.setTypeface(Typeface.DEFAULT);s="结算页确认星标后，照片会出现在这里";c.drawText(s,(w-p.measureText(s))/2,h/2+dp(27),p);return;
            }
            List<MainActivity.PhotoItem> items=pages.get(index);float left=paperRect.left+dp(7),right=paperRect.right-dp(6),top=paperRect.top+dp(9),bottom=paperRect.bottom-dp(10);
            for(int i=0;i<items.size();i++){
                float gap=dp(9),slot=(bottom-top-gap)/2;RectF frame=new RectF(left,top+i*(slot+gap),right,top+i*(slot+gap)+slot);
                p.setColor(0xFFFCFAF2);p.setShadowLayer(dp(2),0,dp(2),0x48000000);c.drawRect(frame,p);p.clearShadowLayer();
                RectF inside=new RectF(frame.left+dp(4),frame.top+dp(4),frame.right-dp(4),frame.bottom-dp(22));if(recordHits)hits.add(frame);
                MainActivity.PhotoItem item=items.get(i);Bitmap b=images.get(item.id);
                if(b!=null){float f=Math.min(inside.width()/b.getWidth(),inside.height()/b.getHeight());float bw=b.getWidth()*f,bh=b.getHeight()*f;RectF dst=new RectF(inside.centerX()-bw/2,inside.centerY()-bh/2,inside.centerX()+bw/2,inside.centerY()+bh/2);c.drawBitmap(b,null,dst,p);}
                else {p.setColor(0xFFE4DFD3);c.drawRect(inside,p);p.setColor(0xFF83745B);p.setTextSize(dp(11));String s=failed.contains(item.id)?"照片暂时无法读取，点击重试":"照片读取中…";c.drawText(s,inside.left+dp(6),inside.centerY(),p);}
                p.setColor(0xFFA89675);Path corner=new Path();corner.moveTo(frame.left,frame.top);corner.lineTo(frame.left+dp(13),frame.top);corner.lineTo(frame.left,frame.top+dp(13));corner.close();c.drawPath(corner,p);
                p.setColor(0xFF807059);p.setTextSize(dp(10));p.setTypeface(Typeface.DEFAULT);
                String caption=item.dateTaken>0?new SimpleDateFormat("yyyy.MM.dd",Locale.CHINA).format(new Date(item.dateTaken)):"日期未知";
                String line=inlineCaption(caption,MemoryNotes.excerpt(prefs,item.identity()));
                android.text.TextPaint captionPaint=new android.text.TextPaint(p);
                CharSequence fitted=android.text.TextUtils.ellipsize(line,captionPaint,frame.width()-dp(12),android.text.TextUtils.TruncateAt.END);
                c.save();c.clipRect(frame.left+dp(6),frame.bottom-dp(21),frame.right-dp(6),frame.bottom);c.drawText(fitted.toString(),frame.left+dp(6),frame.bottom-dp(7),captionPaint);c.restore();
            }
        }
        public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP&&!turning){float dx=e.getX()-downX,dy=e.getY()-downY;
                if(Math.abs(dx)>dp(45)&&Math.abs(dx)>Math.abs(dy)){move(AlbumNavigation.deltaForSwipe(dx));return true;}
                if(Math.abs(dx)<dp(10)&&Math.abs(dy)<dp(10)&&!pages.isEmpty())for(int i=0;i<hits.size();i++)if(hits.get(i).contains(e.getX(),e.getY())){MainActivity.PhotoItem item=pages.get(page).get(i);if(failed.remove(item.id)){load(item);invalidate();return true;}performClick();preview.open(item);return true;}
                return true;
            }return true;
        }
        public boolean performClick(){super.performClick();return true;}
    }
}
