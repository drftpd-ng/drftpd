package org.drftpd.slave.async;

import org.drftpd.remotefile.CaseInsensitiveHashtable;


/**
 * @author zubov
 * @version $Id$
 */
public class AsyncResponseRemerge extends AsyncResponse {
    private CaseInsensitiveHashtable _files;
    private String _directory;

    public AsyncResponseRemerge(String directory,
        CaseInsensitiveHashtable files) {
        super("Remerge");
        _files = files;
        if (directory.contains("\\")) {
        	throw new RuntimeException("\\ is not an acceptable character in a directory path");
        }
        _directory = directory;
    }

    public String getDirectory() {
        return _directory;
    }

    public CaseInsensitiveHashtable getFiles() {
        return _files;
    }

    public String toString() {
        return getClass().getName() + "[path=" + getDirectory() + "]";
    }
}
