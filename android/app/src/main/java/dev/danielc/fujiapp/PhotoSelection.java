package dev.danielc.fujiapp;
import java.util.LinkedHashSet;
/** Selection follows camera object handles, independently of recycled thumbnail views. */
final class PhotoSelection {
 private final LinkedHashSet<Integer> handles = new LinkedHashSet<>();
 boolean contains(int handle) { return handles.contains(handle); }
 void toggle(int handle) { if(!handles.remove(handle))handles.add(handle); }
 void remove(int handle) { handles.remove(handle); }
 void clear() { handles.clear(); }
 int size() { return handles.size(); }
 void selectAll(int[] available) { for(int handle:available)handles.add(handle); }
 void selectRange(int[] available,int from,int to){if(from<1||to<from||to>available.length)throw new IllegalArgumentException("Invalid range");for(int i=from-1;i<to;i++)handles.add(available[i]);}
 int[] snapshot() { int[] result=new int[handles.size()];int i=0;for(int handle:handles)result[i++]=handle;return result; }
}
