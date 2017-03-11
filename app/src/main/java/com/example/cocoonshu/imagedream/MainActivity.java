package com.example.cocoonshu.imagedream;

import android.database.ContentObserver;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.cobox.coview.SlidingImage;
import com.cobox.coview.SlidingImage.BitmapLoader;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton  mFabAction       = null;
    private MediaProviderObserver mContentObserver = null;
    private SlidingImage          mSlidingImage    = null;
    private ImageLoader           mContentLoader   = null;
    private BitmapLoader          mBitmapLoader    = null;
    private List<String>          mImagePaths      = null;
    private int                   mImageCounter    = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeActionBar();
        initializeComponents();
        initializeListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerMediaProviderObserver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterMediaProviderObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mContentLoader.stop();
        mBitmapLoader.terminate();
        mSlidingImage.setBitmapLoader(null);
    }

    private void initializeActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void initializeComponents() {
        mContentLoader = new ImageLoader(getApplicationContext());
        mBitmapLoader  = new BitmapLoader(getApplicationContext());
        mFabAction     = (FloatingActionButton) findViewById(R.id.fab);
        mSlidingImage  = (SlidingImage) findViewById(R.id.SlidingImage);
        mContentLoader.start();
        mBitmapLoader.start();
        mSlidingImage.setBitmapLoader(mBitmapLoader);
    }

    private void initializeListeners() {
        mFabAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mImagePaths == null || mImagePaths.isEmpty()) {
                    Snackbar.make(view, "No photo found", Snackbar.LENGTH_LONG).show();
                } else {
                    nextImage();
                }
            }
        });

        mContentLoader.setOnLoadCompletedListener(new ImageLoader.OnLoadCompletedListener() {
            @Override
            public void OnLoadCompleted(List<String> imagePaths) {
                int imageCount = imagePaths == null ? 0 : imagePaths.size();
                mImageCounter = mImageCounter > imageCount ? imageCount - 1 : mImageCounter;
                mImageCounter = mImageCounter < 0 ? 0 : mImageCounter;
                mImagePaths   = imagePaths;
                nextImage();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void registerMediaProviderObserver() {
        mContentObserver = new MediaProviderObserver();
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mContentObserver);
    }

    private void unregisterMediaProviderObserver() {
        if (mContentObserver != null) {
            getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
    }

    private void nextImage() {
        if (mSlidingImage == null) {
            return;
        }

        List<String> imagePaths = mImagePaths;
        int          imageCount = imagePaths.size();
        if (mImageCounter < imageCount) {
            String imagePath = imagePaths.get(mImageCounter);
            mImageCounter++;
            mSlidingImage.setNextImageBitmap(imagePath);
        }
    }

    private class MediaProviderObserver extends ContentObserver {

        public MediaProviderObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mContentLoader != null) {
                mContentLoader.notifyDirty();
            }
        }
    }
}
