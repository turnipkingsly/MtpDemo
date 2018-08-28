package com.kingsly.mtpdemo.activity;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.kingsly.mtpdemo.Command;
import com.kingsly.mtpdemo.Constants;
import com.kingsly.mtpdemo.MyApplication;
import com.kingsly.mtpdemo.R;
import com.kingsly.mtpdemo.utils.ArrayUtils;
import com.kingsly.mtpdemo.utils.ByteUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.kingsly.mtpdemo.Constants.OLD_PHONE_COMMAND_REQUEST_ID;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_COMMAND_RESPONSE_ID;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_COMMAND_TYPE;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_MESSAGE_FILE_FOLDER_PATH;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_READ_MESSAGE_FILE_NAME;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_SEND_MESSAGE_FILE_NAME;

public class OldPhoneActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "OldPhoneActivity";


    private TextView mLogMessageTextView;
    private TextView mReceiveMessageTextView;
    private EditText mSendMsgEditText;
    private Button mSendMsgButton;
    private Button mReadMsgButton;

    private File mSendMessgaeFile;
    private File mReadMessgaeFile;

    private AtomicBoolean mIsSendingMessage = new AtomicBoolean(false);
    private AtomicBoolean mIsReadingMessage = new AtomicBoolean(false);
    private LinkedBlockingQueue<Command> mCommandQueue = new LinkedBlockingQueue<>();


    private ThreadPoolExecutor mExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);

    private OldPhoneActivity.MyHandler<OldPhoneActivity> mHandler;

    private Object mLock = new Object();

    private MyFileObserver mObserver;

    class MyFileObserver extends FileObserver {

        public MyFileObserver(String path) {
            super(path);
        }

        public MyFileObserver(String path, int mask) {
            super(path, mask);
        }

        @Override
        public void onEvent(int event, @Nullable String path) {

            if (event == CREATE ) {
                Log.e(TAG, "onEvent path is " + path);
                synchronized (mLock) {
                    if ((OLD_PHONE_READ_MESSAGE_FILE_NAME).equals(path)) {
                        mLock.notify();
                    }
                }
            }
        }
    }

    private static class MyHandler<T> extends Handler {

        private final WeakReference<T> mActivty;

        MyHandler(T activity) {
            this.mActivty = new WeakReference<T>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            OldPhoneActivity activity = (OldPhoneActivity) mActivty.get();
            if (Constants.ACTION_SHOW_MESSAGE == msg.what) {
                activity.showMessage(msg.getData().getString("message"));
            } else if (Constants.ACTION_SHOW_LOG == msg.what) {
                activity.showLog(msg.getData().getString("log"));
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_phone);
        init();
    }

    private void initView() {
        mLogMessageTextView = findViewById(R.id.log_tv);
        mReceiveMessageTextView = findViewById(R.id.receiveMsg_tv);
        mSendMsgEditText = findViewById(R.id.message_et);
        mSendMsgButton = findViewById(R.id.sendMsg_btn);
        mReadMsgButton = findViewById(R.id.readMsg_btn);

        mSendMsgButton.setOnClickListener(this);
        mReadMsgButton.setOnClickListener(this);

        mObserver = new MyFileObserver(OLD_PHONE_MESSAGE_FILE_FOLDER_PATH);
        mObserver.startWatching();
        readMessage();
        sendMessage();
    }

    private void init() {

        initView();

        mHandler = new MyHandler<>(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (MyApplication.mStoragePermissionGranted.get()) {
                createMessageFile();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.WRITE_PERMISSION_REQUEST_CODE);
            }
        } else {
            createMessageFile();
        }
    }

    private void showLog(String log) {
        mLogMessageTextView.setText(log);
    }

    private void showMessage(String message) {
        mReceiveMessageTextView.setText(mReceiveMessageTextView.getText() + "\n" + message);
    }

    /**
     * create file for transfer message
     */
    private void createMessageFile() {
        mSendMessgaeFile = new File(OLD_PHONE_MESSAGE_FILE_FOLDER_PATH, OLD_PHONE_SEND_MESSAGE_FILE_NAME);
        try {
            if (!mSendMessgaeFile.getParentFile().exists()) {
                mSendMessgaeFile.getParentFile().mkdirs();
            }
            if (mSendMessgaeFile.exists()) {
                mSendMessgaeFile.delete();
            }
            if (mSendMessgaeFile.createNewFile()) {
                MediaScannerConnection.scanFile(getApplicationContext(),
                        new String[]{OLD_PHONE_MESSAGE_FILE_FOLDER_PATH + File.separator
                                + OLD_PHONE_SEND_MESSAGE_FILE_NAME}, null, null);
                mLogMessageTextView.setText("send message file:" + mSendMessgaeFile.getAbsolutePath());
                Log.i(TAG, "send message file " + mSendMessgaeFile.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            mLogMessageTextView.setText(getResources().getString(R.string.create_file_fail));
        }
    }

    private void readMessage() {
        mIsReadingMessage.set(true);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mReadMessgaeFile = new File(OLD_PHONE_MESSAGE_FILE_FOLDER_PATH, OLD_PHONE_READ_MESSAGE_FILE_NAME);
                while (mIsReadingMessage.get()) {
                    synchronized (mLock) {
                        if (!mReadMessgaeFile.exists()) {
                            try {
                                mLock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    ObjectInputStream reader = null;
                    Command command = null;
                    try {
                        reader = new ObjectInputStream(new FileInputStream(mReadMessgaeFile));
                        command = (Command) reader.readObject();
                        if (command != null) {
                            Message message = Message.obtain();
                            Bundle bundle = new Bundle();
                            bundle.putString("message", command.getCommandContent());
                            message.what = Constants.ACTION_SHOW_MESSAGE;
                            message.setData(bundle);
                            mHandler.sendMessage(message);
                            if (mReadMessgaeFile.delete()) {
                                MediaScannerConnection.scanFile(getApplicationContext(),
                                        new String[]{OLD_PHONE_MESSAGE_FILE_FOLDER_PATH + File.separator
                                                + OLD_PHONE_READ_MESSAGE_FILE_NAME}, null, null);

                                command = new Command();
                                command.setCommandType(OLD_PHONE_COMMAND_TYPE);
                                command.setCommandID(OLD_PHONE_COMMAND_RESPONSE_ID);
                                command.setCommandContent("response from device");
                                mCommandQueue.offer(command);
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
    }

    private void sendMessage() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mIsSendingMessage.set(true);
                while (mIsSendingMessage.get()) {
                    Command command = null;
                    FileOutputStream writer = null;
                    try {
                        command = mCommandQueue.take();
                        if (command != null) {
                            writer = new FileOutputStream(mSendMessgaeFile, true);
                            byte[] buffer = ArrayUtils.objectToBytes(command);
                            Log.e(TAG, "object buffer length is " + buffer.length);
                            byte[] bytesLength = new byte[4];
                            ByteUtil.int2BytesBe(buffer.length, bytesLength, 0);
                            Log.e(TAG, "int2BytesBe length " + ByteUtil.bytes2int32Be(bytesLength));
                            writer.write(bytesLength);
                            writer.write(buffer);
                            writer.flush();
                            Log.e(TAG, "send message command content is " + command.getCommandContent());

                            MediaScannerConnection.scanFile(getApplicationContext(),
                                    new String[]{OLD_PHONE_MESSAGE_FILE_FOLDER_PATH + File.separator
                                            + OLD_PHONE_SEND_MESSAGE_FILE_NAME}, null, null);
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        if (writer != null) {
                            try {
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.sendMsg_btn:
                String content = mSendMsgEditText.getText().toString();
                if (!TextUtils.isEmpty(content)) {
                    Command command = new Command();
                    command.setCommandType(OLD_PHONE_COMMAND_TYPE);
                    command.setCommandID(OLD_PHONE_COMMAND_REQUEST_ID);
                    command.setCommandContent(content);
                    mCommandQueue.offer(command);
                }
                break;
            case R.id.readMsg_btn:
//                readMessage();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.WRITE_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MyApplication.mStoragePermissionGranted.set(true);
                createMessageFile();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mObserver.stopWatching();
    }
}
