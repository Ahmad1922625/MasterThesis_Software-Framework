package com.MyCarApp.modules.facial_recognition;

import android.graphics.Bitmap;

public class ImageProcessor {
    private static final int IMAGE_SIZE = 112; // Model requires 112x112 images
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;

    public static float[] preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        int[] intValues = new int[IMAGE_SIZE * IMAGE_SIZE];
        float[] floatValues = new float[IMAGE_SIZE * IMAGE_SIZE * 3];

        resizedBitmap.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

        for (int i = 0; i < IMAGE_SIZE * IMAGE_SIZE; i++) {
            int pixel = intValues[i];
            floatValues[i * 3] = (((pixel >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD; // Red
            floatValues[i * 3 + 1] = (((pixel >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD; // Green
            floatValues[i * 3 + 2] = ((pixel & 0xFF) - IMAGE_MEAN) / IMAGE_STD; // Blue
        }

        return floatValues;
    }

}
