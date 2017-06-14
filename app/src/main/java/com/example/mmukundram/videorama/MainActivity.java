package com.example.mmukundram.videorama;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.graphics.PixelFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ProgressDialog ringProgressDialog;
    private void showProcessingDialog(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCam.stopPreview();
                ringProgressDialog = ProgressDialog.show(MainActivity.this, "", "Panorama", true);
                ringProgressDialog.setCancelable(false);
            }
        });
    }
    private void closeProcessingDialog(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCam.startPreview();
                ringProgressDialog.dismiss();
            }
        });
    }
    private Button captureBtn, saveBtn; // used to interact with capture and save Button in UI
    private SurfaceView mSurfaceView, mSurfaceViewOnTop; // used to display the camera frame in UI
    private Camera mCam;
    private boolean isPreview; // Is the camera frame displaying?
    private boolean safeToTakePicture = true; // Is it safe to capture a picture?
    private Camera.Size getBestPreviewSize(Camera.Parameters parameters){
        Camera.Size bestSize = null;
        List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
        bestSize = sizeList.get(0);
        for(int i = 1; i < sizeList.size(); i++){
            if((sizeList.get(i).width * sizeList.get(i).height) >
                    (bestSize.width * bestSize.height)){
                bestSize = sizeList.get(i);
            }
        }
        return bestSize;
    }
    private Camera.PictureCallback jpegCallback;
    private Runnable imageProcessingRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        {
            imageProcessingRunnable = new Runnable() {
                @Override
                public void run() {
                    showProcessingDialog();
                    // TODO: implement OpenCV parts
                    closeProcessingDialog();
                }
            };
        }
        {
            Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
                public void onPictureTaken(byte[] data, Camera camera) {
                    // decode the byte array to a bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    // Rotate the picture to fit portrait mode
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                    // TODO: Save the image to a List to pass them to OpenCV method

                    Canvas canvas = null;
                    try {
                        canvas = mSurfaceViewOnTop.getHolder().lockCanvas(null);
                        synchronized (mSurfaceViewOnTop.getHolder()) {
                            // Clear canvas
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                            // Scale the image to fit the SurfaceView
                            float scale = 1.0f * mSurfaceView.getHeight() / bitmap.getHeight();
                            Bitmap scaleImage = Bitmap.createScaledBitmap(bitmap, (int)(scale * bitmap.getWidth()), mSurfaceView.getHeight() , false);
                            Paint paint = new Paint();
                            // Set the opacity of the image
                            paint.setAlpha(200);
                            // Draw the image with an offset so we only see one third of image.
                            canvas.drawBitmap(scaleImage, -scaleImage.getWidth() * 2 / 3, 0, paint);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (canvas != null) {
                            mSurfaceViewOnTop.getHolder().unlockCanvasAndPost(canvas);
                        }
                    }
                    // Start preview the camera again and set the take picture flag to true
                    mCam.startPreview();
                    safeToTakePicture = true;
                }
            };
        }


        View.OnClickListener captureOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCam != null && safeToTakePicture){
                    // set the flag to false so we don't take two picture at a same time
                    safeToTakePicture = false;
                    mCam.takePicture(null, null, jpegCallback);
                }
            }
        };
        View.OnClickListener saveOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread thread = new Thread(imageProcessingRunnable);
                thread.start();
            }
        };

        isPreview = false;
        mSurfaceView = (SurfaceView)findViewById(R.id.surfaceView);

        SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback(){
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    // Tell the camera to display the frame on this surfaceview
                    mCam.setPreviewDisplay(holder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // Get the default parameters for camera
                Camera.Parameters myParameters = mCam.getParameters();
                // Select the best preview size
                Camera.Size myBestSize = getBestPreviewSize( myParameters );
                if(myBestSize != null){
                    // Set the preview Size
                    myParameters.setPreviewSize(myBestSize.width, myBestSize.height);
                    // Set the parameters to the camera
                    mCam.setParameters(myParameters);
                    // Rotate the display frame 90 degree to view in portrait mode
                    mCam.setDisplayOrientation(90);
                    // Start the preview
                    mCam.startPreview();
                    isPreview = true;
                }
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        };

        mSurfaceView.getHolder().addCallback(mSurfaceCallback);

        mSurfaceViewOnTop = (SurfaceView)findViewById(R.id.surfaceViewOnTop);
        mSurfaceViewOnTop.setZOrderOnTop(true);    // necessary
        mSurfaceViewOnTop.getHolder().setFormat(PixelFormat.TRANSPARENT);

        captureBtn = (Button) findViewById(R.id.capture);
        captureBtn.setOnClickListener(captureOnClickListener);

        saveBtn = (Button) findViewById(R.id.save);
        saveBtn.setOnClickListener(saveOnClickListener);

    }
    @Override
    protected void onResume(){
        super.onResume();
        mCam = Camera.open(0); // 0 for back camera
    }
    @Override
    protected void onPause(){
        super.onPause();
        if(isPreview){
            mCam.stopPreview();
        }
        mCam.release();
        mCam = null;
        isPreview = false;
    }
}
