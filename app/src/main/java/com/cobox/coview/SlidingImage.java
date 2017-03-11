package com.cobox.coview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.example.cocoonshu.imagedream.R;

import com.cobox.coview.SlidingImage.BitmapLoader.OnLoadedListener;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A low effect sliding image view
 * @Author Cocoonshu
 */
public class SlidingImage extends View {
    public static final String TAG = "SlidingImage";

    private enum Clamp {
        Crop   (1),
        Fit    (2),
        Inside (3);

        private int mValue = 1;

        private Clamp(int value) {
            mValue = value;
        }

        public static Clamp ValueOf(int value) {
            switch (value) {
                case 1: return Crop;
                case 2: return Fit;
                case 3: return Inside;
                default: return null;
            }
        }

        public int getValue() {
            return mValue;
        }
    }

    private Clamp               mClamp            = Clamp.Crop;
    private long                mDuration         = 1500;
    private BitmapLoader        mBitmapLoader     = null;
    private OnLoadedListener    mOnLoadedListener = null;
    private Deque<BitmapDrawer> mDrawerQueue      = new ArrayDeque<>();

    public SlidingImage(Context context) {
        this(context, null);
    }

    public SlidingImage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingImage(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SlidingImage(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        decodeAttributes(context, attrs, defStyleAttr, defStyleRes);
        initializeComponent();
    }

    private void initializeComponent() {
        mOnLoadedListener = new OnLoadedListener() {

            private int counter = 0;

            @Override
            public void onBitmapLoaded(Bitmap bitmap) {
                BitmapDrawer drawer = counter % 2 == 0
                        ? new RadialBitmapDrawer(bitmap)
                        : new LinearBitmapDrawer(bitmap, counter % 3);

                drawer.setDuration(mDuration);
                drawer.start();
                postInvalidateOnAnimation();
                counter++;

                synchronized (mDrawerQueue) {
                    mDrawerQueue.offer(drawer);
                }
            }
        };
    }

    private void decodeAttributes(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.SlidingImage, defStyleAttr, defStyleRes);
        if (array != null) {
            mClamp    = Clamp.ValueOf(array.getInt(R.styleable.SlidingImage_clamp, mClamp.getValue()));
            mDuration = array.getInteger(R.styleable.SlidingImage_duration, (int) mDuration);
            array.recycle();
        }
    }

    public void setBitmapLoader(BitmapLoader loader) {
        mBitmapLoader = loader;
        if (mBitmapLoader != null) {
            mBitmapLoader.setBitmapClamp(mClamp);
            mBitmapLoader.setBitmapSize(getWidth(), getHeight());
            mBitmapLoader.setOnLoadedListener(mOnLoadedListener);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode    = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode   = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSpec    = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSpec   = MeasureSpec.getSize(heightMeasureSpec);
        int       wantedWidth  = 0;
        int       wantedHeight = 0;
        Point     screenSize   = new Point();
        getDisplay().getSize(screenSize);

        switch (widthMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                wantedWidth = widthSpec;
                break;
            case MeasureSpec.UNSPECIFIED:
                wantedWidth = screenSize.x;
                break;
            default:
                break;
        }

        switch (heightMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                wantedHeight = heightSpec;
                break;
            case MeasureSpec.UNSPECIFIED:
                wantedHeight = screenSize.y;
                break;
            default:
                break;
        }

        setMeasuredDimension(wantedWidth, wantedHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mBitmapLoader != null) {
            mBitmapLoader.setBitmapSize(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean hasMoreFrames = false;
        synchronized (mDrawerQueue) {
            Iterator<BitmapDrawer> iterator = mDrawerQueue.iterator();
            while (iterator.hasNext()) {
                BitmapDrawer drawer = iterator.next();
                boolean hasAnimation = false;
                if (drawer != null) {
                    drawer.setBound(0, 0, getWidth(), getHeight());
                    hasAnimation = drawer.draw(canvas);
                    if (!hasAnimation && mDrawerQueue.size() > 2) {
                        iterator.remove();
                    }
                }
                hasMoreFrames |= hasAnimation;
            }
        }

        if (hasMoreFrames) {
            postInvalidateOnAnimation();
        }
    }

    public void setNextImageBitmap(String imagePath) {
        if (mBitmapLoader != null) {
            mBitmapLoader.addImagePath(imagePath);
        }
    }

    public static abstract class BitmapDrawer {
        private Bitmap       mBitmap       = null;
        private Interpolator mInterpolator = new DecelerateInterpolator(1.5f);
        private RectF        mBound        = new RectF();
        private long         mStartTime    = 0;
        private long         mProgress     = 0;
        private long         mDuration     = 500;

        public BitmapDrawer(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        public void setDuration(long duration) {
            mDuration = duration;
        }

        public void start() {
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
        }

        public void setBound(int left, int top, int right, int bottom) {
            mBound.set(left, top, right, bottom);
        }

        protected RectF getBound() {
            return mBound;
        }

        protected Bitmap getBitmap() {
            return mBitmap;
        }

        public boolean draw(Canvas canvas) {
            boolean hasMoreFrames = false;
            if (mBitmap == null) {
                return false;
            }

            mProgress     = AnimationUtils.currentAnimationTimeMillis() - mStartTime;
            hasMoreFrames = mProgress <= mDuration;
            mProgress     = mProgress > mDuration ? mDuration : mProgress < 0 ? 0 : mProgress;

            if (canvas != null) {
                float percent = mInterpolator.getInterpolation((float) mProgress / (float) mDuration);
                hasMoreFrames |= onDraw(canvas, percent);
            }

            return hasMoreFrames;
        }

        protected abstract boolean onDraw(Canvas canvas, float animationProgress);
    }

    /**
     * Radial gradient drawer
     */
    public static class RadialBitmapDrawer extends BitmapDrawer {
        public static final float CENTER_RL_POS = 0.8f;

        private Paint          mImagePaint   = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Matrix         mImageMatrix  = new Matrix();
        private Xfermode       mXfermode     = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
        private Paint          mMaskPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RadialBitmapDrawer(Bitmap bitmap) {
            super(bitmap);
        }

        public void setBound(int left, int top, int right, int bottom) {
            super.setBound(left, top, right, bottom);
            mImageMatrix.reset();

            Bitmap bitmap  = getBitmap();
            RectF  bound   = getBound();
            if (bitmap != null) {
                mImageMatrix.setScale(
                        bound.width() / (float) bitmap.getWidth(),
                        bound.height() / (float) bitmap.getHeight());
            }
        }

        @Override
        protected boolean onDraw(Canvas canvas, float animationProgress) {
            boolean hasMoreFrames = false;
            Bitmap  bitmap        = getBitmap();
            RectF   bound         = getBound();
            if (bitmap == null) {
                return false;
            }

            float centerX = bound.centerX();
            float centerY = bound.centerY();
            float radius  = (float) (animationProgress * Math.hypot(bound.width(), bound.height()) * 0.5f);

            if (canvas != null) {
                canvas.saveLayer(bound, null, Canvas.ALL_SAVE_FLAG);

                {// Draw animation and xfermode
                    mMaskPaint.setColor(0xFFFFFFFF);
                    mMaskPaint.setShader(
                            new RadialGradient(
                                    centerX, centerY, radius * (2.0f - CENTER_RL_POS),
                                    new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                                    new float[] {0.0f, CENTER_RL_POS, 1.0f}, Shader.TileMode.CLAMP));
                    canvas.drawCircle(centerX, centerY, radius * (1.0f + CENTER_RL_POS), mMaskPaint);

                    mImagePaint.setXfermode(mXfermode);
                    canvas.drawBitmap(bitmap, mImageMatrix, mImagePaint);
                    mImagePaint.setXfermode(null);
                }

                canvas.restore();
            }

            return hasMoreFrames;
        }

    }

    /**
     * Linear gradient drawer
     */
    public static class LinearBitmapDrawer extends BitmapDrawer {
        public  static final int   HORIZONTAL    = 0;
        public  static final int   VERTICAL      = 1;
        private static final float CENTER_RL_POS = 0.4f;
        private static final float LINEAR_WIDTH  = 0.2f;

        private Paint          mImagePaint   = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Matrix         mImageMatrix  = new Matrix();
        private Xfermode       mXfermode     = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
        private Paint          mMaskPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int            mDirection    = HORIZONTAL;
        private Rect           mClipRect     = new Rect();

        public LinearBitmapDrawer(Bitmap bitmap, int direction) {
            super(bitmap);
            mDirection = direction;
        }

        public void setBound(int left, int top, int right, int bottom) {
            super.setBound(left, top, right, bottom);
            mImageMatrix.reset();
            mClipRect.setEmpty();

            Bitmap bitmap = getBitmap();
            RectF  bound  = getBound();
            if (bitmap != null) {
                mImageMatrix.setScale(
                        bound.width() / (float) bitmap.getWidth(),
                        bound.height() / (float) bitmap.getHeight());
            }
        }

        @Override
        protected boolean onDraw(Canvas canvas, float animationProgress) {
            boolean hasMoreFrames = false;
            Bitmap  bitmap        = getBitmap();
            RectF   bound         = getBound();
            if (bitmap == null) {
                return false;
            }

            float width        = bound.width();
            float height       = bound.height();
            float linePosition = mDirection == HORIZONTAL
                                    ? animationProgress * (width * (1.0f + LINEAR_WIDTH)) - width * LINEAR_WIDTH * 0.5f
                                    : animationProgress * (height * (1.0f + LINEAR_WIDTH)) - height * LINEAR_WIDTH * 0.5f;
            if (canvas != null) {
                canvas.saveLayer(bound, null, Canvas.ALL_SAVE_FLAG);

                {// Draw animation and xfermode
                    mMaskPaint.setColor(0xFFFFFFFF);
                    if (mDirection == HORIZONTAL) {
                        mMaskPaint.setShader(
                                new LinearGradient(
                                        linePosition - width * LINEAR_WIDTH * 0.5f, 0.5f * height,
                                        linePosition + width * LINEAR_WIDTH * 0.5f, 0.5f * height,
                                        new int[] {0x00000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00000000},
                                        new float[] {0.0f, CENTER_RL_POS, 0.5f, 1f - CENTER_RL_POS, 1.0f},
                                        Shader.TileMode.CLAMP));
                        canvas.drawRect(
                                linePosition - width * LINEAR_WIDTH * 0.5f, 0.0f,
                                linePosition + width * LINEAR_WIDTH * 0.5f, height,
                                mMaskPaint);
                        mClipRect.set(0, 0, Math.round(linePosition + width * LINEAR_WIDTH * 0.5f), Math.round(height));
                    } else {
                        mMaskPaint.setShader(
                                new LinearGradient(
                                        0.5f * width, linePosition - height * LINEAR_WIDTH * 0.5f,
                                        0.5f * width, linePosition + height * LINEAR_WIDTH * 0.5f,
                                        new int[] {0x00000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00000000},
                                        new float[] {0.0f, CENTER_RL_POS, 0.5f, 1f - CENTER_RL_POS, 1.0f},
                                        Shader.TileMode.CLAMP));
                        canvas.drawRect(
                                0.0f, linePosition - height * LINEAR_WIDTH * 0.5f,
                                width, linePosition + height * LINEAR_WIDTH * 0.5f,
                                mMaskPaint);
                        mClipRect.set(0, 0, Math.round(width), Math.round(linePosition + height * LINEAR_WIDTH * 0.5f));
                    }

                    mImagePaint.setAlpha(255);
                    mImagePaint.setXfermode(mXfermode);
                    canvas.drawBitmap(bitmap, mImageMatrix, mImagePaint);
                    mImagePaint.setXfermode(null);

                    mImagePaint.setAlpha((int)(animationProgress * 255));
                    canvas.clipRect(mClipRect);
                    canvas.drawBitmap(bitmap, mImageMatrix, mImagePaint);
                }

                canvas.restore();
            }

            return hasMoreFrames;
        }
    }

    /**
     * Bitmap loader
     */
    public static class BitmapLoader extends Thread {
        private int              mBitmapWidth      = 0;
        private int              mBitmapHeight     = 0;
        private boolean          mIsKeepRunning    = true;
        private Clamp            mClamp            = Clamp.Crop; // TODO Implemented
        private Object           mLocker           = new Object();
        private Queue<String>    mImagePathQueue   = new LinkedBlockingQueue<>();
        private OnLoadedListener mOnLoadedListener = null;

        protected interface OnLoadedListener {
            void onBitmapLoaded(Bitmap bitmap);
        }

        public BitmapLoader(Context context) {
            if (context != null) {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                mBitmapWidth  = metrics.widthPixels;
                mBitmapHeight = metrics.heightPixels;
            }
        }

        public void setOnLoadedListener(OnLoadedListener listener) {
            mOnLoadedListener = listener;
        }

        public void addImagePath(String path) {
            if (!mIsKeepRunning || path == null || path.trim().isEmpty()) {
                return;
            }
            synchronized (mImagePathQueue) {
                mImagePathQueue.offer(path);
            }
            synchronized (mLocker) {
                mLocker.notifyAll();
            }
        }

        public void terminate() {
            mIsKeepRunning = false;
            synchronized (mImagePathQueue) {
                mImagePathQueue.clear();
            }
            synchronized (mLocker) {
                mLocker.notifyAll();
            }
        }

        @Override
        public void run() {
            while (mIsKeepRunning) {
                String path = null;
                synchronized (mImagePathQueue) {
                     path = mImagePathQueue.poll();
                }
                if (path == null) {
                    synchronized (mLocker) {
                        try {
                            mLocker.wait();
                        } catch (InterruptedException e) {
                            // Don't wanna see this stack trace
                        }
                    }
                } else {
                    if (loadBitmapIfNeed()) {
                        Bitmap bitmap = loadBitmap(path);
                        if (mOnLoadedListener != null) {
                            mOnLoadedListener.onBitmapLoaded(bitmap);
                        }
                    }
                }
            }
        }

        private boolean loadBitmapIfNeed() {
            return true;
        }

        private Bitmap loadBitmap(String path) {
            Bitmap reuseBitmap = null;
            if (mBitmapWidth != 0 && mBitmapHeight != 0) {
                BitmapRegionDecoder decoder = null;
                try {
                    decoder = BitmapRegionDecoder.newInstance(path, false);

                    Rect    decodeRect   = new Rect();
                    Options options      = new Options();
                    float   bitmapWidth  = decoder.getWidth();
                    float   bitmapHeight = decoder.getHeight();
                    float   bitmapRatio  = bitmapWidth / bitmapHeight;
                    float   rectRatio    = (float) mBitmapWidth / (float) mBitmapHeight;
                    int     sampleSize   = 1;
                    if (bitmapRatio > rectRatio) {
                        decodeRect.top    = 0;
                        decodeRect.bottom = (int) bitmapHeight;
                        decodeRect.left   = (int) ((bitmapWidth - (bitmapHeight / mBitmapHeight) * mBitmapWidth) * 0.5f);
                        decodeRect.right  = (int) ((bitmapWidth + (bitmapHeight / mBitmapHeight) * mBitmapWidth) * 0.5f);
                        sampleSize        = Math.round(bitmapHeight / (float) mBitmapHeight);
                        sampleSize        = sampleSize < 1 ? 1 : sampleSize;
                    } else {
                        decodeRect.top    = (int) ((bitmapHeight - (bitmapWidth / mBitmapWidth) * mBitmapHeight) * 0.5f);
                        decodeRect.bottom = (int) ((bitmapHeight + (bitmapWidth / mBitmapWidth) * mBitmapHeight) * 0.5f);
                        decodeRect.left   = 0;
                        decodeRect.right  = (int) bitmapWidth;
                        sampleSize        = Math.round(bitmapWidth / (float) mBitmapWidth);
                        sampleSize        = sampleSize < 1 ? 1 : sampleSize;
                    }
                    options.inSampleSize       = sampleSize;
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig  = Bitmap.Config.ARGB_8888;
                    reuseBitmap = decoder.decodeRegion(decodeRect, options);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (decoder != null) {
                        decoder.recycle();
                    }
                }
            } else {
                Options options = new Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                reuseBitmap = BitmapFactory.decodeFile(path, options);
            }

            return reuseBitmap;
        }

        private void setBitmapClamp(Clamp clamp) {
            mClamp = clamp;
        }

        public void setBitmapSize(int width, int height) {
            mBitmapWidth  = width;
            mBitmapHeight = height;
        }

    }
}
