package com.breakdash.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Fits the 1280x720 cockpit into a smaller landscape screen without clipping instruments. */
final class CockpitViewport extends ViewGroup {
    private final int designWidth, designHeight;

    CockpitViewport(Context context, View cockpit, int background) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        designWidth = Math.round(1280 * density);
        designHeight = Math.round(720 * density);
        setBackgroundColor(background);
        addView(cockpit);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(designWidth, widthSpec), resolveSize(designHeight, heightSpec));
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec(designWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(designHeight, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View cockpit = getChildAt(0);
        float scale = Math.min((float) getWidth() / designWidth, (float) getHeight() / designHeight);
        cockpit.setPivotX(0); cockpit.setPivotY(0);
        cockpit.setScaleX(scale); cockpit.setScaleY(scale);
        int x = Math.round((getWidth() - designWidth * scale) / 2);
        int y = Math.round((getHeight() - designHeight * scale) / 2);
        cockpit.layout(x, y, x + designWidth, y + designHeight);
    }
}
