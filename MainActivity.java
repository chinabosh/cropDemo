package com.gwchina.trainee.cropdem;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button crop = (Button) findViewById(R.id.action_crop);
        final CropView cropView = (CropView) findViewById(R.id.crop);
        cropView.setImage("/mnt/sdcard/IMG_20170119_103702.jpg");
        crop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cropView.saveCropImagetofile("/mnt/sdcard/test.jpg");
//                cropView.testOnDraw();
            }
        });

    }
}
