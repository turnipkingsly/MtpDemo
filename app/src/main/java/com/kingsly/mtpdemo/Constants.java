package com.kingsly.mtpdemo;

import android.os.Environment;

import java.io.File;

public class Constants {

    public static final String ACTION_USB_PERMISSION = "com.kingsly.MtpDemo.USB_PERMISSION";
    public static final int TASK_COUNT = 10;

    public static final int ACTION_COMPLETE_COPY_FILE = 1;
    public static final int ACTION_SHOW_MESSAGE = 2;
    public static final int ACTION_SHOW_LOG = 3;

    public static final int WRITE_PERMISSION_REQUEST_CODE = 101;


    public static final int NEW_PHONE_COMMAND_TYPE = 1;
    public static final int NEW_PHONE_COMMAND_ID = 1;

    public static final String NEW_PHONE_SEND_MESSAGE_FILE_NAME = "sendMessage.txt";
    public static final String NEW_PHONE_READ_MESSAGE_FILE_NAME = "readMessage.txt";
    public static final String NEW_PHONE_BACKUP_FILE_FOLDER_NAME = "newPhoneMtp";

    public static final int OLD_PHONE_COMMAND_TYPE = 2;
    public static final int OLD_PHONE_COMMAND_REQUEST_ID = 1;
    public static final int OLD_PHONE_COMMAND_RESPONSE_ID = 0;

    public static final String OLD_PHONE_SEND_MESSAGE_FILE_NAME = "readMessage.txt";
    public static final String OLD_PHONE_READ_MESSAGE_FILE_NAME = "sendMessage.txt";
    public static final String OLD_PHONE_BACKUP_FILE_FOLDER_NAME = "oldPhoneMtp";

    public static final String MESSAGE_FILE_FOLDER_NAME = "message";

    public static final String NEW_PHONE_MESSAGE_FILE_FOLDER_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator + NEW_PHONE_BACKUP_FILE_FOLDER_NAME + File.separator + MESSAGE_FILE_FOLDER_NAME;

    public static final String OLD_PHONE_MESSAGE_FILE_FOLDER_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator + OLD_PHONE_BACKUP_FILE_FOLDER_NAME + File.separator + MESSAGE_FILE_FOLDER_NAME;
    
}
