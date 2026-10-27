package com.breakdash.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    final Context context;
    final boolean light;
    final int bg,card,border,text,muted,accent,amber;
    Ui(Context context,boolean light) {
        this.context=context; this.light=light;
        bg=Color.parseColor(light ? "#EEF3F5" : "#0A0E17"); card=Color.parseColor(light ? "#FFFFFF" : "#111A26");
        border=Color.parseColor(light ? "#CDDAE0" : "#23303F"); text=Color.parseColor(light ? "#12232E" : "#F1F6FA");
        muted=Color.parseColor(light ? "#506572" : "#91A4B5"); accent=Color.parseColor(light ? "#007C76" : "#42D6CB"); amber=Color.parseColor(light ? "#946006" : "#F2B86B");
    }
    int dp(float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    LinearLayout row() { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    TextView text(String value,int size,int color) {
        TextView view=new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false); return view;
    }
    TextView label(String value) {
        TextView view=text(value,12,muted); view.setLetterSpacing(.14f); view.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return view;
    }
    GradientDrawable surface(int fill,int stroke,int radius) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1),stroke); return drawable;
    }
    Button button(String value,Runnable click) {
        Button view=new Button(context); view.setText(value); view.setAllCaps(false); view.setTextSize(15); view.setTextColor(text);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(64)); view.setMinimumHeight(dp(64));
        view.setPadding(dp(12),dp(8),dp(12),dp(8)); view.setStateListAnimator(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x22007C76 : 0x3342D6CB),surface(card,border,16),null));
        view.setOnClickListener(v->click.run()); return view;
    }
    Button mapButton(String value,Runnable click) {
        Button view=button(value,click); view.setTextSize(13);
        view.setMinHeight(dp(60)); view.setMinimumHeight(dp(60));
        view.setPadding(dp(8),dp(4),dp(8),dp(4)); return view;
    }
    EditText input(String hint) {
        EditText view=new EditText(context); view.setTextColor(text); view.setHintTextColor(muted); view.setHint(hint); view.setTextSize(18);
        view.setSingleLine(true); view.setMinHeight(dp(64)); view.setPadding(dp(16),dp(8),dp(16),dp(8)); view.setBackgroundTintList(ColorStateList.valueOf(accent)); return view;
    }
    void gap(LinearLayout layout,int height) { layout.addView(new View(context),new LinearLayout.LayoutParams(1,dp(height))); }
    void fill(LinearLayout layout) { layout.addView(new View(context),new LinearLayout.LayoutParams(1,0,1)); }
    void weighted(LinearLayout row,View child,int height) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(height),1); params.setMargins(dp(3),dp(3),dp(3),dp(3)); row.addView(child,params);
    }
    void heading(LinearLayout layout,String title) { gap(layout,16); layout.addView(label(title)); gap(layout,10); }
    Glyph glyph(String name,int color) { return new Glyph(context,name,color); }

    final class Glyph extends View {
        final String name; final int color; final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); final Path path=new Path();
        Glyph(Context context,String name,int color) { super(context); this.name=name; this.color=color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save(); float scale=Math.min(getWidth(),getHeight())/24f; canvas.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2); canvas.scale(scale,scale);
            paint.setColor(color); paint.setStrokeWidth(1.6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            switch(name) {
                case "dvr": canvas.drawRoundRect(3,6,16,19,3,3,paint); canvas.drawCircle(9.5f,12.5f,3,paint); line(canvas,16,10,21,7); line(canvas,21,7,21,18); line(canvas,21,18,16,15); break;
                case "projection": canvas.drawRoundRect(5,2,19,22,3,3,paint); line(canvas,10,19,14,19); line(canvas,9,8,12,5); line(canvas,12,5,15,8); line(canvas,12,5,12,13); break;
                case "nav": path.reset(); path.moveTo(12,2); path.lineTo(21,21); path.lineTo(12,17); path.lineTo(3,21); path.close(); canvas.drawPath(path,paint); break;
                case "music": line(canvas,8,17,8,5); line(canvas,8,5,19,3); line(canvas,19,3,19,15); canvas.drawOval(2,15,8,21,paint); canvas.drawOval(13,13,19,19,paint); break;
                case "apps": for(int x=4;x<21;x+=8) for(int y=4;y<21;y+=8) canvas.drawRoundRect(x-2,y-2,x+2,y+2,1,1,paint); break;
                case "settings": canvas.drawCircle(12,12,4,paint); for(int i=0;i<8;i++){ double a=i*Math.PI/4; line(canvas,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10); } canvas.drawCircle(12,12,8,paint); break;
                case "search": canvas.drawCircle(10,10,6,paint); line(canvas,15,15,21,21); break;
                default: canvas.drawCircle(12,12,9,paint); line(canvas,12,11,12,17); canvas.drawPoint(12,7,paint); break;
            }
            canvas.restore();
        }
        private void line(Canvas canvas,float x,float y,float a,float b) { canvas.drawLine(x,y,a,b,paint); }
    }
}
