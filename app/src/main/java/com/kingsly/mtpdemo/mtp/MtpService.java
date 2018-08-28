package com.kingsly.mtpdemo.mtp;

import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpEvent;
import android.mtp.MtpObjectInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.kingsly.mtpdemo.Command;
import com.kingsly.mtpdemo.Constants;
import com.kingsly.mtpdemo.MyApplication;
import com.kingsly.mtpdemo.activity.NewPhoneActivity;
import com.kingsly.mtpdemo.utils.ArrayUtils;
import com.kingsly.mtpdemo.utils.ByteUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_COMMAND_ID;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_COMMAND_TYPE;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_SEND_MESSAGE_FILE_NAME;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_COMMAND_RESPONSE_ID;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_COMMAND_TYPE;
import static com.kingsly.mtpdemo.utils.ArrayUtils.splitAry;
import static com.kingsly.mtpdemo.Constants.ACTION_COMPLETE_COPY_FILE;
import static com.kingsly.mtpdemo.Constants.ACTION_SHOW_LOG;
import static com.kingsly.mtpdemo.Constants.ACTION_SHOW_MESSAGE;
import static com.kingsly.mtpdemo.Constants.MESSAGE_FILE_FOLDER_NAME;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_BACKUP_FILE_FOLDER_NAME;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_MESSAGE_FILE_FOLDER_PATH;
import static com.kingsly.mtpdemo.Constants.NEW_PHONE_READ_MESSAGE_FILE_NAME;
import static com.kingsly.mtpdemo.Constants.OLD_PHONE_MESSAGE_FILE_FOLDER_PATH;
import static com.kingsly.mtpdemo.Constants.TASK_COUNT;

public class MtpService {

    private static final String TAG = "MtpService";


    private ThreadPoolExecutor mExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(TASK_COUNT);
    private StringBuffer mBasePath = new StringBuffer();
    private StringBuffer mFilePath = new StringBuffer();
    private ConcurrentHashMap<Integer, String> mFoldersPathMap = new ConcurrentHashMap<>();
    private AtomicInteger mMessageFileFolderHandle = new AtomicInteger(-1);
    private AtomicInteger mReadMessageFileHandle = new AtomicInteger(-1);
    private AtomicInteger mSendMessageFileHandle = new AtomicInteger(-1);
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private AtomicBoolean mIsSendingMessage = new AtomicBoolean(false);
    private AtomicBoolean mIsReadingMessage = new AtomicBoolean(false);
    private AtomicLong mReadOffset = new AtomicLong(0);

    private LinkedBlockingQueue<Command> mCommandQueue = new LinkedBlockingQueue<>();

    private MtpDevice mMtpDevice;
    private int mStorageId;
    private Object[] mObjectHandles;

    private File mSendMessgaeFile;
    private ParcelFileDescriptor mSendMessgaeFileDescriptor;

    private NewPhoneActivity.MyHandler mHandler;

    private Object mLock = new Object();

    private BlockingQueue<Integer> mObjectHandleQueue = new LinkedBlockingQueue<>();

    public MtpService() {

    }

    public MtpService(NewPhoneActivity.MyHandler handler) {
        this.mHandler = handler;
    }


    /**
     * create file for transfer message
     */
    public void createMessageFile() {

        mBasePath.append(Environment.getExternalStorageDirectory().getAbsolutePath())
                .append(File.separator)
                .append(NEW_PHONE_BACKUP_FILE_FOLDER_NAME);

        File file = new File(mBasePath.toString());
        if (!file.exists()) {
            file.mkdirs();
        }

        mSendMessgaeFile = new File(NEW_PHONE_MESSAGE_FILE_FOLDER_PATH, NEW_PHONE_SEND_MESSAGE_FILE_NAME);
        try {
            if (!mSendMessgaeFile.getParentFile().exists()) {
                mSendMessgaeFile.getParentFile().mkdirs();
            }
            if (mSendMessgaeFile.exists()) {
                mSendMessgaeFile.delete();
            }
            if (mSendMessgaeFile.createNewFile()) {
                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putString("log", "send message file:" + mSendMessgaeFile.getAbsolutePath());
                message.what = ACTION_SHOW_LOG;
                message.setData(bundle);
                mHandler.sendMessage(message);
                Log.i(TAG, "message file " + mSendMessgaeFile.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("log", "消息文件创建失败");
            message.what = ACTION_SHOW_LOG;
            message.setData(bundle);
            mHandler.sendMessage(message);
            Log.i(TAG, "message file " + mSendMessgaeFile.getAbsolutePath());
        }
    }

    public void setupDevice(MtpDevice device) {
        Log.i(TAG, "setupDevice invoke");
        reset();
        mMtpDevice = device;
        int[] storageIds = mMtpDevice.getStorageIds();
        if (storageIds != null) {
            mIsConnected.set(true);
            mStorageId = storageIds[0];
            final int[] objectHandles = mMtpDevice.getObjectHandles(mStorageId, 0, -1);
            mObjectHandleQueue.addAll(Arrays.<Integer>asList());
            if (objectHandles != null) {
                mObjectHandles = splitAry(objectHandles, TASK_COUNT);
                ArrayUtils.arrayToQueue(mObjectHandleQueue, objectHandles);
                for (int i = 0; i < TASK_COUNT; i++) {
                    final int index = i;
                    mExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            long star = System.currentTimeMillis();
                            int handle = findFileFolderByPath((int[]) mObjectHandles[index], mStorageId, 0, -1,
                                    OLD_PHONE_MESSAGE_FILE_FOLDER_PATH);

                            Log.d(TAG, "findFileFolderByPath cost time is  " + (System.currentTimeMillis() - star) / (float) 1000);

                            if (handle != -1) {
                                mMessageFileFolderHandle.set(handle);
                                mReadMessageFileHandle.set(findFileByName(mStorageId, 0, mMessageFileFolderHandle.get(),
                                        NEW_PHONE_READ_MESSAGE_FILE_NAME));

                                if (mReadMessageFileHandle.get() != -1) {
                                    readMessage();
//                                    readEventsFromDevice();
                                }

                                Message message = Message.obtain();
                                Bundle bundle = new Bundle();
                                bundle.putString("log", "setupDevice complete and readMessageFileHandle is "
                                        + mReadMessageFileHandle.get() + " and cost time is  " + (System.currentTimeMillis() - star) / (float) 1000 + " s");
                                message.what = ACTION_SHOW_LOG;
                                message.setData(bundle);
                                mHandler.sendMessage(message);
                                Log.d(TAG, "message file " + mSendMessgaeFile.getAbsolutePath());
                            }
                        }
                    });
                }
            }
        }
    }

    public void sendMessage(final String message) {
        if (MyApplication.mStoragePermissionGranted.get()) {
            if (!TextUtils.isEmpty(message)) {
                Command command = new Command();
                command.setCommandType(NEW_PHONE_COMMAND_TYPE);
                command.setCommandID(NEW_PHONE_COMMAND_ID);
                command.setCommandContent(message);
                mCommandQueue.offer(command);
                if (!mIsSendingMessage.get()) {
                    mExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mMessageFileFolderHandle.get() != -1) {
                                mIsSendingMessage.set(true);
                                while (mIsSendingMessage.get()) {
                                    Log.i(TAG, "mSendMessageFileHandle value is " + mSendMessageFileHandle.get());
                                    synchronized (mLock) {
                                        if (mSendMessageFileHandle.get() != -1) {
                                            try {
                                                mLock.wait();
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                                continue;
                                            }
                                        }
                                    }
                                    try {
                                        Command command1 = mCommandQueue.take();
                                        sendMessageToDevice(command1, mMessageFileFolderHandle.get());
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else {
                                Log.e(TAG, "can not find message file folder");
                            }
                        }
                    });
                }
            }
        } else {
            Log.e(TAG, "has no permission access storage");
        }
    }

    private void sendMessageToDevice(Command command, int messageFileFolderHandle) {
        MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(messageFileFolderHandle);
        if (command != null && objectInfo != null && mSendMessageFileHandle.get() == -1) {
            ObjectOutputStream writer = null;
            try {
                mSendMessgaeFileDescriptor = ParcelFileDescriptor.open(mSendMessgaeFile, MODE_READ_WRITE);
                writer = new ObjectOutputStream(new FileOutputStream(mSendMessgaeFile));
                writer.writeObject(command);
                writer.flush();
            } catch (IOException e) {
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

            MtpObjectInfo.Builder builder = new MtpObjectInfo.Builder();
            builder.setStorageId(mStorageId);
            builder.setParent(messageFileFolderHandle);
            builder.setName(NEW_PHONE_SEND_MESSAGE_FILE_NAME);
            builder.setCompressedSize(mSendMessgaeFile.length());
            objectInfo = mMtpDevice.sendObjectInfo(builder.build());

            Message message1 = Message.obtain();
            Bundle bundle = new Bundle();
            message1.what = ACTION_SHOW_LOG;
            message1.setData(bundle);
            if (objectInfo != null) {
                mSendMessageFileHandle.set(objectInfo.getObjectHandle());
                boolean sendResult = mMtpDevice.sendObject(objectInfo.getObjectHandle(),
                        objectInfo.getCompressedSize(), mSendMessgaeFileDescriptor);
                if (!sendResult) {
                    Log.e(TAG, "sendObject failure");
                    bundle.putString("log", "sendObject failure and objectHandle is "
                            + objectInfo.getObjectHandle() + " and name is " + objectInfo.getName());
                    mHandler.sendMessage(message1);
                }
            } else {
                bundle.putString("log", "sendObjectInfo failure");
                Log.e(TAG, "sendObjectInfo failure");
                mHandler.sendMessage(message1);
            }

        }
    }

    public void readMessage() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mIsReadingMessage.set(true);
                while (mIsReadingMessage.get()) {
                    readMessageFromDevice(mReadMessageFileHandle.get());
                }
            }
        });
    }

    private void readMessageFromDevice(int messageFileHandle) {
        MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(messageFileHandle);

        if (objectInfo != null) {

//            Log.d(TAG, "readMessageFromDevice--->" + messageFileHandle +
//                    "  and compress size is  " + objectInfo.getCompressedSizeLong() + " and mReadOffset is " + mReadOffset.get());

            if (mReadOffset.get() >= objectInfo.getCompressedSizeLong()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return;
            }

            byte[] sizeBuffer = new byte[4];
            try {
                mMtpDevice.getPartialObject(messageFileHandle, mReadOffset.get(), 4, sizeBuffer);

                int length = ByteUtil.bytes2int32Be(sizeBuffer);
                byte[] buffer = new byte[length];
                mMtpDevice.getPartialObject(messageFileHandle, mReadOffset.get() + 4, length, buffer);
                mReadOffset.set(mReadOffset.get() + sizeBuffer.length + length);
                Command command = (Command) ArrayUtils.bytesToObject(buffer);

                Log.i(TAG, "readMessageFromDevice length is  --->" + length
                        + "readMessageFromDevice command content  --->" + command.getCommandContent());

                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                message.what = ACTION_SHOW_MESSAGE;
                bundle.putString("message", command.getCommandContent());
                message.setData(bundle);
                mHandler.sendMessage(message);

                synchronized (mLock) {
                    if (command.getCommandType() == OLD_PHONE_COMMAND_TYPE
                            && command.getCommandID() == OLD_PHONE_COMMAND_RESPONSE_ID) {
                        Log.i(TAG, "this is response from device");
                        mSendMessageFileHandle.set(-1);
                        mLock.notify();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, e.toString());
            }
        } else {
            Log.e(TAG, "cant not find message file MtpObjectInfo");
        }
    }


    public void copyFile() {
        mFoldersPathMap.clear();
        for (int i = 0; i < TASK_COUNT; i++) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    long star = System.currentTimeMillis();

                    while (mObjectHandleQueue.size() > 0) {
                        int objectHandle = mObjectHandleQueue.poll();
                        copyFileFromDevice(objectHandle, mStorageId, 0);
                    }


                    Log.e(TAG, Thread.currentThread().getName() + "  task copyFile cost time is  " +
                            (System.currentTimeMillis() - star) / (float) 1000 + "  seconds");

                    Message message = Message.obtain();
                    Bundle bundle = new Bundle();
                    bundle.putString("TASK_NAME", Thread.currentThread().getName());
                    message.what = ACTION_COMPLETE_COPY_FILE;
                    message.setData(bundle);
                    mHandler.sendMessage(message);
                }
            });
        }
    }

    public void copyFileByRecursion() {
        mFoldersPathMap.clear();
        if (mObjectHandles == null) {
            int[] objectHandles = mMtpDevice.getObjectHandles(mStorageId, 0, -1);
            if (objectHandles != null) {
                mObjectHandles = ArrayUtils.splitAry(objectHandles, TASK_COUNT);
            }
        }
        if (mObjectHandles != null) {
            for (int i = 0; i < TASK_COUNT; i++) {
                final int index = i;
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        long star = System.currentTimeMillis();
                        copyFileFromDevice((int[]) mObjectHandles[index], mStorageId, 0, -1);

                        Log.e(TAG, "task copyFile cost time is  " +
                                (System.currentTimeMillis() - star) / (float) 1000 + "  seconds");

                        Message message = Message.obtain();
                        Bundle bundle = new Bundle();
                        bundle.putString("TASK_NAME", Thread.currentThread().getName());
                        message.what = ACTION_COMPLETE_COPY_FILE;
                        message.setData(bundle);
                        mHandler.sendMessage(message);
                    }
                });
            }
        }
    }

    private void copyFileFromDevice(int objectHandle, int storageId, int format) {
        MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(objectHandle);
        int parentHandle;
        if (objectInfo == null) {
            Log.e(TAG, "mtpObjectInfo == null");
            return;
        }
        parentHandle = objectInfo.getParent();
        String parentFilePath = mFoldersPathMap.get(parentHandle);
        if (parentFilePath == null) {
            parentFilePath = mBasePath.toString();
            mFoldersPathMap.put(parentHandle, parentFilePath);

        }

        mFilePath.setLength(0);
        mFilePath.append(parentFilePath)
                .append(File.separator)
                .append(objectInfo.getName());

        if (objectInfo.getFormat() == MtpConstants.FORMAT_ASSOCIATION) {
            File file = new File(mFilePath.toString());
            if (!file.exists()) {
                file.mkdirs();
            }
            mFoldersPathMap.put(objectHandle, mFilePath.toString());
            Log.i(TAG, "create folder is  " + mFilePath.toString());

            int[] objectHandles = mMtpDevice.getObjectHandles(storageId, format, objectHandle);
            ArrayUtils.arrayToQueue(mObjectHandleQueue, objectHandles);
//            if (objectHandles != null) {
//                for (int handle : objectHandles) {
//                    copyFileFromDevice(handle, storageId, format, objectHandle);
//                }
//            }
        } else {
            if (Environment.getDataDirectory().getFreeSpace() > objectInfo.getCompressedSize()) {
                mMtpDevice.importFile(objectHandle, mFilePath.toString());
                Log.i(TAG, " copy file is  " + mFilePath.toString());
            } else {
                Log.e(TAG, "storage is full");
            }
        }
    }


    private void copyFileFromDevice(int[] objectHandles, int storageId, int format, int parent) {
        if (objectHandles == null) {
            objectHandles = mMtpDevice.getObjectHandles(storageId, format, parent);
            if (objectHandles == null) {
                return;
            }
        }
        for (int objectHandle : objectHandles) {
            MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(objectHandle);
            if (objectInfo == null) {
                Log.e(TAG, "mtpObjectInfo == null");
                continue;
            }
            String parentFilePath = mFoldersPathMap.get(parent);
            if (parentFilePath == null) {
                parentFilePath = mBasePath.toString();
            }

//            if (!parentFilePath.contains(OLD_PHONE_MESSAGE_FOLDER_NAME) &&
//                    !objectInfo.getName().equals(OLD_PHONE_MESSAGE_FOLDER_NAME)) {
//                continue;
//            }

            mFilePath.setLength(0);
            mFilePath.append(parentFilePath)
                    .append(File.separator)
                    .append(objectInfo.getName());
            if (objectInfo.getFormat() == MtpConstants.FORMAT_ASSOCIATION) {
                File file = new File(mFilePath.toString());
                if (!file.exists()) {
                    file.mkdirs();
                }
                mFoldersPathMap.put(objectHandle, mFilePath.toString());
                Log.i(TAG, "create folder is  " + mFilePath.toString());
                copyFileFromDevice(null, storageId, format, objectHandle);
            } else {
                if (Environment.getDataDirectory().getFreeSpace() > objectInfo.getCompressedSize()) {
                    mMtpDevice.importFile(objectHandle, mFilePath.toString());
                    Log.i(TAG, " copy file is  " + mFilePath.toString());
                } else {
                    Log.e(TAG, "storage is full");
                    return;
                }
            }
        }
    }

    /**
     * find file objectHandle by file path
     *
     * @param storageId
     * @param format
     * @param parent
     * @param path
     * @return
     */
    private int findFileFolderByPath(int[] objectHandles, int storageId, int format, int parent, String path) {
        int targetFileFolderHandle = -1;
        if (objectHandles == null) {
            objectHandles = mMtpDevice.getObjectHandles(storageId, format, parent);
            if (objectHandles == null) {
                return targetFileFolderHandle;
            }
        }
        for (int objectHandle : objectHandles) {
            if (mReadMessageFileHandle.get() != -1) {
                break;
            }
            MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(objectHandle);
            if (objectInfo == null) {
                Log.e(TAG, "mtpObjectInfo == null");
                continue;
            }
            if (MtpConstants.FORMAT_ASSOCIATION == objectInfo.getFormat()) {
                String parentPath = mFoldersPathMap.get(parent);
                if (parentPath == null) {
                    parentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    mFoldersPathMap.put(parent, parentPath);
                }

                mFilePath.setLength(0);
                mFilePath.append(parentPath)
                        .append(File.separator)
                        .append(objectInfo.getName());
                mFoldersPathMap.put(objectHandle, mFilePath.toString());

//                Log.i(TAG, " mFilePath is  " + mFilePath.toString());

                if (path.equals(mFilePath.toString())) {
                    return objectHandle;
                }
                targetFileFolderHandle = findFileFolderByPath(null, storageId, format, objectHandle, path);
                if (targetFileFolderHandle != -1) {
                    Log.i(TAG, "MessageFileFolderHandle objectHandle is " + targetFileFolderHandle);
                    return targetFileFolderHandle;
                }
            }
        }
        return targetFileFolderHandle;
    }

    /**
     * find file objectHandle by file path
     *
     * @param storageId
     * @param format
     * @param parent    parent objectHandle of target file that file name is param fileName
     * @param fileName
     * @return
     */
    private int findFileByName(int storageId, int format, int parent, String fileName) {
        int[] objectHandles = mMtpDevice.getObjectHandles(storageId, format, parent);
        if (objectHandles == null) {
            return -1;
        }
        for (int objectHandle : objectHandles) {
            MtpObjectInfo objectInfo = mMtpDevice.getObjectInfo(objectHandle);
            if (objectInfo == null) {
                Log.e(TAG, "mtpObjectInfo == null");
                continue;
            }
            if (fileName.equals(objectInfo.getName())) {
                Log.d(TAG, "mReadMessageFileHandle value is " + objectHandle);
                return objectHandle;
            }
        }
        return -1;
    }

    /**
     * (该方法会影响文件拷贝，暂不使用)
     * 手动在文件夹或是照相添加/删除文件会收到event
     * 若是应用内创建文件不会收到event，需要在从机端调用MediaScannerConnection.scanFile()方法，
     * 而且创建文件必须不能为空文件，否则也收不到EVENT
     */
    private void readEventsFromDevice() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (mIsConnected.get()) {

                    try {
                        MtpEvent event = mMtpDevice.readEvent(null);
                        Log.e(TAG, "readEventsFromDevice  " + event.getEventCode());

                        Message message = Message.obtain();
                        Bundle bundle = new Bundle();
                        if (event.getEventCode() == MtpEvent.EVENT_OBJECT_ADDED) {
                            bundle.putString("log", "EVENT_OBJECT_ADDED");
                        } else if (event.getEventCode() == MtpEvent.EVENT_OBJECT_REMOVED) {
                            bundle.putString("log", "EVENT_OBJECT_REMOVED");
                        } else {
                            bundle.putString("log", "EVENT_CODE is " + event.getEventCode());
                        }
                        message.what = Constants.ACTION_SHOW_LOG;
                        message.setData(bundle);
                        mHandler.sendMessage(message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void usbDisconnect() {
        reset();
    }

    private void reset() {
        mIsConnected.set(false);
        mIsReadingMessage.set(false);
        mIsSendingMessage.set(false);
        mMessageFileFolderHandle.set(-1);
        mReadMessageFileHandle.set(-1);
        mSendMessageFileHandle.set(-1);
        mFoldersPathMap.clear();
        mCommandQueue.clear();
        mReadOffset.set(0);
        synchronized (mLock) {
            mLock.notifyAll();
        }
    }

}
