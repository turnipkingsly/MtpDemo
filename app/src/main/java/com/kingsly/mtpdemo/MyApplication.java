package com.kingsly.mtpdemo;

import android.app.Application;

import java.util.concurrent.atomic.AtomicBoolean;

public class MyApplication extends Application{

    public static AtomicBoolean mStoragePermissionGranted = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(this);
    }
}
