package org.dreambot.installer;

/**
 * Unchecked exception used by the installer when an unrecoverable error occurs.
 */
final class InstallerException extends RuntimeException {

    InstallerException(String message) {
        super(message);
    }

    InstallerException(String message, Throwable cause) {
        super(message, cause);
    }
}
