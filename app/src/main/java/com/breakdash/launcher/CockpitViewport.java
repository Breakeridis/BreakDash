package com.breakdash.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Uniformly scales readable cockpit controls while filling the screen's actual aspect ratio. */
final class CockpitViewport extends ViewGroup {
    private final int referenceWidth,referenceHeight;
    private int designWidth,designHeight;
    private float scale=1;

    CockpitViewport(Context context, View cockpit, int background) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        referenceWidth = Math.round(1280 * density);
        referenceHeight = Math.round(720 * density);
        setBackgroundColor(background);
        addView(cockpit);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width=resolveSize(referenceWidth,widthSpec),height=resolveSize(referenceHeight,heightSpec);
        setMeasuredDimension(width,height);
        scale=Math.min((float)width/referenceWidth,(float)height/referenceHeight);
        if(scale<=0) scale=1;
        // Grow the design canvas along the longer axis instead of leaving letterbox bars.
        designWidth=Math.max(1,(int)Math.ceil(width/scale));
        designHeight=Math.max(1,(int)Math.ceil(height/scale));
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec(designWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(designHeight, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View cockpit = getChildAt(0);
        cockpit.setPivotX(0); cockpit.setPivotY(0);
        cockpit.setScaleX(scale); cockpit.setScaleY(scale);
        cockpit.layout(0,0,designWidth,designHeight);
    }
}
