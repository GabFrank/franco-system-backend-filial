package com.franco.dev.fmc.model;

import com.google.firebase.messaging.MessagingErrorCode;

public class DeliveryResult {

    private final DeliveryOutcome outcome;
    private final String message;
    private final MessagingErrorCode errorCode;

    private DeliveryResult(DeliveryOutcome outcome, String message, MessagingErrorCode errorCode) {
        this.outcome = outcome;
        this.message = message;
        this.errorCode = errorCode;
    }

    public static DeliveryResult success() {
        return new DeliveryResult(DeliveryOutcome.SUCCESS, null, null);
    }

    public static DeliveryResult invalidToken(String message, MessagingErrorCode code) {
        return new DeliveryResult(DeliveryOutcome.INVALID_TOKEN, message, code);
    }

    public static DeliveryResult transientError(String message, MessagingErrorCode code) {
        return new DeliveryResult(DeliveryOutcome.TRANSIENT_ERROR, message, code);
    }

    public static DeliveryResult failure(String message, MessagingErrorCode code) {
        return new DeliveryResult(DeliveryOutcome.FAILURE, message, code);
    }

    public DeliveryOutcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public MessagingErrorCode getErrorCode() {
        return errorCode;
    }

    public enum DeliveryOutcome {
        SUCCESS,
        INVALID_TOKEN,
        TRANSIENT_ERROR,
        FAILURE
    }
}
