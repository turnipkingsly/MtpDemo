package com.kingsly.mtpdemo.activity;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.kingsly.mtpdemo.MyApplication;
import com.kingsly.mtpdemo.R;
import com.kingsly.mtpdemo.mtp.MtpService;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kingsly.mtpdemo.Constants.ACTION_COMPLETE_COPY_FILE;
import static com.kingsly.mtpdemo.Constants.ACTION_SHOW_LOG;
import static com.kingsly.mtpdemo.Constants.ACTION_SHOW_MESSAGE;
import static com.kingsly.mtpdemo.Constants.ACTION_USB_PERMISSION;
import static com.kingsly.mtpdemo.Constants.TASK_COUNT;
import static com.kingsly.mtpdemo.Constants.WRITE_PERMISSION_REQUEST_CODE;

public class NewPhoneActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "NewPhoneActivity";


    private TextView mLogTextView;
    private TextView mReceiveMessageTextView;
    private EditText mSendMsgEditText;
    private Button mSendMsgButton;
    private Button mReadMsgButton;
    private Button mCopyFileButton;


    private UsbManager mUsbManager;
    private MtpService mService;
    private MyHandler<NewPhoneActivity> mHandler;

    private long mStartTime;
    private long mEndTime;

    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private AtomicInteger mCompleteTaskCount = new AtomicInteger();

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                reset();
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    setupDevice(device);
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Toast.makeText(NewPhoneActivity.this, "ACTION_USB_DEVICE_ATTACHED", Toast.LENGTH_LONG).show();
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    discoverDevice(device);
                } else {
                    Toast.makeText(NewPhoneActivity.this, "usbDevice is null", Toast.LENGTH_LONG).show();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "USB device detached");
                mIsConnected.set(false);
                if (mService != null) {
                    mService.usbDisconnect();
                }
            }

        }
    };

    public static class MyHandler<T> extends Handler {

        private final WeakReference<T> mActivty;

        MyHandler(T activity) {
            this.mActivty = new WeakReference<T>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            NewPhoneActivity activity = (NewPhoneActivity) mActivty.get();
            if (ACTION_COMPLETE_COPY_FILE == msg.what) {
                activity.copyFileComplete(msg.getData().getString("TASK_NAME"));
            } else if (ACTION_SHOW_MESSAGE == msg.what) {
                activity.showMessage(msg.getData().getString("message"));
            } else if (ACTION_SHOW_LOG == msg.what) {
                activity.showLog(msg.getData().getString("log"));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_phone);
        init();
    }

    private void initView() {
        mLogTextView = findViewById(R.id.log_tv);
        mReceiveMessageTextView = findViewById(R.id.receiveMsg_tv);
        mSendMsgEditText = findViewById(R.id.message_et);
        mSendMsgButton = findViewById(R.id.sendMsg_btn);
        mReadMsgButton = findViewById(R.id.readMsg_btn);
        mCopyFileButton = findViewById(R.id.copyFile_btn);

        mSendMsgButton.setOnClickListener(this);
        mReadMsgButton.setOnClickListener(this);
        mCopyFileButton.setOnClickListener(this);
    }

    private void init() {

        initView();

        mHandler = new MyHandler<>(this);
        mService = new MtpService(mHandler);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        discoverDevice(device);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (MyApplication.mStoragePermissionGranted.get()) {
                MyApplication.mStoragePermissionGranted.set(true);
                mService.createMessageFile();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_REQUEST_CODE);
            }
        } else {
            MyApplication.mStoragePermissionGranted.set(true);
            mService.createMessageFile();
        }
    }


    private void copyFileComplete(String taskName) {
        mCompleteTaskCount.getAndIncrement();
        mEndTime = System.currentTimeMillis();
        long totalTime = mEndTime - mStartTime;
        if (mCompleteTaskCount.get() == TASK_COUNT) {
            mLogTextView.setText("all task complete and  cost time is  " + (totalTime / (float) 1000) + " seconds");
        } else {
            mLogTextView.setText("task " + taskName + " complete and cost time " + (totalTime / (float) 1000) + " seconds");
        }
    }

    private void showLog(String log) {
        mLogTextView.setText(log);
    }

    private void showMessage(String message) {
        Log.e(TAG, "showMessage " + message);
        mReceiveMessageTextView.setText(mReceiveMessageTextView.getText() + "\n" + message);
    }

    private void discoverDevice(UsbDevice usbDevice) {
        if (usbDevice != null && mUsbManager.hasPermission(usbDevice)) {
            setupDevice(usbDevice);
        } else {
            if (usbDevice != null) {
                Toast.makeText(NewPhoneActivity.this, "discoverDevice has no Permission", Toast.LENGTH_LONG).show();
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                        ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(usbDevice, permissionIntent);
            } else {
                Toast.makeText(NewPhoneActivity.this, "discoverDevice device is null", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupDevice(final UsbDevice usbDevice) {
        Log.i(TAG, "setupDevice invoke");
        reset();
        UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
        MtpDevice device = new MtpDevice(usbDevice);
        if (device.open(connection)) {
            if (mService == null) {
                mService = new MtpService(mHandler);
            }
            mService.setupDevice(device);
        } else {
            Toast.makeText(NewPhoneActivity.this, "mtpDevice open failure", Toast.LENGTH_LONG).show();
        }
    }


    private void reset() {
        mStartTime = 0;
        mEndTime = 0;
        mCompleteTaskCount.set(0);
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.sendMsg_btn:
                mService.sendMessage(mSendMsgEditText.getText().toString());
                break;
            case R.id.readMsg_btn:
//                mService.readMessage();
                break;
            case R.id.copyFile_btn:
                mStartTime = System.currentTimeMillis();
                mService.copyFileByRecursion();
                break;
            default:
                break;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MyApplication.mStoragePermissionGranted.set(true);
                mService.createMessageFile();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
    }
}
