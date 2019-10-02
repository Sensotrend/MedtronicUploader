package info.nightscout.android.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

public class TrimmingImageView extends AppCompatImageView {
    public TrimmingImageView(Context context) {
        this(context, null);
    }

    public TrimmingImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int intrinsicHeight;
        final int intrinsicWidth;
        final float aspectRatio;
        Drawable image = getDrawable();
        if (image != null) {
            intrinsicHeight = image.getIntrinsicHeight();
            intrinsicWidth = image.getIntrinsicWidth();
            if (intrinsicHeight > 0 && intrinsicWidth > 0) {
                aspectRatio = (float) intrinsicHeight / (float) intrinsicWidth;
            } else {
                return;
            }
        } else {
            return;
        }

        if (aspectRatio < 0.00001f) {
            return;
        }

        final int measuredWidth = getMeasuredWidth();
        final int measuredHeight = getMeasuredHeight();

        if (intrinsicWidth <= measuredWidth && intrinsicHeight <= measuredHeight) {
            return;
        }

        final float ratioByHeight = (float) intrinsicHeight / (float) measuredHeight;
        final float ratioByWidth = (float) intrinsicWidth / (float) measuredWidth;

        if (ratioByHeight >= ratioByWidth) {
            setMeasuredDimension(
                    (int) (intrinsicWidth / ratioByHeight),
                    measuredHeight
            );
        } else {
            setMeasuredDimension(
                    measuredWidth,
                    (int) (intrinsicHeight / ratioByWidth)
            );
        }
    }
}
