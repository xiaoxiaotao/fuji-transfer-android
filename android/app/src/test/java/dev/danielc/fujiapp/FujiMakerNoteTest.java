package dev.danielc.fujiapp;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FujiMakerNoteTest {
 private byte[] fixture(boolean big,int[][] tags){
  int makerLength=12+2+tags.length*12+4+8;ByteBuffer b=ByteBuffer.allocate(6+44+makerLength).order(big?ByteOrder.BIG_ENDIAN:ByteOrder.LITTLE_ENDIAN);
  b.put(new byte[]{'E','x','i','f',0,0});b.put((byte)(big?'M':'I')).put((byte)(big?'M':'I')).putShort((short)42).putInt(8);
  b.putShort((short)1).putShort((short)0x8769).putShort((short)4).putInt(1).putInt(26).putInt(0);
  b.putShort((short)1).putShort((short)0x927c).putShort((short)7).putInt(makerLength).putInt(44).putInt(0);
  int maker=b.position();b.order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{'F','U','J','I','F','I','L','M'}).putInt(12).putShort((short)tags.length);
  for(int[] tag:tags){b.putShort((short)tag[0]).putShort((short)tag[1]).putInt(tag.length==4?2:1);if(tag.length==4)b.putInt(makerLength-8);else if(tag[1]==3)b.putShort((short)tag[2]).putShort((short)0);else b.putInt(tag[2]);}
  b.putInt(0);for(int[] t:tags)if(t.length==4){b.position(maker+makerLength-8);b.putInt(t[2]).putInt(t[3]);}return b.array();
 }
 private FujiMakerNote parse(int[]... tags){return FujiMakerNote.parseExif(fixture(false,tags));}
 private byte[] jpeg(byte[] exif)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);d.writeShort(0xffd8);d.writeShort(0xffe1);d.writeShort(exif.length+2);d.write(exif);d.writeShort(0xffda);return bytes.toByteArray();}
 @Test public void readsBothTiffOrdersWithLittleEndianFujiDirectory(){for(boolean big:new boolean[]{true,false})assertEquals("Reala ACE",FujiMakerNote.parseExif(fixture(big,new int[][]{{0x1401,3,0xb00}})).film());}
 @Test public void acrosAndMonochromeOverrideColorFilmMode(){assertEquals("Acros + Ye",parse(new int[]{0x1401,3,0},new int[]{0x1003,3,0x502}).film());assertEquals("Monochrome + G",parse(new int[]{0x1401,3,0},new int[]{0x1003,3,0x303}).film());assertEquals("Sepia",parse(new int[]{0x1003,3,0x310}).film());}
 @Test public void recognizesLatestAndClassicSimulations(){assertEquals("Velvia",parse(new int[]{0x1401,3,0x200}).film());assertEquals("Classic Chrome",parse(new int[]{0x1401,3,0x600}).film());assertEquals("Nostalgic Negative",parse(new int[]{0x1401,3,0xa00}).film());assertEquals("Eterna Bleach Bypass",parse(new int[]{0x1401,3,0x900}).film());}
 @Test public void decodesSignedFractionalTonesAndClarity(){FujiMakerNote r=parse(new int[]{0x1041,9,-24},new int[]{0x1040,9,32},new int[]{0x100f,9,-3000});assertEquals("+1.5",r.settings.get("highlight"));assertEquals("-2",r.settings.get("shadow"));assertEquals("-3",r.settings.get("clarity"));}
 @Test public void preservesWbRawScaleWithoutGuessingCameraSteps(){FujiMakerNote r=parse(new int[]{0x100a,9,40,-60});assertEquals("R +40 / B -60（MakerNote 原始单位）",r.settings.get("wbShift"));}
 @Test public void decodesEffectsAndColor(){FujiMakerNote r=parse(new int[]{0x100e,3,0x2e0},new int[]{0x1047,9,32},new int[]{0x104c,3,16},new int[]{0x1048,9,64},new int[]{0x1003,3,0x4c0});assertEquals("-4",r.settings.get("noiseReduction"));assertEquals("弱 / 小",r.settings.get("grain"));assertEquals("强",r.settings.get("colorChrome"));assertEquals("-3",r.settings.get("color"));}
 @Test public void missingSettingsStayMissingAndUnknownFilmIsExplicit(){FujiMakerNote r=parse(new int[]{0x1401,3,0xffff});assertTrue(r.present);assertEquals("",r.film());assertFalse(r.settings.containsKey("noiseReduction"));assertEquals(1,r.unknown.size());}
 @Test public void acceptsJpegAndStopsAtScanWithoutPixelSearch()throws Exception{byte[] exif=fixture(false,new int[][]{{0x1401,3,0x600}});assertEquals("Classic Chrome",FujiMakerNote.read(new ByteArrayInputStream(jpeg(exif))).film());ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{(byte)255,(byte)216,(byte)255,(byte)218});b.write(jpeg(exif));assertFalse(FujiMakerNote.read(new ByteArrayInputStream(b.toByteArray())).present);}
 @Test public void truncatedSegmentsAndNonJpegFailSafely()throws Exception{byte[] valid=jpeg(fixture(false,new int[][]{{0x1401,3,0x200}}));for(int n=0;n<valid.length-2;n++)assertFalse(FujiMakerNote.read(new ByteArrayInputStream(java.util.Arrays.copyOf(valid,n))).present);assertFalse(FujiMakerNote.read(new ByteArrayInputStream("FUJIFILM".getBytes())).present);}
 @Test public void maliciousOffsetAndWrongSignatureFailSafely(){byte[] b=fixture(false,new int[][]{{0x1401,3,0x200}});ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(42,Integer.MAX_VALUE);assertFalse(FujiMakerNote.parseExif(b).present);b=fixture(false,new int[][]{{0x1401,3,0x200}});b[50]='X';assertFalse(FujiMakerNote.parseExif(b).present);for(int n=0;n<14;n++)assertFalse(FujiMakerNote.parseExif(new byte[n]).present);}
 @Test public void declaredMakerNoteBoundaryCannotReadOtherExifData(){byte[] b=fixture(false,new int[][]{{0x1401,3,0x200}});ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(38,12);assertFalse(FujiMakerNote.parseExif(b).present);}
 @Test public void dynamicRangeDoesNotInventMissingPercent(){assertEquals("标准（MakerNote）",parse(new int[]{0x1400,3,1}).settings.get("dynamicRange"));assertEquals("DR 200%",parse(new int[]{0x1403,3,200},new int[]{0x1400,3,1}).settings.get("dynamicRange"));}
 @Test public void verifiedXe5ScalePreservesRawProvenance(){FujiMakerNote r=parse(new int[]{0x100a,9,40,-60});r.applyCameraScale("X-E5");assertEquals("R +2 / B -3（相机档位；原始 40 / -60）",r.settings.get("wbShift"));}
 @Test public void unknownModelsAndInvalidStepsRetainRawUnits(){for(String camera:new String[]{"X-T5","","X-E5"}){FujiMakerNote r=parse(new int[]{0x100a,9,30,-60});r.applyCameraScale(camera);assertTrue(r.settings.get("wbShift").contains("原始单位"));}FujiMakerNote r=parse(new int[]{0x100a,9,200,-60});r.applyCameraScale("X-E5");assertTrue(r.settings.get("wbShift").contains("原始单位"));}
 @Test public void standardLensModelWorksWithoutFujiMakerNote(){byte[] b=fixture(false,new int[][]{{0x1401,3,0}});java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);byte[] lens="Sigma 17-40mm\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);buffer.putShort(34,(short)0xa434).putShort(36,(short)2).putInt(38,lens.length).putInt(42,44);System.arraycopy(lens,0,b,50,lens.length);FujiMakerNote r=FujiMakerNote.parseExif(b);assertEquals("Sigma 17-40mm",r.lens);assertFalse(r.present);buffer.putInt(42,Integer.MAX_VALUE);assertFalse(FujiMakerNote.parseExif(b).present);}
}
