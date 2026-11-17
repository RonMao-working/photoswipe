package com.maodun.photoswipe;

/** Shared plain-text composition and vertical sizing for memory page and export. */
final class MemoryLayout {
    static String locationLines(String value){
        if(value==null)return "";
        String text=value.trim().replace("\r\n","\n").replace('\r','\n');
        // Geocoder fallback may put its status and coordinates on a single line.
        // Keep coordinates on their own line, just like a successful place lookup.
        int gps=text.indexOf("纬度 ");
        if(gps>0){
            String prefix=text.substring(0,gps).trim();
            while(prefix.endsWith("·")||prefix.endsWith("，")||prefix.endsWith(","))prefix=prefix.substring(0,prefix.length()-1).trim();
            text=prefix+"\n"+text.substring(gps).trim();
        }
        StringBuilder result=new StringBuilder();
        for(String row:text.split("\n")){row=row.trim();if(row.isEmpty())continue;if(result.length()>0)result.append('\n');result.append(row);}
        return result.toString();
    }
    static String metadata(String time,String location){
        String when=time==null?"":time.trim(),where=locationLines(location);
        return when.isEmpty()?where:(where.isEmpty()?when:when+"\n"+where);
    }
    static boolean hasStory(String value){return value!=null&&!value.trim().isEmpty();}
    static int exportHeight(int imageHeight,int metadataHeight,int storyHeight){
        // No reserved story box. The final content always has exactly 36px bottom padding.
        return 66+imageHeight+30+metadataHeight+(storyHeight>0?20+2+20+storyHeight:0)+36;
    }
}
