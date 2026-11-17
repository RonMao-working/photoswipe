package com.maodun.photoswipe;

import android.content.SharedPreferences;

/** Code-point limits avoid cutting surrogate pairs. Local notes do not alter original files. */
final class MemoryNotes {
    static final String HINT="记录你的独家记忆";
    static String first(String text,int limit) {
        if(text==null)return ""; int count=text.codePointCount(0,text.length());
        return count<=limit?text:text.substring(0,text.offsetByCodePoints(0,limit));
    }
    static String key(String uri){return "memory:"+uri;}
    static String get(SharedPreferences p,String uri){return p.getString(key(uri),"");}
    static void save(SharedPreferences p,String uri,String value){p.edit().putString(key(uri),first(value,40)).commit();}
    static String excerpt(SharedPreferences p,String uri){return first(get(p,uri).trim(),10);}
}
