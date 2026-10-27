package com.breakdash.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Gives the map the largest square between usable side columns, with no outer gutters. */
final class CockpitLayout extends ViewGroup {
    private final int minimumSideWidth;
    private int mapSize, leftWidth, rightWidth;

    CockpitLayout(Context context, View left, View map, View right) {
        super(context);
        minimumSideWidth=Math.round(260*context.getResources().getDisplayMetrics().density);
        addView(left); addView(map); addView(right);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width,height);
        mapSize=Math.min(height,Math.max(1,width-2*minimumSideWidth));
        leftWidth=Math.max(0,(width-mapSize)/2); rightWidth=Math.max(0,width-mapSize-leftWidth);
        getChildAt(0).measure(exact(leftWidth),exact(height));
        getChildAt(1).measure(exact(mapSize),exact(mapSize));
        getChildAt(2).measure(exact(rightWidth),exact(height));
    }

    private int exact(int pixels) { return MeasureSpec.makeMeasureSpec(pixels,MeasureSpec.EXACTLY); }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int height=getHeight(),mapTop=(height-mapSize)/2;
        getChildAt(0).layout(0,0,leftWidth,height);
        getChildAt(1).layout(leftWidth,mapTop,leftWidth+mapSize,mapTop+mapSize);
        getChildAt(2).layout(leftWidth+mapSize,0,leftWidth+mapSize+rightWidth,height);
    }
}
