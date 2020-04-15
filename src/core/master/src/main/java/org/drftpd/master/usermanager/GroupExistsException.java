package org.drftpd.master.usermanager;

/**
 * @author mikevg
 */
@SuppressWarnings("serial")
public class GroupExistsException extends Exception {
    public GroupExistsException() {
        super();
    }

    public GroupExistsException(String message) {
        super(message);
    }

    public GroupExistsException(Throwable cause) {
        super(cause);
    }

    public GroupExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
