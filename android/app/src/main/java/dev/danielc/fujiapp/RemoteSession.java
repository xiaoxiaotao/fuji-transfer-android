package dev.danielc.fujiapp;
import java.util.concurrent.atomic.AtomicBoolean;
/** Hold ownership until camera mode restoration finishes, including after the page closes. */
final class RemoteSession {
    private static final AtomicBoolean owned=new AtomicBoolean();
    static boolean isBusy(){return owned.get();}
    static boolean acquire(){return owned.compareAndSet(false,true);}
    static void release(){owned.set(false);}
}
