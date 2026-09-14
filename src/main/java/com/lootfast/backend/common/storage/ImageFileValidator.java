package com.lootfast.backend.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class ImageFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    public byte[] validateAndRead(
            MultipartFile file,
            long maxFileSize,
            long maxPixelCount
    ) {
        if (file == null || file.isEmpty()) {
            throw invalid(ImageValidationFailure.FILE_REQUIRED);
        }
        if (file.getSize() > maxFileSize) {
            throw invalid(ImageValidationFailure.FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw invalid(ImageValidationFailure.UNSUPPORTED_MEDIA_TYPE);
        }

        try {
            byte[] content = file.getBytes();
            validateImageContent(content, contentType, maxPixelCount);
            return content;
        } catch (IOException exception) {
            throw new ImageValidationException(
                    ImageValidationFailure.UNREADABLE_FILE,
                    exception
            );
        }
    }

    private void validateImageContent(
            byte[] content,
            String contentType,
            long maxPixelCount
    ) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (input == null) {
                throw invalid(ImageValidationFailure.INVALID_IMAGE);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid(ImageValidationFailure.INVALID_IMAGE);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixelCount = (long) width * height;

                if (width <= 0 || height <= 0 || pixelCount > maxPixelCount) {
                    throw invalid(ImageValidationFailure.INVALID_DIMENSIONS);
                }

                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean formatMatches = (contentType.equalsIgnoreCase("image/png")
                        && actualFormat.equals("png"))
                        || (contentType.equalsIgnoreCase("image/jpeg")
                        && (actualFormat.equals("jpeg") || actualFormat.equals("jpg")));
                if (!formatMatches) {
                    throw invalid(ImageValidationFailure.MEDIA_TYPE_MISMATCH);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private ImageValidationException invalid(ImageValidationFailure failure) {
        return new ImageValidationException(failure);
    }
}
