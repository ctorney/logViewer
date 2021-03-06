package ctorney.logviewer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import dji.common.camera.CameraSystemState;
import dji.common.gimbal.DJIGimbalAttitude;
import dji.common.gimbal.DJIGimbalRotateDirection;
import dji.common.gimbal.DJIGimbalSpeedRotation;
import dji.common.gimbal.DJIGimbalWorkMode;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.battery.DJIBattery;
import dji.sdk.camera.DJICamera;
import dji.sdk.camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.DJIAircraft;
//import dji.sdk.base.DJIBaseComponent. .DJICompletionCallback;
import dji.sdk.base.DJIBaseProduct;
import dji.common.error.DJIError;
import dji.common.camera.DJICameraSettingsDef.CameraMode;
import dji.common.camera.DJICameraSettingsDef.CameraShootPhotoMode;
import android.os.Bundle;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.io.FileWriter;

import dji.common.camera.DJICameraSettingsDef;
import dji.sdk.camera.DJIMedia;
import dji.sdk.base.DJIBaseComponent;


import dji.sdk.gimbal.DJIGimbal;
import dji.sdk.products.DJIHandHeld;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener,OnClickListener{

    private static final String TAG = MainActivity.class.getName();


    private DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallback = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextView mConnectStatusTextView;
    protected TextureView mVideoSurface = null;

    private Button mCaptureBtn, mShootPhotoModeBtn, mRecordVideoModeBtn;
    private ToggleButton mRecordBtn;
    private TextView recordingTime;

    private int batteryPercent;

    private ImageButton mUpBtn,mRightBtn, mLeftBtn, mDownBtn;

    private boolean recording = false;

    private DJIGimbalSpeedRotation mPitchSpeedRotation;
    private DJIGimbalSpeedRotation mYawSpeedRotation;


    private Timer mTimer;
    private GimbalRotateTimerTask mGimbalRotationTimerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        initUI();



        DJICamera camera = djiConnector.getCameraInstance();

        if (camera != null) {
            camera.setDJICameraUpdatedSystemStateCallback(new DJICamera.CameraUpdatedSystemStateCallback() {
                @Override
                public void onResult(CameraSystemState cameraSystemState) {
                    if (null != cameraSystemState) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                recordingTime.setText(timeString);// + " " + djiConnector.getBatteryPercent());

                                /*
                                 * Update recordingTime TextView visibility and mRecordBtn's check state
                                 */
                                if (isVideoRecording){
                                    recordingTime.setVisibility(View.VISIBLE);
                                }else
                                {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            });
        }


        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(djiConnector.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            onProductChange();
        }

    };

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        DJIBaseProduct product = djiConnector.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
	
                mConnectStatusTextView.setText(djiConnector.getProductInstance().getModel() + " " );//+	String.valueOf(djiConnector.getBatteryPercent()));
                ret = true;
            } else {
                if(product instanceof DJIAircraft) {
                    DJIAircraft aircraft = (DJIAircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    protected void onProductChange() {
        initPreviewer();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        updateTitleBar();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void initUI() {
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        // init mVideoSurface
        //mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);

        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);



            mReceivedVideoDataCallback = new DJICamera.CameraReceivedVideoDataCallback() {
                @Override
                public void onResult(byte[] videoBuffer, int size) {
                    if (null != mCodecManager) {
                        mCodecManager.sendDataToDecoder(videoBuffer, size);
                    }
                }
            };
        }


        //setLogger();
        switchCameraMode(CameraMode.RecordVideo);
        recordingTime = (TextView) findViewById(R.id.timer);

        mRecordBtn = (ToggleButton) findViewById(R.id.btn_record);
        mRightBtn = (ImageButton) findViewById(R.id.btnRight);
        mLeftBtn = (ImageButton) findViewById(R.id.btnLeft);
        mUpBtn = (ImageButton) findViewById(R.id.btnUp);
        mDownBtn = (ImageButton) findViewById(R.id.btnDown);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }


        mRecordBtn.setOnClickListener(this);
        mRightBtn.setOnClickListener(this);
        mLeftBtn.setOnClickListener(this);
        mUpBtn.setOnClickListener(this);
        mDownBtn.setOnClickListener(this);

//        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                showToast("Battery at " + String.valueOf(djiConnector.getBatteryPercent()) + "%");
                if (isChecked) {
                    startRecord();
                } else {
                    stopRecord();
                }
            }
        });
    }

    private void initPreviewer() {



        try {
            DJIBaseProduct product = djiConnector.getProductInstance();
            if (product instanceof DJIHandHeld) {
                product.getCamera().setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallback);

            }
        } catch (Exception exception) {}


    }

    private void uninitPreviewer() {
        DJICamera camera = djiConnector.getCameraInstance();
        if (camera != null){
            // Reset the callback
            djiConnector.getCameraInstance().setDJICameraReceivedVideoDataCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnUp: {
                moveUp();
                break;
            }
            case R.id.btnDown: {
                moveDown();
                break;
            }
            case R.id.btnLeft: {
                moveLeft();
                break;
            }
            case R.id.btnRight: {
                moveRight();
                break;
            }
            default:
                break;
        }
    }

    private void moveUp() {

        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
            }
            else
            {
                mTimer = new Timer();
                mPitchSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.Clockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(mPitchSpeedRotation,null,null);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = djiConnector.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}

    }

    private void moveDown() {
        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
            }
            else
            {
                mTimer = new Timer();
                mPitchSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.CounterClockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(mPitchSpeedRotation,null,null);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = djiConnector.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) { }
                });
            }
        }
        else{showToast("can't move while recording");}

    }

    private void moveLeft() {
        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
            }
            else
            {
                mTimer = new Timer();
                mYawSpeedRotation = new DJIGimbalSpeedRotation(5,DJIGimbalRotateDirection.CounterClockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(null,null,mYawSpeedRotation);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = djiConnector.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}


    }

    private void moveRight() {

        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
            }
            else
            {
                mTimer = new Timer();
                mYawSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.Clockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(null,null,mYawSpeedRotation);
                //mGimbalRotationTimerTask.run();
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }

            DJIGimbal gimbal = djiConnector.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}

    }


    private void switchCameraMode(CameraMode cameraMode){

        DJICamera camera = djiConnector.getCameraInstance();
        if (camera != null) {

            camera.setCameraMode(cameraMode, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {

                    if (error == null) {
 //                       showToast("Switch Camera Mode Succeeded");
                    } else {
   //                     showToast(error.getDescription());
                    }
                }
            });
        }

    }



    // Method for starting recording
    private void startRecord(){

        CameraMode cameraMode = CameraMode.RecordVideo;

        final DJICamera camera = djiConnector.getCameraInstance();


        if (camera != null) {
            camera.startRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error)
                {
                    if (error == null) {
                        //showToast("Record video: success");
                        recording=true;
                        writeToLog();
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the startRecordVideo API
        }
    }

    // Method for stopping recording
    private void stopRecord(){

        DJICamera camera = djiConnector.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){

                @Override
                public void onResult(DJIError error)
                {
                    if(error == null) {
                        recording=false;
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the stopRecordVideo API
        }

    }
    private void writeToLog() {

            try {

                DateFormat df = new SimpleDateFormat("yyyyMMdd");

                // Get the date today using Calendar object.
                Date today = Calendar.getInstance().getTime();

                String todayDate = df.format(today);

                //String filename = Environment.getExternalStorageDirectory().getAbsolutePath() + "/osmoLog/" + todayDate + ".txt";

                String filename = "/sdcard/osmoLog/" + todayDate + ".txt";
                //showToast(filename);

                File file = new File(filename);
                file.setReadable(true, false);
                file.setExecutable(true, false);
                file.setWritable(true, false);
                //File file = new File("/mnt/shared/osmoLogs/" + todayDate + ".txt");
                if (!file.exists()) {
                    try {

                        file.createNewFile();
                    }
                    catch(IOException ex){
                        showToast(ex.getMessage());
                    }


                }
                SimpleDateFormat dfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String nowTime = dfTime.format(today);

                DJIGimbalAttitude djiAttitude;
                djiAttitude = djiConnector.getProductInstance().getGimbal().getAttitudeInDegrees();

                String outputText = nowTime + " " + String.valueOf(djiAttitude.pitch) + "\n" ;

                FileWriter fw = new FileWriter(file,true);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(outputText);
                bw.close();

                try{
                    Process su = Runtime.getRuntime().exec("rm /mnt/shared/osmoLogs/" + todayDate + ".txt");
                    su.waitFor();
                    su = Runtime.getRuntime().exec("cp " + filename + " /mnt/shared/osmoLogs/" + todayDate + ".txt");
                    su.waitFor();

                }catch(IOException e){
                    throw new Exception(e);
                }
                catch(InterruptedException e){
                    throw new Exception(e);
                }



            } catch (Exception e) {
                showToast(e.getMessage());

            }

    }




    class GimbalRotateTimerTask extends TimerTask {
        DJIGimbalSpeedRotation mPitch;
        DJIGimbalSpeedRotation mRoll;
        DJIGimbalSpeedRotation mYaw;

        GimbalRotateTimerTask(DJIGimbalSpeedRotation pitch, DJIGimbalSpeedRotation roll, DJIGimbalSpeedRotation yaw) {
            super();
            this.mPitch = pitch;
            this.mRoll = roll;
            this.mYaw = yaw;
        }
        @Override
        public void run() {
            DJIGimbal gimbal = djiConnector.getProductInstance().getGimbal();
            if (gimbal!=null) {

                gimbal.rotateGimbalBySpeed(mPitch, mRoll, mYaw,
                        new DJICommonCallbacks.DJICompletionCallback() {

                            @Override
                            public void onResult(DJIError error) {

                            }
                        });
            }
        }

    }


}
