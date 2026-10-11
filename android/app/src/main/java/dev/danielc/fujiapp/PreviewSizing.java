package dev.danielc.fujiapp;
final class PreviewSizing {static int sample(int width,int height,int edge){int sample=1;while(width/sample>edge||height/sample>edge){if(sample>Integer.MAX_VALUE/2)break;sample*=2;}return sample;}}
