package org.drftpd.slave.async;

import org.drftpd.remotefile.CaseInsensitiveHashtable;


/**
 * @author zubov
 * @version $Id: AsyncResponseRemerge.java,v 1.4 2004/11/09 18:59:58 mog Exp $
 */
public class AsyncResponseRemerge extends AsyncResponse {
    private CaseInsensitiveHashtable _files;
    private String _directory;

    public AsyncResponseRemerge(String directory,
        CaseInsensitiveHashtable files) {
        super("Remerge");
        _files = files;
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
