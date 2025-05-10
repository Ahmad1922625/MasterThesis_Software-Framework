package com.MyCarApp.modules.facial_recognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FaceEmbeddingModel {
    private Interpreter interpreter;

    public FaceEmbeddingModel(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context));
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("mobile_face_net.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    public float[][] getFaceEmbedding(float[] input) {
        float[][] output = new float[1][192]; // Output shape remains the same

        // 🔹 Convert 1D input array into a 4D tensor: [1, 112, 112, 3]
        float[][][][] reshapedInput = new float[1][112][112][3];

        int index = 0;
        for (int i = 0; i < 112; i++) {
            for (int j = 0; j < 112; j++) {
                reshapedInput[0][i][j][0] = input[index++]; // Red channel
                reshapedInput[0][i][j][1] = input[index++]; // Green channel
                reshapedInput[0][i][j][2] = input[index++]; // Blue channel
            }
        }

        // 🔹 Run inference with correctly shaped input
        interpreter.run(reshapedInput, output);
        return output;
    }

}
