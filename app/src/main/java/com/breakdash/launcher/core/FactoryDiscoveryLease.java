package com.breakdash.launcher.core;

import java.util.function.BooleanSupplier;

/** An accepted search owns exactly one stop, including cancellation while the start RPC is returning. */
public final class FactoryDiscoveryLease {
    public interface Commands { boolean start() throws Exception; boolean stop() throws Exception; }
    public interface Waiter { void await() throws InterruptedException; }
    public static final class Result {
        public final boolean accepted,stopConfirmed;
        Result(boolean accepted,boolean stopConfirmed) { this.accepted=accepted; this.stopConfirmed=stopConfirmed; }
    }
    private FactoryDiscoveryLease() {}
    public static Result run(Commands commands,BooleanSupplier cancelled,Waiter wait,Runnable accepted) throws Exception {
        if(cancelled.getAsBoolean()) throw new InterruptedException();
        if(!commands.start()) return new Result(false,false);
        boolean stopped=false;
        try { accepted.run(); if(!cancelled.getAsBoolean()) wait.await(); }
        finally {
            boolean interrupted=Thread.interrupted();
            try { stopped=commands.stop(); } catch(Exception | LinkageError ignored) { /* Fixed stop outcome only. */ }
            finally { if(interrupted) Thread.currentThread().interrupt(); }
        }
        return new Result(true,stopped);
    }
}
