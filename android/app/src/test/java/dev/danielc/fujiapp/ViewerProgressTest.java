package dev.danielc.fujiapp;
import org.junit.Test;
import static org.junit.Assert.*;
public class ViewerProgressTest {
 @Test public void interleavedCallbacksKeepPercentageAndSpeedTogether(){ViewerProgress progress=new ViewerProgress();progress.progress(25);progress.speed(2048);assertEquals("正在读取原文件 · 25% · 2.0 MB/s",progress.caption());progress.progress(31);assertEquals("正在读取原文件 · 31% · 2.0 MB/s",progress.caption());progress.speed(3072);assertEquals("正在读取原文件 · 31% · 3.0 MB/s",progress.caption());}
 @Test public void stalePercentageCannotRegressAndNewReadResets(){ViewerProgress progress=new ViewerProgress();progress.progress(70);progress.progress(40);assertTrue(progress.caption().contains("70%"));progress.progress(120);assertTrue(progress.caption().contains("100%"));progress.speed(1024);progress.reset();assertEquals("正在读取原文件 · 0%",progress.caption());}
}
