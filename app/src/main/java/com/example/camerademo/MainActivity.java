package com.example.camerademo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {
    boolean isPreview;
    TextureView mPreviewview;
    HandlerThread mHandlerThread;
    Handler mCameraHandler;
    CameraManager mCameraManger;
    Size mPreviewSize;//最佳的预览尺寸
    Size mCaptureSize;//最佳的拍照尺寸
    String mCameraID;
    CameraDevice mcameraDevice;
    CaptureRequest.Builder mBuilder;
    CaptureRequest mCaptureRequest;
    CameraCaptureSession mCameraCaptureSession;
    ImageReader mImageReader;
    String filename;
    private static final SparseArray ORIENTATION = new SparseArray();
    static {
        ORIENTATION.append(Surface.ROTATION_0,90);
        ORIENTATION.append(Surface.ROTATION_90,0);
        ORIENTATION.append(Surface.ROTATION_180,270);
        ORIENTATION.append(Surface.ROTATION_270,180);
    }
    RxPermissions rxPermissions = new RxPermissions(this);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化预览窗口
        //权限申请框架
        mPreviewview = findViewById(R.id.textureView);
        rxPermissions.request(Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE).subscribe(new Observer<Boolean>() {
            @Override
            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

            }

            @Override
            public void onNext(@io.reactivex.annotations.NonNull Boolean aBoolean) {
                if(aBoolean){
                    Toast.makeText(MainActivity.this, "已获取权限，可以干想干的咯", Toast.LENGTH_SHORT).show();
                }else {
                    //只有用户拒绝开启权限，且选了不再提示时，才会走这里，否则会一直请求开启
                    Toast.makeText(MainActivity.this, "主人，我被禁止啦，去设置权限设置那把我打开哟", Toast.LENGTH_LONG)
                            .show();
                }
            }

            @Override
            public void onError(@io.reactivex.annotations.NonNull Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraThread();

        if (!mPreviewview.isAvailable()) {
            mPreviewview.setSurfaceTextureListener(mTextureListener);
        }
        else {
            try {
                startPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            //当Surfacetexture可用时,设置相机的参数，并打开摄像头
            setupCamera(width, height);
            openCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    //设置摄像头参数
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupCamera(int width, int height) {
        mCameraManger = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //拿到摄像头ID
        try {
            for (String cameraID : mCameraManger.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManger.getCameraCharacteristics(cameraID);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map != null) {//找到摄像头能够输出的，最符合我们显示界面分辨率的最小值
                    mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                    mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                        @Override
                        public int compare(Size size, Size t1) {
                            return Long.signum(size.getHeight()*size.getWidth() - t1.getHeight()*t1.getWidth());
                        }
                        @Override
                        public boolean equals(Object o) {
                            return false;
                        }
                    });
                }
                //建立ImageReader，准备保存照片
                setupImageReader();
                mCameraID = cameraID;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<Size>();
        for (Size option : sizeMap) {
            if (width > height) {
                //横屏
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            }
            else {//竖屏
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 1) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum(size.getHeight() * size.getWidth() - t1.getHeight() * t1.getWidth());
                }
            });
        }
        return sizeMap[0];
    }

    //打开摄像头
    private void openCamera() {
        try {
            String [] permissions = {Manifest.permission.CAMERA};
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(permissions,0);

                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mCameraManger.openCamera(mCameraID, mState, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    CameraDevice.StateCallback mState = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mcameraDevice =cameraDevice;
            try {
                startPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mcameraDevice.close();
            mcameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            mcameraDevice.close();
            mcameraDevice = null;
        }
    };
    //开启摄像头线程
    private void startCameraThread(){
        mHandlerThread = new HandlerThread("CameraThread");
        mHandlerThread.start();
        mCameraHandler = new Handler(mHandlerThread.getLooper());

    }
    //开始预览
    private void startPreview() throws CameraAccessException {

        //建立图象缓冲区
        SurfaceTexture mSurfaceTexture = mPreviewview.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        //得到界面的显示对象
        try {
            mBuilder = mcameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mBuilder.addTarget(previewSurface);
            mcameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        mCaptureRequest = mBuilder.build();
                        mCameraCaptureSession = cameraCaptureSession;
                        mCameraCaptureSession.setRepeatingBurst(Collections.singletonList(mCaptureRequest), null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // 建立通道（CaptureSession会话）
    }
    public void capture(View view){
        //获取摄像头的请求
        show mShow = new show();
        CaptureRequest.Builder mCameraBuilder = null;
        try {
            mCameraBuilder = mcameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCameraBuilder.addTarget(mImageReader.getSurface());
        //要获取摄像头方向
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        //设置拍照方向
        mCameraBuilder.set(CaptureRequest.JPEG_ORIENTATION,(Integer) ORIENTATION.get(rotation));

        CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Toast.makeText(getApplicationContext(),"拍照结束",Toast.LENGTH_LONG).show();
                unLockFocus();
            }
        };
        try {
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(mCameraBuilder.build(),mCaptureCallBack,mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //获取图象的缓冲区
        //获取文件的存储权限


    }
    
    private void unLockFocus(){
        mBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        try {
            mCameraCaptureSession.setRepeatingRequest(mBuilder.build(),null,mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupImageReader(){
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(),mCaptureSize.getHeight(),ImageFormat.JPEG,2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                mCameraHandler.post(new ImageSaver(imageReader.acquireNextImage()));
            }
        },mCameraHandler);
    }
    private class ImageSaver implements Runnable{
        Image mImage;
        public ImageSaver(Image image){
            mImage = image;
        }
        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            String path = Environment.getExternalStorageDirectory() + "/DCIM/MyCamera/";
            //这个路径要修改一下
            File mImageFile = new File(path);
            if(!mImageFile.exists()){
                mImageFile.mkdir();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            filename = path + "IMG " + timeStamp+".jpg";
            try {
                FileOutputStream fos = new FileOutputStream(filename);
                fos.write(data,0,data.length);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}