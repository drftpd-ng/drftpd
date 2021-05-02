package org.drftpd.common.exceptions;

public class SSLServiceException extends Exception {

    public SSLServiceException() { super(); }

    public SSLServiceException(String message) {
        super(message);
    }

    public SSLServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public SSLServiceException(Throwable cause) {
        super(cause);
    }
}

