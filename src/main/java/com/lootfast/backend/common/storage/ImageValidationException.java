package com.lootfast.backend.common.storage;

public class ImageValidationException extends RuntimeException {

    private final ImageValidationFailure failure;

    public ImageValidationException(ImageValidationFailure failure) {
        this.failure = failure;
    }

    public ImageValidationException(
            ImageValidationFailure failure,
            Throwable cause
    ) {
        super(cause);
        this.failure = failure;
    }

    public ImageValidationFailure getFailure() {
        return failure;
    }
}
