package dev.danielc.fujiapp;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PhotoGroupsTest {
 @Test public void newestDatesFirstUnknownLastAndHandlesPreserved(){Map<Integer,String> dates=new HashMap<>();dates.put(1,"20261010T120000");dates.put(2,"20261011T120000");dates.put(4,"20261011T130000");List<PhotoGroups.Row> rows=PhotoGroups.build(new int[]{1,2,3,4},dates,true);assertEquals(7,rows.size());assertEquals("2026/10/11",rows.get(0).date);assertArrayEquals(new int[]{2,4},rows.get(0).members);assertEquals(2,rows.get(1).handle);assertEquals(4,rows.get(2).handle);assertEquals("2026/10/10",rows.get(3).date);assertEquals("日期待确认",rows.get(5).date);assertEquals(3,rows.get(6).handle);}
 @Test public void headersNeverCollideWithUnsignedCameraHandles(){Map<Integer,String> dates=new HashMap<>();dates.put(-1,"20261011");List<PhotoGroups.Row> rows=PhotoGroups.build(new int[]{-1},dates,true);assertTrue(rows.get(0).id()>0xffffffffL);assertEquals(0xffffffffL,rows.get(1).id());assertNotEquals(rows.get(0).id(),rows.get(1).id());}
 @Test public void ungroupedKeepsRequestedOrder(){List<PhotoGroups.Row> rows=PhotoGroups.build(new int[]{7,2,9},Collections.emptyMap(),false);assertEquals(3,rows.size());assertFalse(rows.get(0).header);assertEquals(7,rows.get(0).handle);assertEquals(2,rows.get(1).handle);assertEquals(9,rows.get(2).handle);}
 @Test public void invalidCameraDatesStayUnknown(){assertEquals("日期待确认",PhotoGroups.day("20260230T000000"));assertEquals("日期待确认",PhotoGroups.day(""));assertEquals("2024/02/29",PhotoGroups.day("20240229T000000"));}
 @Test public void selectingDateGroupNeverSelectsSectionIds(){PhotoSelection selection=new PhotoSelection();List<PhotoGroups.Row> rows=PhotoGroups.build(new int[]{42,43},Collections.emptyMap(),true);selection.selectAll(rows.get(0).members);assertArrayEquals(new int[]{42,43},selection.snapshot());}
}
