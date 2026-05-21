package com.win.detect.locate;



import com.win.detect.predict.Preprocess;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Simple demo (no Android Context): reads txt files from filesystem, preprocesses in Java,
 * then runs locate ONNX and returns aggregated predictions.
 *
 * In a real Android app you typically open assets via AssetManager and copy the ONNX model
 * into cache, then initialize LocatePipeline once in Application.onCreate().
 */
public final class OnnxLocateDemo {
    private OnnxLocateDemo() {}

    public static OnnxLocatePredictor.Result runDemo(
            String timePath,
            String ampPath,
            String phaPath,
            String onnxModelPath,
            int voteCount
    ) throws Exception {
        try (InputStream timeStream = new FileInputStream(timePath);
             InputStream ampStream = new FileInputStream(ampPath);
             InputStream phaStream = new FileInputStream(phaPath)) {

            Preprocess.SampleBatch batch = Preprocess.processTripletToSamples(
                    timeStream, ampStream, phaStream,
                    200, 80, 5, 3.0f
            );

            try (OnnxLocatePredictor predictor = OnnxLocatePredictor.create(new File(onnxModelPath))) {
                return predictor.predictAggregated(batch.data, voteCount);
            }
        }
    }

    public static float runPredict(
            String timePath,
            String ampPath,
            String phaPath,
            int voteCount
    ) throws Exception {
        try (InputStream timeStream = new FileInputStream(timePath);
             InputStream ampStream = new FileInputStream(ampPath);
             InputStream phaStream = new FileInputStream(phaPath)) {

            Preprocess.SampleBatch batch = Preprocess.processTripletToSamples(
                    timeStream, ampStream, phaStream,
                    200, 80, 5, 3.0f
            );

            OnnxLocatePredictor.Result result = LocatePipeline.predictFromTriplet(timeStream, ampStream, phaStream, voteCount);
            return result.mean;

        }
    }

}

