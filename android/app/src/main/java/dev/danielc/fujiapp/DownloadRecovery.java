package dev.danielc.fujiapp;
import java.util.*;
/** Reconnects never reuse old handles; duplicate identities are excluded rather than guessed. */
final class DownloadRecovery {
 private final Map<String,Integer> candidates=new HashMap<>();
 private final Set<String> ambiguous=new HashSet<>();
 void observe(String key,int handle){if(key==null||ambiguous.contains(key))return;Integer previous=candidates.get(key);if(previous==null)candidates.put(key,handle);if(previous!=null&&previous!=handle){candidates.remove(key);ambiguous.add(key);}}
 Map<String,Integer> matches(){return new HashMap<>(candidates);}
}
