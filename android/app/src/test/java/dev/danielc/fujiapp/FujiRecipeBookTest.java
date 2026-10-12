package dev.danielc.fujiapp;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FujiRecipeBookTest {
 private View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private void inputs(View v,List<EditText> fields){if(v instanceof EditText)fields.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)inputs(((ViewGroup)v).getChildAt(i),fields);}
 private void settle(java.util.function.BooleanSupplier ready)throws Exception{for(int i=0;i<200;i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("UI work did not settle");}
 @Test public void formRejectsInvalidSlotAndClosesOnlyAfterSuccessfulSave()throws Exception{ActivityController<FujiRecipeBook> controller=Robolectric.buildActivity(FujiRecipeBook.class).setup();FujiRecipeBook book=controller.get();find(book.getWindow().getDecorView(),"＋ 新建配方").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();List<EditText> fields=new ArrayList<>();inputs(dialog.getWindow().getDecorView(),fields);assertEquals(FujiRecipes.KEYS.length,fields.size());dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());assertNotNull(fields.get(0).getError());fields.get(0).setText("Integration recipe");fields.get(2).setText("C9");dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());assertNotNull(fields.get(2).getError());fields.get(2).setText("c2");fields.get(3).setText("Classic Chrome");dialog.getButton(-1).performClick();settle(()->!dialog.isShowing());JSONObject saved=new FujiRecipes(book).list().get(0);assertEquals("C2",saved.optString("slot"));assertEquals("Classic Chrome",saved.optString("simulation"));controller.pause().stop().destroy();}
 @Test public void choosingRecipeReturnsOnlyManualNoteToEditor()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();new FujiRecipes(c).save(new JSONObject().put("id","choice").put("title","My look").put("simulation","Classic Chrome"));ActivityController<FujiRecipeBook> controller=Robolectric.buildActivity(FujiRecipeBook.class,new Intent().putExtra("choose_recipe",true)).setup();FujiRecipeBook book=controller.get();settle(()->find(book.getWindow().getDecorView(),"My look")!=null);((View)find(book.getWindow().getDecorView(),"My look").getParent()).performClick();androidx.appcompat.app.AlertDialog detail=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();detail.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.appcompat.app.AlertDialog actions=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();ListView list=actions.getListView();list.performItemClick(list.getAdapter().getView(0,null,list),0,0);assertTrue(book.isFinishing());assertEquals("My look / Classic Chrome",Shadows.shadowOf(book).getResultIntent().getStringExtra("film_note"));controller.pause().stop().destroy();}
}
