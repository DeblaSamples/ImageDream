package com.example.cocoonshu.imagedream;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author Cocoonshu
 */
public class ImageLoader extends AsyncTask<Void, Float, Void>{

    public static final int UI_INTERVAL = 16;

    private Context                 mContext                 = null;
    private OnLoadCompletedListener mOnLoadCompletedListener = null;
    private boolean                 mIsQuit                  = false;
    private Object                  mLocker                  = new Object();

    public static interface OnLoadCompletedListener {
        void OnLoadCompleted(List<String> imagePaths);
    }

    public ImageLoader(Context context) {
        mContext = context;
    }

    public void setOnLoadCompletedListener(OnLoadCompletedListener listener) {
        mOnLoadCompletedListener = listener;
    }

    public void start() {
        this.execute();
    }

    public void stop() {
        mIsQuit = true;
        notifyDirty();
    }

    public void notifyDirty() {
        synchronized (mLocker) {
            mLocker.notifyAll();
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        while (!mIsQuit) {
            List<String> result = scanMediaProvider();
            if (mOnLoadCompletedListener != null) {
                mOnLoadCompletedListener.OnLoadCompleted(result);
            }

            try {
                synchronized (mLocker) {
                    mLocker.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private List<String> scanMediaProvider() {
        if (mContext == null) {
            return new ArrayList<>();
        }

        List<String>    result   = new ArrayList<>();
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.Images.ImageColumns.DATA},
                MediaStore.Images.ImageColumns.MIME_TYPE + " IN (?, ?, ?, ?) ",
                new String[] {"image/jpg", "image/jpe", "image/jpeg", "image/png"},
                MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC ");
        if (cursor != null) {
            try {
                int  indexData      = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                int  cursorIterator = 0;
                int  cursorCount    = cursor.getCount();
                long lastTime       = System.currentTimeMillis();

                publishProgress(0.0f);
                while (cursor.moveToNext()) {
                    String data = cursor.getString(indexData);
                    result.add(data);
                    cursorIterator++;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTime > UI_INTERVAL) {
                        publishProgress((float) cursorIterator / (float) cursorCount);
                    }
                }
            } catch (Throwable thr) {
                thr.printStackTrace();
            } finally {
                cursor.close();
                publishProgress(1.0f);
            }
        }
        return result;
    }
}
