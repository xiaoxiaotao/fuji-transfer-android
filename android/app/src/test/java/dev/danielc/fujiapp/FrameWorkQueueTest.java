package dev.danielc.fujiapp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FrameWorkQueueTest {
    @Test public void rapidChangesOnlyRunTheNewestPendingPreviewAndNeverOnCallerThread()throws Exception{
        FrameWorkQueue queue=new FrameWorkQueue("frame-test");Thread caller=Thread.currentThread();List<String> events=Collections.synchronizedList(new ArrayList<>());CountDownLatch started=new CountDownLatch(1),resume=new CountDownLatch(1),finished=new CountDownLatch(1);
        try{queue.latest(()->{assertNotSame(caller,Thread.currentThread());events.add("first");started.countDown();try{resume.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new AssertionError(e);}});assertTrue(started.await(3,TimeUnit.SECONDS));
            for(int i=0;i<30;i++)queue.latest(()->events.add("obsolete"));queue.latest(()->events.add("latest"));queue.finish(()->{events.add("release");finished.countDown();});resume.countDown();assertTrue(finished.await(3,TimeUnit.SECONDS));assertEquals(Arrays.asList("first","latest","release"),events);
        }finally{resume.countDown();queue.finish(()->{});}
    }
    @Test public void bitmapReleaseWaitsForTheRunningPreviewAndIsNotCancelledByNewOptions()throws Exception{
        FrameWorkQueue queue=new FrameWorkQueue("frame-lifetime-test");List<String> events=Collections.synchronizedList(new ArrayList<>());CountDownLatch started=new CountDownLatch(1),resume=new CountDownLatch(1),finished=new CountDownLatch(1);
        try{queue.latest(()->{started.countDown();try{resume.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new AssertionError(e);}events.add("old preview done");});assertTrue(started.await(3,TimeUnit.SECONDS));queue.latest(()->events.add("obsolete"));queue.execute(()->events.add("old photo released"));queue.latest(()->events.add("new photo rendered"));queue.finish(finished::countDown);resume.countDown();assertTrue(finished.await(3,TimeUnit.SECONDS));assertEquals(Arrays.asList("old preview done","old photo released","new photo rendered"),events);
        }finally{resume.countDown();queue.finish(()->{});}
    }
}
