package dev.danielc.fujiapp;

import java.util.Comparator;

/** Stable ordering keeps equal names or timestamps from jumping during a refresh. */
final class LibraryOrdering {
    static final String[] TITLES={"最近保存","文件名","文件大小"};
    static Comparator<SavedLibrary.Photo> comparator(int order){
        return (a,b)->{
            int value=order==1?String.CASE_INSENSITIVE_ORDER.compare(a.name,b.name):order==2?Long.compare(b.size,a.size):Long.compare(b.saved,a.saved);
            if(value==0)value=Long.compare(b.saved,a.saved);
            return value==0?Long.compare(b.id,a.id):value;
        };
    }
}
