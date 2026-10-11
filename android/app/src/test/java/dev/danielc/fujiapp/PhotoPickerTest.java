package dev.danielc.fujiapp;

import android.content.*;
import android.content.pm.*;
import android.provider.MediaStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PhotoPickerTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    private void provide(Intent intent,String name){ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName="test.photos";info.activityInfo.name=name;Shadows.shadowOf(context().getPackageManager()).addResolveInfoForIntent(intent,info);}
    @Test @Config(sdk=35) public void platformPhotoPickerIsPreferredAndSupportsMultiplePhotos(){
        provide(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"),"PhotoPicker");
        Intent single=PhotoPicker.create(context(),1);assertEquals(MediaStore.ACTION_PICK_IMAGES,single.getAction());assertFalse(single.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX));
        Intent multiple=PhotoPicker.create(context(),5);assertEquals(MediaStore.ACTION_PICK_IMAGES,multiple.getAction());assertEquals(Math.min(5,MediaStore.getPickImagesMaxLimit()),multiple.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,0));assertTrue((multiple.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
    }
    @Test public void olderPhonesOpenTheirGalleryBeforeTheFileBrowser(){
        provide(new Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*"),"Gallery");Intent result=PhotoPicker.create(context(),3);assertEquals(Intent.ACTION_PICK,result.getAction());assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,result.getData());assertTrue(result.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
    }
    @Test public void devicesWithoutAPhotoAppStillAllowChoosingAnImage(){Intent result=PhotoPicker.create(context(),1);assertEquals(Intent.ACTION_OPEN_DOCUMENT,result.getAction());assertEquals("image/*",result.getType());assertTrue(result.hasCategory(Intent.CATEGORY_OPENABLE));}
}
