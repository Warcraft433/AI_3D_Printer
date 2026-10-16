package com.printguard.storage;

/**
 * Thrown when a Supabase Storage bucket violates PrintGuard AI's strict security
 * policy by being configured as public rather than private.
 */
public class BucketPrivacyException extends RuntimeException {
    public BucketPrivacyException(String message) {
        super(message);
    }

    public BucketPrivacyException(String message, Throwable cause) {
        super(message, cause);
    }
}
