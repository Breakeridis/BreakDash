package com.breakdash.launcher.core;

/** A finite visual tween between measured fixes; never predicts travel beyond the newest fix. */
public final class VehicleMotion {
    public static final long DURATION_MS=700;
    public static final long FRAME_MS=50; // At most the map's existing 20 FPS budget.
    public static final class Pose {
        public final double latitude,longitude,bearing;
        public Pose(double latitude,double longitude,double bearing) {
            this.latitude=Math.max(-85.05112878,Math.min(85.05112878,latitude));
            this.longitude=wrap(longitude); this.bearing=normalize(bearing);
        }
    }
    private Pose from,to;
    private long started;
    private boolean moving;
    public void target(double latitude,double longitude,double bearing,long now,boolean snap) {
        Pose displayed=sample(now),next=new Pose(latitude,longitude,bearing);
        from=displayed==null || snap ? next : displayed; to=next; started=now;
        moving=!snap && (from.latitude!=to.latitude || from.longitude!=to.longitude || from.bearing!=to.bearing);
    }
    public Pose sample(long now) { return to==null ? null : moving ? interpolate(from,to,fraction(now-started)) : to; }
    public boolean moving(long now) { return moving && now-started<DURATION_MS; }
    public void finish() { from=to; moving=false; }
    public static double fraction(long elapsed) { return Math.max(0,Math.min(1,elapsed/(double)DURATION_MS)); }
    public static Pose interpolate(Pose from,Pose to,double fraction) {
        double t=Math.max(0,Math.min(1,fraction));
        return new Pose(lerp(from.latitude,to.latitude,t),from.longitude+wrap(to.longitude-from.longitude)*t,
                from.bearing+wrap(to.bearing-from.bearing)*t);
    }
    public static double lerp(double from,double to,double fraction) { return from+(to-from)*fraction; }
    private static double wrap(double degrees) { return ((degrees+180)%360+360)%360-180; }
    private static double normalize(double degrees) { return (degrees%360+360)%360; }
}
