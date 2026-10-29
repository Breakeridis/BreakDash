package com.breakdash.launcher.core;

/** Semantic, opaque colours shared by native controls and map overlays. */
public final class ThemePalette {
    public final boolean light;
    public final int background,surface,outline,text,secondary,navigation,navigationFill,radio,radioFill,active,danger;
    public static final int ON_FILLED=0xFFFFFFFF;
    public ThemePalette(boolean light) {
        this.light=light;
        background=light ? 0xFFFFFFFF : 0xFF080A0D; surface=light ? 0xFFE4E9EF : 0xFF20252C;
        outline=light ? 0xFF536170 : 0xFF929BA6; text=light ? 0xFF101820 : 0xFFFFFFFF;
        secondary=light ? 0xFF344150 : 0xFFCBD2D9;
        navigation=light ? 0xFF00408F : 0xFF75B7FF; navigationFill=light ? 0xFF00408F : 0xFF173E69;
        radio=light ? 0xFF623800 : 0xFFFFD166; radioFill=light ? 0xFF623800 : 0xFF3D2C0A;
        active=light ? 0xFF075025 : 0xFF7DE2A0; danger=light ? 0xFF8B1111 : 0xFFFFAAA5;
    }
    public int neutralRipple() { return light ? 0x1AFFFFFF : 0x1A000000; }
    public static double contrast(int a,int b) { return (Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05); }
    private static double luminance(int color) { return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255); }
    private static double linear(int channel) { double value=channel/255.0; return value<=.04045 ? value/12.92 : Math.pow((value+.055)/1.055,2.4); }
}
