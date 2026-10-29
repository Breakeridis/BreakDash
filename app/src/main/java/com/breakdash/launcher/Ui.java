package com.breakdash.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
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
import android.widget.Switch;
import android.widget.TextView;
import java.util.function.Consumer;
import com.breakdash.launcher.core.ThemePalette;

final class Ui {
    final Context context;
    final boolean light;
    final ThemePalette palette;
    final int bg,card,border,text,muted,accent,amber,green,danger,navFill,radioFill;
    static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    Ui(Context context,boolean light) {
        this.context=context; this.light=light;
        palette=new ThemePalette(light); bg=palette.background; card=palette.surface; border=palette.outline;
        text=palette.text; muted=palette.secondary; accent=palette.navigation; amber=palette.radio; green=palette.active;
        danger=palette.danger; navFill=palette.navigationFill; radioFill=palette.radioFill;
    }
    int dp(float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    LinearLayout row() { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    TextView text(String value,int size,int color) {
        TextView view=new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        if(size>=24) view.setTypeface(MEDIUM); return view;
    }
    TextView label(String value) {
        TextView view=text(value,18,muted); view.setLetterSpacing(.03f); view.setTypeface(MEDIUM); return view;
    }
    GradientDrawable surface(int fill,int stroke,int radius) {
        return surface(fill,stroke,radius,2);
    }
    GradientDrawable surface(int fill,int stroke,int radius,int width) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(width),stroke); return drawable;
    }
    RippleDrawable controlSurface(int fill,int stroke,int width,boolean filled) {
        return new RippleDrawable(ColorStateList.valueOf(filled ? 0x1A000000 : palette.neutralRipple()),surface(fill,stroke,14,width),null);
    }
    void neutral(Button view) { view.setTextColor(text); view.setBackground(controlSurface(card,border,2,false)); }
    void primary(Button view) { view.setTextColor(ThemePalette.ON_FILLED); view.setBackground(controlSurface(navFill,accent,2,true)); }
    void selected(Button view,boolean selected) { if(view.isSelected()==selected) return; view.setSelected(selected); if(selected) primary(view); else neutral(view); }
    void radioPreset(Button view,boolean selected) {
        if(view.isSelected()==selected) return;
        view.setSelected(selected); view.setTextColor(selected ? ThemePalette.ON_FILLED : text);
        view.setBackground(controlSurface(selected ? radioFill : card,selected ? amber : border,selected ? 3 : 2,selected));
    }
    void danger(Button view) { view.setTextColor(danger); view.setBackground(controlSurface(card,danger,2,false)); }
    Button button(String value,Runnable click) {
        Button view=new Button(context); view.setText(value); view.setAllCaps(false); view.setTextSize(20); view.setTextColor(text); view.setTypeface(MEDIUM);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(64)); view.setMinimumHeight(dp(64));
        view.setPadding(dp(8),dp(6),dp(8),dp(6)); view.setStateListAnimator(null); view.setBackgroundTintList(null); neutral(view);
        view.setOnClickListener(v->click.run()); return view;
    }
    Button mapButton(String value,Runnable click) {
        Button view=button(value,click); view.setTextSize(18);
        view.setMinHeight(dp(64)); view.setMinimumHeight(dp(64));
        view.setPadding(dp(8),dp(4),dp(8),dp(4)); return view;
    }
    final class ToggleRow extends LinearLayout {
        private final Switch control;
        private final TextView status;
        private final String title;
        private boolean syncing;
        ToggleRow(String title,Consumer<Boolean> changed) {
            super(Ui.this.context); this.title=title; setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(80)); setPadding(dp(16),dp(8),dp(12),dp(8)); setBackground(surface(card,border,16));
            LinearLayout labels=column(); TextView name=text(title,20,text); name.setTypeface(Typeface.DEFAULT,Typeface.BOLD); labels.addView(name);
            status=text("",18,muted); status.setPadding(0,dp(5),0,0); labels.addView(status); addView(labels,new LayoutParams(0,-2,1));
            control=new Switch(context); control.setShowText(false); control.setMinHeight(dp(64)); control.setSwitchMinWidth(dp(58));
            int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};
            int thumb=light ? ThemePalette.ON_FILLED : bg;
            control.setThumbTintList(ColorStateList.valueOf(thumb));
            control.setTrackTintList(new ColorStateList(states,new int[]{green,border}));
            control.setPadding(dp(16),0,0,0); addView(control,new LayoutParams(dp(92),dp(64)));
            control.setOnCheckedChangeListener((button,checked)->{ if(!syncing && changed!=null) changed.accept(checked); });
            setOnClickListener(v->{ if(control.isEnabled()) control.performClick(); });
            if(changed==null) { control.setClickable(false); control.setFocusable(false); setClickable(false); }
        }
        void state(boolean checked,String label,boolean interactive) {
            syncing=true; control.setChecked(checked); syncing=false; status.setText(label); status.setTextColor(checked ? green : muted);
            control.setEnabled(interactive); setClickable(interactive); control.setContentDescription(title+", "+label);
        }
    }
    ToggleRow toggle(LinearLayout parent,String title,boolean checked,String status,Consumer<Boolean> changed) {
        ToggleRow row=new ToggleRow(title,changed); row.state(checked,status,changed!=null);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(8); parent.addView(row,params); return row;
    }
    LinearLayout actionTile(String title,String icon,Runnable click) {
        return actionTile(title,icon,18,click);
    }
    LinearLayout actionTile(String title,String icon,int labelSize,Runnable click) {
        LinearLayout tile=column(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4),dp(8),dp(4),dp(8));
        tile.setBackground(controlSurface(card,border,2,false));
        tile.addView(glyph(icon,text),new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView caption=text(title,labelSize,text); caption.setTypeface(MEDIUM); caption.setGravity(Gravity.CENTER); caption.setMaxLines(2); caption.setEllipsize(android.text.TextUtils.TruncateAt.END); caption.setPadding(0,dp(6),0,0); tile.addView(caption);
        tile.setContentDescription(title); tile.setFocusable(true); tile.setOnClickListener(v->click.run()); return tile;
    }
    EditText input(String hint) {
        EditText view=new EditText(context); view.setTextColor(text); view.setHintTextColor(muted); view.setHint(hint); view.setTextSize(18);
        view.setSingleLine(true); view.setMinHeight(dp(64)); view.setPadding(dp(16),dp(8),dp(16),dp(8)); view.setBackgroundTintList(null); view.setBackground(surface(card,border,12)); return view;
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
            paint.setColor(color); paint.setStrokeWidth(2.3f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            switch(name) {
                case "dvr": canvas.drawRoundRect(3,6,16,19,3,3,paint); canvas.drawCircle(9.5f,12.5f,3,paint); line(canvas,16,10,21,7); line(canvas,21,7,21,18); line(canvas,21,18,16,15); break;
                case "projection": canvas.drawRoundRect(5,2,19,22,3,3,paint); line(canvas,10,19,14,19); line(canvas,9,8,12,5); line(canvas,12,5,15,8); line(canvas,12,5,12,13); break;
                case "nav": path.reset(); path.moveTo(12,2); path.lineTo(21,21); path.lineTo(12,17); path.lineTo(3,21); path.close(); canvas.drawPath(path,paint); break;
                case "music": line(canvas,8,17,8,5); line(canvas,8,5,19,3); line(canvas,19,3,19,15); canvas.drawOval(2,15,8,21,paint); canvas.drawOval(13,13,19,19,paint); break;
                case "apps": for(int x=4;x<21;x+=8) for(int y=4;y<21;y+=8) canvas.drawRoundRect(x-2,y-2,x+2,y+2,1,1,paint); break;
                case "settings": canvas.drawCircle(12,12,4,paint); for(int i=0;i<8;i++){ double a=i*Math.PI/4; line(canvas,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10); } canvas.drawCircle(12,12,8,paint); break;
                case "search": canvas.drawCircle(10,10,6,paint); line(canvas,15,15,21,21); break;
                case "updates": line(canvas,12,3,12,16); line(canvas,7,11,12,16); line(canvas,17,11,12,16); line(canvas,4,18,4,21); line(canvas,4,21,20,21); line(canvas,20,21,20,18); break;
                default: canvas.drawCircle(12,12,9,paint); line(canvas,12,11,12,17); canvas.drawPoint(12,7,paint); break;
            }
            canvas.restore();
        }
        private void line(Canvas canvas,float x,float y,float a,float b) { canvas.drawLine(x,y,a,b,paint); }
    }
}
