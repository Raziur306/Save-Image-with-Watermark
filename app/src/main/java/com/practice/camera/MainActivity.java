package com.practice.camera;


import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.ContentValues.TAG;
import static android.os.Build.VERSION.SDK_INT;

import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;


import android.content.ContentResolver;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.provider.MediaStore;
import android.util.Log;

import android.widget.Toast;


import java.io.File;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import com.practice.camera.databinding.ActivityMainBinding;



public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private boolean readPermissionGranted = false;
    private boolean writePermissionGranted = false;
    private boolean cameraPermissionGranted = false;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Uri> takePhoto;
    private Uri imageUri;

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.captureImage.setOnClickListener(view -> {
            updateOrRequestPermission();
        });

        binding.saveTogGallery.setOnClickListener(view -> {
            if (imageUri == null) {
                Toast.makeText(this, "Take photo to save", Toast.LENGTH_SHORT).show();
            } else {
                savePhotoToExternalStorage();
            }
        });


        //register for activity result 
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            readPermissionGranted = result.get(READ_EXTERNAL_STORAGE) != null ? result.get(READ_EXTERNAL_STORAGE) : readPermissionGranted;
            writePermissionGranted = result.get(WRITE_EXTERNAL_STORAGE) != null ? result.get(WRITE_EXTERNAL_STORAGE) : writePermissionGranted;
            cameraPermissionGranted = result.get(CAMERA) != null ? result.get(CAMERA) : cameraPermissionGranted;
        });
        //register for taking photo
        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result) {
                binding.imageView.setImageURI(null);
                binding.imageView.setImageBitmap(null);
                binding.imageView.setImageURI(imageUri);
            }
        });
    }

    private void updateOrRequestPermission() {

        //checking already permission granted or not
        boolean readPermission = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean writePermission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean cameraPermission = ContextCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED;

        readPermissionGranted = readPermission;
        writePermissionGranted = writePermissionGranted || (SDK_INT >= Build.VERSION_CODES.Q);
        cameraPermissionGranted = cameraPermission;

        //permission list that need to request
        ArrayList<String> permissionToRequest = new ArrayList<String>();
        if (!readPermissionGranted) permissionToRequest.add(READ_EXTERNAL_STORAGE);
        if (!writePermissionGranted) permissionToRequest.add(WRITE_EXTERNAL_STORAGE);
        if (!cameraPermissionGranted) permissionToRequest.add(CAMERA);

        if (!permissionToRequest.isEmpty()) { //launch for permission request
            permissionLauncher.launch(permissionToRequest.toArray(new String[0]));
        }

        if (readPermissionGranted && writePermissionGranted && cameraPermissionGranted) {
            imageUri = createImageUri();
            takePhoto.launch(imageUri);
        }

    }


    //create private image file uri
    private Uri createImageUri() {
        File file = new File(getApplicationContext().getFilesDir(), "camera_photo.jpg");
        return FileProvider.getUriForFile(getApplicationContext(), "com.practice.camera.fileProvider", file);
    }


    //saving photo to external storage
    private void savePhotoToExternalStorage() {
        ContentResolver contentResolver = getContentResolver();
        Uri imageCollection;
        if (SDK_INT >= Build.VERSION_CODES.Q)
            imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        else imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        try {
            Bitmap bitmap = null;
            if (SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = getMarkBitmap(ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, imageUri)), getMetaData());
            } else {
                bitmap = getMarkBitmap(MediaStore.Images.Media.getBitmap(contentResolver, imageUri), getMetaData());
            }

            binding.imageView.setImageURI(null);
            binding.imageView.setImageBitmap(bitmap);

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + new Date().getTime() + ".jpg");
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
            contentValues.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

            Uri uri = contentResolver.insert(imageCollection, contentValues);
            OutputStream stream = contentResolver.openOutputStream(uri);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            Toast.makeText(MainActivity.this, "Photo Saved Successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show();
            Log.d(TAG, exception.getMessage().toString());
        }


    }


    private Bitmap getMarkBitmap(Bitmap hardwareBitmap, ArrayList<String> metaData) {
        binding.imageView.buildDrawingCache();
        Bitmap bitmap = binding.imageView.getDrawingCache();
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, hardwareBitmap.getWidth(), hardwareBitmap.getHeight(), false);
        Canvas canvas = new Canvas(scaledBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha(80);
        paint.setTextSize(65);
        paint.setAntiAlias(true);
        paint.setColor(ContextCompat.getColor(this, R.color.fade_white));
        canvas.drawRect(120, canvas.getHeight() - (100 * metaData.size()), 1200, canvas.getHeight() - 20, paint);

        paint.setColor(ContextCompat.getColor(this, R.color.black));
        int height = canvas.getHeight() - (90 * metaData.size()) + 20;
        for (String value : metaData) {
            canvas.drawText(value, 130, height, paint);
            height += 90;
        }


        return scaledBitmap;
    }


    //getTags
    @RequiresApi(api = Build.VERSION_CODES.N)
    private ArrayList<String> getMetaData() {
        ArrayList<String> metaData = new ArrayList<String>();
        try {
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(imageUri));
            String lattitude = exif.getAttribute(ExifInterface.LATITUDE_NORTH);
            metaData.add("Latitude: " + lattitude);
            String longitude = exif.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE);
            metaData.add("Longitude: " + longitude);
            String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            metaData.add("Orientation: " + orientation);
            String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            metaData.add("Date: " + date);
            String location = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
            metaData.add("Location: " + location);
        } catch (Exception e) {

        }
        return metaData;
    }
}