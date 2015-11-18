/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.slimroms.wallpaper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class SlimWallpaper extends Activity {

    private static final int[] WALLPAPER_ARRAYS = {
            R.array.wallpapers,
            R.array.extra_wallpapers
    };

    protected static final float WALLPAPER_SCREENS_SPAN = 2f;

    private HorizontalLayout mLayout;
    private ImageView mImageView;
    private boolean mIsWallpaperSet;

    private Bitmap mBitmap;

    private ArrayList<String> mImages = new ArrayList<>();
    private HashMap<String, Bitmap> mWallpapers = new HashMap<>();
    private static WallpaperLoader mLoader;

    Executor mExecutor = Executors.newFixedThreadPool(2);

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        new ImageResizer().executeOnExecutor(mExecutor);

        setContentView(R.layout.wallpaper_chooser);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
            actionBar.getCustomView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectWallpaper();
                }
            });
        }

        mLayout = (HorizontalLayout) findViewById(R.id.gallery);
        mLayout.setOnImageClickListener(new HorizontalLayout.OnImageClickListener() {
            @Override
            public void onImageClick(View v) {
                imageClicked(v.getId());
            }
        });
        mImageView = (ImageView) findViewById(R.id.wallpaper);
        imageClicked(0);
    }

    private void imageClicked(int i) {
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel();
        }
        mLoader = (WallpaperLoader) new WallpaperLoader().executeOnExecutor(mExecutor, i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.apply, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                try {
                    saveWallpaper(mLayout.getCurrent());
                } catch (IOException e) {
                    e.printStackTrace();
                    makeToast("Failed to make folder");
                }
                break;
        }
        return true;
    }

    private void makeToast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    private void saveWallpaper(int position) throws IOException {
        File folder = new File(Environment.getExternalStorageDirectory() + "/Slim/wallpapers");
        String file = folder + "/" + getResources().getStringArray(R.array.wallpapers)[position]
                + ".png";
        String toastText;
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                throw new IOException("Failed to make folder " + folder);
            }
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            mBitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (new File(file).exists()) {
            toastText = "Wallpaper saved to " + file;
        } else {
            toastText = "Failed to save wallpaper.";
        }
        makeToast(toastText);
    }

    private Point getWallpaperSize() {
        // Uses suggested size if available
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        int suggestedWidth = wallpaperManager.getDesiredMinimumWidth();
        int suggestedHeight = wallpaperManager.getDesiredMinimumHeight();
        if (suggestedWidth != 0 && suggestedHeight != 0) {
            return new Point(suggestedWidth, suggestedHeight);
        }

        // Else, calculate desired size from screen size
        Point realSize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(realSize);
        int maxDim = Math.max(realSize.x, realSize.y);
        int minDim = Math.min(realSize.x, realSize.y);

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended
        // parallax effects
        final int defaultWidth, defaultHeight;
        if (isScreenLarge()) {
            defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            defaultHeight = maxDim;
        } else {
            defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
            defaultHeight = maxDim;
        }
        return new Point(defaultWidth, defaultHeight);
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    protected float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    protected boolean isScreenLarge() {
        Configuration config = getResources().getConfiguration();
        return config.smallestScreenWidthDp >= 720;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsWallpaperSet = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel(true);
            mLoader = null;
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        mWallpapers.clear();
    }

    /*
     * When using touch if you tap an image it triggers both the onItemClick and
     * the onTouchEvent causing the wallpaper to be set twice. Ensure we only
     * set the wallpaper once.
     */
    private void selectWallpaper() {
        if (mIsWallpaperSet) {
            return;
        }

        mIsWallpaperSet = true;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(getApplicationContext());
            wm.setBitmap(mBitmap);
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            Log.e("Paperless System", "Failed to set wallpaper: " + e);
        }
    }

    private class ThumbInfo {
        Drawable thumb;
        int pos;

        ThumbInfo update(Drawable d, int p) {
            thumb = d;
            pos = p;
            return this;
        }
    }

    class ImageResizer extends AsyncTask<Integer, ThumbInfo, ArrayList<Drawable>> {

        protected ArrayList<Drawable> doInBackground(Integer... v) {
            if (isCancelled()) return null;
            for (int arrayId : WALLPAPER_ARRAYS) {
                final String[] extras = getResources().getStringArray(arrayId);
                ThumbInfo info = new ThumbInfo();
                for (int i = 0; i < extras.length; i++) {
                    int res = getResources().getIdentifier(extras[i], "drawable", getPackageName());
                    if (res != 0) {
                        mImages.add(extras[i]);
                        Bitmap bitmap = getBitmapFromAssets(extras[i]);
                        Drawable d = ImageHelper.resize(getApplicationContext(),
                               new BitmapDrawable(getResources(), bitmap), 75);
                        publishProgress(info.update(d, i));
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(ThumbInfo... i) {
            mLayout.add(i[0].thumb, i[0].pos);
        }
    }

    class WallpaperLoader extends AsyncTask<Integer, Void, Bitmap> {

        BitmapFactory.Options mOptions;
        Point mSize = getWallpaperSize();

        WallpaperLoader() {
            mOptions = new BitmapFactory.Options();
            mOptions.inDither = false;
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            mOptions.inJustDecodeBounds = true;
            mOptions.inSampleSize = calculateInSampleSize(mOptions, mSize.x, mSize.y);
        }

        protected Bitmap doInBackground(Integer... params) {
            if (isCancelled()) return null;

            String name;
            if (params[0] == 0) {
                name = getResources().getStringArray(R.array.wallpapers)[0];
            } else {
                name = mImages.get(params[0]);
            }
            Bitmap b;
            if (mWallpapers.containsKey(name)) {
                b = mWallpapers.get(name);
            } else {
                b = getBitmapFromAssets(name);
                mWallpapers.put(name, b);
            }
            Point size = getWallpaperSize();
            return ImageHelper.resize(getApplicationContext(), b, size.x, size.y);
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) return;

            if (!isCancelled() && !mOptions.mCancel) {
                // Help the GC
                if (mBitmap != null) {
                    mBitmap.recycle();
                }
                mBitmap = Bitmap.createBitmap(b);

                mImageView.setImageBitmap(b);

                mLoader = null;
            } else {
                b.recycle();
            }
        }

        void cancel() {
            mOptions.requestCancelDecode();
            super.cancel(true);
        }
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private Bitmap getBitmapFromAssets(String name) {
        AssetManager assetManager = getAssets();

        InputStream is;
        Bitmap bitmap = null;
        try {
            is = assetManager.open("wallpapers/" + name + ".png");
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}