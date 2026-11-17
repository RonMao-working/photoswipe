package com.maodun.photoswipe;
import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;
/** Host JVM test of local manual-place storage; no Android stub methods invoked. */
public final class MemoryPlaceTest {
 static int checks;static Map<String,String> values=new HashMap<>();
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
 static SharedPreferences prefs(){
  final Object[] editor=new Object[1]; final Map<String,String> pending=new HashMap<>(); final Set<String> removed=new HashSet<>();
  editor[0]=Proxy.newProxyInstance(MemoryPlaceTest.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(o,m,a)->{
   switch(m.getName()){
    case "putString":pending.put((String)a[0],(String)a[1]);return editor[0];
    case "remove":removed.add((String)a[0]);return editor[0];
    case "commit":for(String k:removed)values.remove(k);values.putAll(pending);removed.clear();pending.clear();return true;
    default:throw new AssertionError(m.getName());
   }
  });
  return (SharedPreferences)Proxy.newProxyInstance(MemoryPlaceTest.class.getClassLoader(),new Class[]{SharedPreferences.class},(o,m,a)->{
   switch(m.getName()){
    case "getString":return values.containsKey(a[0])?values.get(a[0]):a[1];
    case "edit":return editor[0];
    default:throw new AssertionError(m.getName());
   }
  });
 }
 public static void main(String[] args){
  SharedPreferences p=prefs();String a="content://qa/a",b="content://qa/b";
  check(MemoryNotes.getPlace(p,a).isEmpty(),"unwritten photo has empty manual address");
  check(MemoryNotes.savePlace(p,a,"上海市静安区 · 苏州河畔"),"manual address commit succeeds");
  check(MemoryNotes.getPlace(p,a).equals("上海市静安区 · 苏州河畔"),"Chinese address reads back unchanged");
  check(MemoryNotes.getPlace(prefs(),a).equals("上海市静安区 · 苏州河畔"),"new preference accessor restores address");
  check(MemoryNotes.getPlace(p,b).isEmpty(),"different photo has isolated address");
  MemoryNotes.save(p,a,"我的独家记忆");check(MemoryNotes.get(p,a).equals("我的独家记忆"),"story and address stored separately");
  values.put("place:"+a,"旧GPS缓存");check(MemoryNotes.getPlace(p,a).equals("上海市静安区 · 苏州河畔"),"geocoder cache does not overwrite manual address");
  check(MemoryNotes.cleanPlace("  北京\n海淀\t公园  ").equals("北京 海淀 公园"),"trim and flatten manual place");
  check(MemoryNotes.cleanPlace(null).isEmpty(),"null normalizes to empty");
  check(MemoryNotes.savePlace(p,a," \n\t "),"clear commits successfully");check(MemoryNotes.getPlace(p,a).isEmpty(),"whitespace-only place removed");
  check(!values.containsKey(MemoryNotes.placeKey(a)),"clearing removes key rather than saving placeholder");
  check(MemoryNotes.get(p,a).equals("我的独家记忆"),"clearing place preserves story");check(values.get("place:"+a).equals("旧GPS缓存"),"clearing place preserves GPS cache");
  StringBuilder longPlace=new StringBuilder();for(int i=0;i<210;i++)longPlace.append("🌸");String limit=MemoryNotes.cleanPlace(longPlace.toString());
  check(limit.codePointCount(0,limit.length())==200,"long manual address bounded by code points");check(!Character.isHighSurrogate(limit.charAt(limit.length()-1)),"Unicode boundary not split");
  System.out.println("TOTAL "+checks+" checks");
 }
}
