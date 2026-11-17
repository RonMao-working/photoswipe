package com.maodun.photoswipe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative metadata-only detection. Camera make, suffix and visual appearance are not evidence. */
final class LivePhotoMetadata {
    static final class Result {
        final boolean live;
        final String evidence;
        Result(boolean live, String evidence) { this.live = live; this.evidence = evidence; }
    }
    static Result inspect(byte[] bytes, int length) {
        String text = new String(bytes, 0, length, java.nio.charset.StandardCharsets.ISO_8859_1);
        // XMP attributes and elements. An explicit false/zero flag must not be detected as live.
        for (String field : new String[]{"MotionPhoto", "MicroVideo", "LivePhoto", "MovingPhoto"}) {
            String attr = "(?:[A-Za-z_][A-Za-z0-9_.-]*:)?" + field;
            if (Pattern.compile("(?:^|[\\s<])" + attr + "\\s*=\\s*[\"'](?:1|true)[\"']", Pattern.CASE_INSENSITIVE).matcher(text).find()
                    || Pattern.compile("<" + attr + "(?:\\s[^>]*)?>\\s*(?:1|true)\\s*</" + attr + "\\s*>", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return new Result(true, "元数据 " + field + "=1/true");
            }
        }
        // Legacy offset without a flag: positive offset is concrete video metadata.
        Matcher offset = Pattern.compile("(?:[A-Za-z_][A-Za-z0-9_.-]*:)?(?:MicroVideoOffset|MotionPhotoVideoOffset)\\s*=\\s*[\"']([0-9]+)[\"']").matcher(text);
        while (offset.find()) {
            try { if (Long.parseLong(offset.group(1)) > 0) return new Result(true, "元数据含正值动态视频偏移"); }
            catch (NumberFormatException ignored) { }
        }
        // Standard container directory explicitly identifies a MotionPhoto video item.
        Matcher item = Pattern.compile("<[^>]*Item\\b[^>]*>").matcher(text);
        while (item.find()) {
            String tag = item.group();
            if (Pattern.compile("(?:Item:)?Mime\\s*=\\s*[\"']video/mp4[\"']").matcher(tag).find()
                    && Pattern.compile("(?:Item:)?Semantic\\s*=\\s*[\"']MotionPhoto[\"']").matcher(tag).find()) {
                return new Result(true, "XMP MotionPhoto视频条目");
            }
        }
        return new Result(false, "未找到可确认的Live元数据");
    }
}
