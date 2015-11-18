package org.slimroms.wallpaper;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class HorizontalLayout extends LinearLayout {

    Context mContext;
    OnImageClickListener mOnImageClickListener;

    int mCurrent = 0;

    public HorizontalLayout(Context context) {
        super(context);

        mContext = context;
    }

    public HorizontalLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        mContext = context;
    }

    public HorizontalLayout(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);

        mContext = context;
    }

    void add(Drawable d, int i) {
        if (getChildAt(i) != null) {
            removeViewAt(i);
        }
        addView(getImageButton(d, i), i);
    }

    int getCurrent() {
        return mCurrent;
    }

    void setCurrent(int i) {
        mCurrent = i;
    }

    public void setOnImageClickListener(OnImageClickListener onImageClickListener) {
        mOnImageClickListener = onImageClickListener;
    }

    View getImageButton(Drawable d, int i) {

        View view = View.inflate(getContext(), R.layout.wallpaper_item, null);
        ImageView imageButton = (ImageView) view.findViewById(R.id.wallpaper_image);
        imageButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageButton.setId(i);
        imageButton.setImageDrawable(d);
        //imageButton.setPadding(5,0,5,0);

        imageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = v.getId();
                setCurrent(i);
                mOnImageClickListener.onImageClick(v);
            }
        });

        return view;
    }

    public interface OnImageClickListener {
        void onImageClick(View v);
    }
}
