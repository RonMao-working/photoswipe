package com.maodun.photoswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.ImageDecoder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.*;
import java.util.regex.*;

/** Local URI only. Motion video remains in private cache and is never indexed as a new photo. */
final class DynamicPreview {
    static final class Content { Drawable animated; Movie movie; File motion; boolean live; String evidence = ""; }
    static Content load(Context context, Uri uri, String name) {
        Content content = new Content();
        String lower = name.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".gif") || lower.endsWith(".webp")) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.getContentResolver(), uri),
                        (decoder, info, source) -> {
                            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                            float factor = Math.min(1f, 1440f / Math.max(w, h));
                            decoder.setTargetSize(Math.max(1, (int)(w*factor)), Math.max(1, (int)(h*factor)));
                        });
                    if (d instanceof AnimatedImageDrawable) content.animated = d;
                } else if (lower.endsWith(".gif")) {
                    try (InputStream input = context.getContentResolver().openInputStream(uri)) { content.movie = Movie.decodeStream(input); }
                }
            } catch (Exception ignored) { }
        }
        if (content.animated != null || content.movie != null) return content;
        boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        boolean imageContainer = jpeg || lower.endsWith(".heic") || lower.endsWith(".heif") || lower.endsWith(".avif");
        if (!imageContainer) return content;
        try {
            byte[] header = new byte[524288]; int n = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) return content;
                int k; while (n < header.length && (k = input.read(header, n, header.length-n)) > 0) n += k;
            }
            LivePhotoMetadata.Result info = LivePhotoMetadata.inspect(header, n);
            content.live = info.live; content.evidence = info.evidence;
            if (!content.live || !jpeg) return content;
            String xmp = new String(header, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            long length = 0;
            Matcher legacy = Pattern.compile("(?:MicroVideoOffset|MotionPhotoVideoOffset)\\s*=\\s*[\"']([0-9]+)[\"']").matcher(xmp);
            if (legacy.find()) length = Long.parseLong(legacy.group(1));
            if (length == 0) {
                Matcher directory = Pattern.compile("<[^>]*Item\\b[^>]*>").matcher(xmp);
                while (directory.find()) {
                    String item = directory.group();
                    if (!item.contains("video/mp4")) continue;
                    Matcher m = Pattern.compile("(?:Item:)?Length\\s*=\\s*[\"']([0-9]+)[\"']").matcher(item);
                    if (m.find()) length = Long.parseLong(m.group(1));
                }
            }
            File source = File.createTempFile("motion_source_", ".bin", context.getCacheDir());
            try {
                long total = 0;
                try (InputStream in = context.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(source)) {
                    byte[] buffer = new byte[65536]; int k;
                    while ((k = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        total += k; if (total > 134217728L) throw new IOException("Motion photo exceeds 128 MB safety limit");
                        out.write(buffer, 0, k);
                    }
                }
                long offset = length > 0 && length < total ? total-length : -1;
                try (RandomAccessFile in = new RandomAccessFile(source, "r")) {
                    if (offset < 0) {
                        long start = Math.max(0, total-33554432L);
                        byte[] tail = new byte[(int)(total-start)]; in.seek(start); in.readFully(tail);
                        for (int i=4; i<tail.length-16; i++) {
                            if (tail[i]=='f' && tail[i+1]=='t' && tail[i+2]=='y' && tail[i+3]=='p') {
                                int box = ((tail[i-4]&255)<<24)|((tail[i-3]&255)<<16)|((tail[i-2]&255)<<8)|(tail[i-1]&255);
                                if (box>=16 && box<=256) { offset=start+i-4; break; }
                            }
                        }
                    }
                    if (offset < 0 || total-offset < 16) return content;
                    in.seek(offset+4); if (in.readInt() != 0x66747970) return content;
                    File video = File.createTempFile("motion_video_", ".mp4", context.getCacheDir());
                    try (OutputStream out = new FileOutputStream(video)) {
                        in.seek(offset); byte[] buffer = new byte[65536]; int k;
                        while ((k = in.read(buffer)) != -1) out.write(buffer, 0, k);
                    }
                    content.motion = video;
                }
            } finally { source.delete(); }
        } catch (Exception ignored) { }
        return content;
    }
    private final FrameLayout card;
    private final ImageView photo;
    private final TextView badge, liveBadge;
    private TextureView texture;
    private MovieView movieView;
    private Content current;
    private MediaPlayer player;
    private Surface surface;
    DynamicPreview(FrameLayout card, ImageView photo, TextView badge) {
        this(card, photo, badge, null);
    }
    DynamicPreview(FrameLayout card, ImageView photo, TextView badge, TextView liveBadge) {
        this.card=card; this.photo=photo; this.badge=badge; this.liveBadge=liveBadge;
        badge.setOnClickListener(v -> play());
        if (liveBadge != null) liveBadge.setOnClickListener(v -> {
            if (current == null) return;
            String message = current.evidence + (current.motion == null
                    ? "；仅保留Live标记，未找到可播放动态片段。传输可能只保留静态照片。"
                    : "；含可播放片段，自动播放一次后可点击右上角重播。");
            new android.app.AlertDialog.Builder(card.getContext()).setTitle("Live识别依据")
                    .setMessage(message).setPositiveButton("知道了", null).show();
        });
    }
    void show(Content content) {
        stop(); current=content;
        if (content.live && liveBadge != null) {
            liveBadge.setText(content.motion != null ? "LIVE" : "LIVE · 仅静态");
            liveBadge.setContentDescription("Live照片，点击查看识别依据");
            liveBadge.setVisibility(View.VISIBLE);
        }
        if (content.animated != null) {
            badge.setText("动图 · 重播"); badge.setVisibility(View.VISIBLE); play();
        } else if (content.movie != null) {
            badge.setText("GIF · 重播"); badge.setVisibility(View.VISIBLE); play();
        } else if (content.motion != null) {
            badge.setText("LIVE · 重播"); badge.setVisibility(View.VISIBLE); play();
        } else badge.setVisibility(View.GONE);
    }
    private FrameLayout.LayoutParams photoBounds() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1,-1);
        p.bottomMargin = ((FrameLayout.LayoutParams)photo.getLayoutParams()).bottomMargin;
        return p;
    }
    void play() {
        if (current == null) return;
        if (current.animated != null) {
            photo.setImageDrawable(current.animated);
            if (Build.VERSION.SDK_INT >= 28) {
                AnimatedImageDrawable d=(AnimatedImageDrawable)current.animated;
                d.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE); d.start();
            }
            return;
        }
        if (current.movie != null) {
            if (movieView == null) { movieView=new MovieView(card.getContext(), current.movie); card.addView(movieView, 2, photoBounds()); }
            movieView.start=SystemClock.uptimeMillis(); movieView.running=true; movieView.invalidate();
            return;
        }
        if (current.motion == null) return;
        releasePlayer();
        if (texture == null) {
            texture = new TextureView(card.getContext());
            card.addView(texture, 2, photoBounds());
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture s,int w,int h) { startVideo(s); }
                public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture s,int w,int h) { fitVideo(); }
                public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture s) { releasePlayer(); return true; }
                public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture s) { }
            });
        }
        texture.setVisibility(View.VISIBLE);
        if (texture.isAvailable()) startVideo(texture.getSurfaceTexture());
    }
    private boolean playbackFailed() {
        if (texture != null) texture.setVisibility(View.GONE);
        releasePlayer();
        if (liveBadge != null) { liveBadge.setText("LIVE · 仅静态"); liveBadge.setVisibility(View.VISIBLE); }
        badge.setText("LIVE · 重试");
        return true;
    }
    private void startVideo(android.graphics.SurfaceTexture s) {
        if (current == null || current.motion == null || !current.motion.exists()) return;
        try {
            releasePlayer(); player = new MediaPlayer(); surface=new Surface(s); player.setSurface(surface);
            player.setDataSource(current.motion.getAbsolutePath()); player.setLooping(false); player.setVolume(0f,0f);
            player.setOnPreparedListener(p -> { android.util.Log.i("PhotoSwipeMotion", "prepared " + p.getDuration() + "ms"); fitVideo(); p.start(); });
            player.setOnCompletionListener(p -> { android.util.Log.i("PhotoSwipeMotion", "completed once"); if (texture != null) texture.setVisibility(View.GONE); releasePlayer(); });
            player.setOnErrorListener((p,a,b) -> playbackFailed());
            player.prepareAsync();
        } catch(Exception e) { playbackFailed(); }
    }
    private void fitVideo() {
        if (player == null || texture == null) return;
        int vw=player.getVideoWidth(), vh=player.getVideoHeight(), w=texture.getWidth(), h=texture.getHeight();
        if(vw<=0||vh<=0||w<=0||h<=0)return;
        float scale=Math.min((float)w/vw,(float)h/vh);
        Matrix matrix=new Matrix(); matrix.setScale(vw*scale/w,vh*scale/h,w/2f,h/2f); texture.setTransform(matrix);
    }
    private void releasePlayer() {
        if(player!=null){player.release();player=null;} if(surface!=null){surface.release();surface=null;}
    }
    void stop() {
        releasePlayer();
        if (Build.VERSION.SDK_INT>=28 && current!=null && current.animated instanceof AnimatedImageDrawable) ((AnimatedImageDrawable)current.animated).stop();
        if(texture!=null){card.removeView(texture);texture=null;}
        if(movieView!=null){movieView.running=false;card.removeView(movieView);movieView=null;}
        if(current!=null && current.motion!=null)current.motion.delete();
        current=null; badge.setVisibility(View.GONE); if (liveBadge != null) liveBadge.setVisibility(View.GONE);
    }
    private static final class MovieView extends View {
        final Movie movie; long start=SystemClock.uptimeMillis(); boolean running=true;
        MovieView(Context c,Movie m){super(c);movie=m;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas canvas){
            if(!running)return;int duration=movie.duration();if(duration<=0)duration=1000;
            movie.setTime((int)((SystemClock.uptimeMillis()-start)%duration));
            float scale=Math.min((float)getWidth()/movie.width(),(float)getHeight()/movie.height());
            canvas.save();canvas.translate((getWidth()-movie.width()*scale)/2,(getHeight()-movie.height()*scale)/2);canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restore();postInvalidateOnAnimation();
        }
    }
}
