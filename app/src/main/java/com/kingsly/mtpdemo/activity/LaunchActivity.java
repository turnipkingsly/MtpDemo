package com.kingsly.mtpdemo.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;

import com.kingsly.mtpdemo.MyApplication;
import com.kingsly.mtpdemo.R;

import static com.kingsly.mtpdemo.MyApplication.mStoragePermissionGranted;

public class LaunchActivity extends Activity implements View.OnClickListener {


    private static final int WRITE_PERMISSION_REQUEST_CODE = 101;

    private Button mNewPhoneButton;
    private Button mOldPhoneButton;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        mNewPhoneButton = findViewById(R.id.newPhone_btn);
        mOldPhoneButton = findViewById(R.id.oldPhone_btn);

        mNewPhoneButton.setOnClickListener(this);
        mOldPhoneButton.setOnClickListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int hasWritePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (hasWritePermission != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_REQUEST_CODE);
            } else {
                MyApplication.mStoragePermissionGranted.set(true);
            }
        } else {
            MyApplication.mStoragePermissionGranted.set(true);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        Intent intent = new Intent();
        if (R.id.newPhone_btn == id) {
            intent.putExtra(UsbManager.EXTRA_DEVICE, device);
            intent.setClass(this, NewPhoneActivity.class);
        } else if (R.id.oldPhone_btn == id) {
            intent.setClass(this, OldPhoneActivity.class);
        }
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mStoragePermissionGranted.set(true);
            }
        }
    }
}
