package org.opensearch.ml.common.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;

// TODO: Add image preprocessing options: resample, rescale, rescale factor, normalization. There is no hugging face JNI for image preprocessing.
public class ImageEmbeddingPreprocessorConfig {
    String SIZE = "size";
    String HEIGHT = "height";
    String WIDTH = "width";

    public final int imageHeightSize;
    public final int imageWidthSize;

    public ImageEmbeddingPreprocessorConfig(Path configPath) throws IOException {
        String jsonString = new String(Files.readAllBytes(configPath));
        JSONObject jsonObject = new JSONObject(jsonString);

        if (jsonObject.has(SIZE)) {
            Object size = jsonObject.get(SIZE);
            if (size instanceof Integer) {
                this.imageHeightSize = (int) size;
                this.imageWidthSize = (int) size;
            } else if (size instanceof JSONObject) {
                JSONObject sizeObject = (JSONObject) size;
                if (sizeObject.has(HEIGHT) && sizeObject.has(WIDTH)) {
                    this.imageHeightSize = sizeObject.getInt(HEIGHT);
                    this.imageWidthSize = sizeObject.getInt(WIDTH);
                } else {
                    throw new IllegalArgumentException(
                        String.format("Invalid %s structure: size object must contain height and width", configPath)
                    );
                }
            } else {
                throw new IllegalArgumentException(
                    String.format("Invalid %s structure: size must be an integer or an object containing height and width", configPath)
                );
            }
        } else {
            throw new IllegalArgumentException(String.format("Invalid %s structure: missing size field", configPath));
        }
    }
}
