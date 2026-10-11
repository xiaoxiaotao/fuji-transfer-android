package dev.danielc.fujiapp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FrameCollectionTest {
 @Test public void favoritesPersistAsStableIdsAndToggleIndependently(){android.content.Context c=RuntimeEnvironment.getApplication();FrameCollection store=new FrameCollection(c);assertTrue(store.toggle(67));assertTrue(store.toggle(0));assertArrayEquals(new int[]{0,67},new FrameCollection(c).list(true));assertFalse(store.toggle(67));assertArrayEquals(new int[]{0},store.list(true));assertArrayEquals(new int[0],store.list(false));assertFalse(store.toggle(-1));}
 @Test public void recentIsDeduplicatedAndBounded(){FrameCollection store=new FrameCollection(RuntimeEnvironment.getApplication());for(int i=0;i<20;i++)store.used(i);assertEquals(12,store.list(false).length);assertEquals(19,store.list(false)[0]);store.used(10);assertEquals(10,store.list(false)[0]);assertEquals(19,store.list(false)[1]);assertEquals(12,store.list(false).length);store.used(999);assertEquals(10,store.list(false)[0]);assertArrayEquals(new int[0],store.list(true));}
 @Test public void staleAndCorruptRecordsCannotBecomeInvalidTemplates(){android.content.Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences(FrameCollection.STORE,0).edit().putString("favorites","[67,67,-1,900,0]").putString("recent","invalid").commit();FrameCollection store=new FrameCollection(c);assertArrayEquals(new int[]{67,0},store.list(true));assertArrayEquals(new int[0],store.list(false));store.used(3);assertArrayEquals(new int[]{3},store.list(false));}
}
