package org.eclipse.dataspace.filesharing.exception;

/**
 * Indicates an issue decoding or verifying a received JWT.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
