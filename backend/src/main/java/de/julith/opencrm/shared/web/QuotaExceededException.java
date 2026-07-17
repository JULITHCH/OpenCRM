package de.julith.opencrm.shared.web;

/** Zeitliche Tenant-Limits (E-35): wird als 429 quota_exceeded mit Retry-After beantwortet. */
public class QuotaExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public QuotaExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
