package dev.danielc.fujiapp;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.MediaStore;

/** Open the actual photo library first; documents are the last compatibility fallback. */
final class PhotoPicker {
    static Intent create(Context context,int maxPhotos){
        Intent picker=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        if(context.getPackageManager().resolveActivity(picker,0)!=null){
            if(maxPhotos>1&&Build.VERSION.SDK_INT>=33)picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(maxPhotos,MediaStore.getPickImagesMaxLimit()));
            else if(maxPhotos>1)picker=null;
            if(picker!=null)return picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        Intent album=new Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,maxPhotos>1).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(context.getPackageManager().resolveActivity(album,0)!=null)return album;
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,maxPhotos>1).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    }
}
