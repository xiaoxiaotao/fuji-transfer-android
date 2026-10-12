package dev.danielc.fujiapp;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FujiRecipesTest {
 @Test public void savesFullUserRecordAndUpdatesWithoutDuplicating()throws Exception{FujiRecipes store=new FujiRecipes(RuntimeEnvironment.getApplication());JSONObject r=new JSONObject().put("id","personal").put("title","My look").put("camera","X-E5").put("slot","C2").put("simulation","Classic Chrome").put("whiteBalance","Daylight").put("wbShift","R +2 B -3").put("notes","Cloudy afternoon");store.save(r);r.put("notes","Updated");store.save(r);assertEquals(1,new FujiRecipes(RuntimeEnvironment.getApplication()).list().size());JSONObject loaded=store.list().get(0);assertEquals("Updated",loaded.getString("notes"));assertEquals("R +2 B -3",loaded.getString("wbShift"));assertEquals("C2",loaded.getString("slot"));store.delete("personal");assertTrue(store.list().isEmpty());}
 @Test public void filtersByModelFilmAndFavoriteWithoutClaimingCompatibility()throws Exception{JSONObject r=new JSONObject().put("title","Travel").put("camera","X-E5").put("simulation","Classic Chrome").put("favorite",false);assertTrue(FujiRecipes.matches(r," x-e5 ",false));assertTrue(FujiRecipes.matches(r,"CHROME",false));assertFalse(FujiRecipes.matches(r,"X-T5",false));assertFalse(FujiRecipes.matches(r,"",true));r.put("favorite",true);assertTrue(FujiRecipes.matches(r,"",true));}
 @Test public void shareAndPhotoNoteKeepManualOriginAndBoundLength()throws Exception{JSONObject r=new JSONObject().put("title","My recipe").put("simulation","Classic Chrome").put("shadow","+1");assertEquals("My recipe / Classic Chrome",FujiRecipes.note(r));String text=FujiRecipes.shareText(r);assertTrue(text.contains("手动记录"));assertTrue(text.contains("阴影：+1"));assertFalse(text.contains("降噪："));r.put("title",new String(new char[100]).replace('\0','A'));assertEquals(48,FujiRecipes.note(r).length());}
 @Test public void unnamedRecipesCannotOverwriteRecords()throws Exception{FujiRecipes store=new FujiRecipes(RuntimeEnvironment.getApplication());try{store.save(new JSONObject().put("id","blank").put("title","  "));fail();}catch(IllegalArgumentException expected){}assertTrue(store.list().isEmpty());try{store.delete("../original");fail();}catch(IllegalArgumentException expected){}}
}
