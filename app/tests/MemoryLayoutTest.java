package com.maodun.photoswipe;

public final class MemoryLayoutTest {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 public static void main(String[] args){
  String time="2026年11月17日 12:00:00",gps="纬度 31.230000°N · 经度 121.470000°E";
  check(MemoryLayout.metadata(time,"上海市静安区\n"+gps).equals(time+"\n上海市静安区\n"+gps),"time place coordinates use three consecutive rows");
  check(MemoryLayout.metadata(time,"").equals(time),"no place reserves no extra line");
  check(MemoryLayout.metadata(time,null).equals(time),"null place reserves no line");
  check(MemoryLayout.metadata(time,"苏州河畔").equals(time+"\n苏州河畔"),"manual place follows time without empty row");
  check(MemoryLayout.metadata(time,gps).equals(time+"\n"+gps),"coordinates-only photo uses two rows");
  check(MemoryLayout.locationLines("地点未解析 · "+gps).equals("地点未解析\n"+gps),"fallback status separates from coordinates");
  check(MemoryLayout.locationLines(" 上海 \r\n\n "+gps+" \n").equals("上海\n"+gps),"blank rows and CRLF normalized");
  check(MemoryLayout.metadata("",gps).equals(gps),"empty time creates no blank first row");
  check(!MemoryLayout.hasStory(""),"empty story does not expand story area");
  check(!MemoryLayout.hasStory(" \n\t "),"whitespace-only story counts as empty");
  check(!MemoryLayout.hasStory(null),"null story counts as empty");
  check(MemoryLayout.hasStory("旅途中"),"written story is retained");
  for(int imageHeight:new int[]{1,200,948,1400})for(int metaHeight:new int[]{34,80,130,220}){
   int noStory=MemoryLayout.exportHeight(imageHeight,metaHeight,0);
   check(noStory-(66+imageHeight+30+metaHeight)==36,"empty story leaves exactly 36px bottom for image="+imageHeight+",meta="+metaHeight);
   int storyHeight=117;
   check(MemoryLayout.exportHeight(imageHeight,metaHeight,storyHeight)-(66+imageHeight+30+metaHeight+20+2+20+storyHeight)==36,"written story leaves same 36px bottom");
  }
  check(MemoryLayout.exportHeight(1400,130,100)-MemoryLayout.exportHeight(1400,130,0)==142,"only real story height and separator increase canvas");
  System.out.println("TOTAL "+checks+" checks");
 }
}
