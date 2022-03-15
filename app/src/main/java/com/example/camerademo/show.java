package com.example.camerademo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;

public class show extends AppCompatActivity {

    private ImageView imageView;
    String mpath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show);
        imageView = findViewById(R.id.img);
        Bitmap bitmap = BitmapFactory.decodeFile(mpath);
        imageView.setImageBitmap(bitmap);
        Toast.makeText(getApplicationContext(),"站视",Toast.LENGTH_LONG).show();
    }
    public  void getInfo(String path){
        mpath = path;
        /*Uri uri = Uri.fromFile(new File(path));
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(getContentResolver()
                    .openInputStream(uri));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }*/
    }
}