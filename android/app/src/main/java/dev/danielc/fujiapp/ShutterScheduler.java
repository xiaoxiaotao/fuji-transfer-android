package dev.danielc.fujiapp;

/** One deliberate user request produces at most one command; no retry after uncertain results. */
final class ShutterScheduler {
    private boolean ready, pending, sending;
    private long deadline, cooldown;
    synchronized void reset() { ready=false; pending=false; sending=false; cooldown=0; }
    synchronized void frameReady() { ready=true; }
    synchronized boolean request(int seconds,long now) {
        if(!canRequest(now))return false;
        deadline=now+Math.max(0,seconds)*1000L;pending=true;return true;
    }
    synchronized boolean take(long now) {
        if(!pending||now<deadline)return false;
        pending=false;sending=true;return true;
    }
    synchronized void complete(long now) { sending=false;cooldown=now+1500; }
    synchronized void cancel() { pending=false;ready=false; }
    synchronized boolean canRequest(long now) { return ready&&!pending&&!sending&&now>=cooldown; }
    synchronized boolean pending() { return pending; }
    synchronized int secondsLeft(long now) { return pending?(int)Math.max(0,(deadline-now+999)/1000):0; }
}
