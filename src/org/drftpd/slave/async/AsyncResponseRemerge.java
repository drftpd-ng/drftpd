package org.drftpd.slave.async;

import net.sf.drftpd.remotefile.LinkedRemoteFile;


/**
 * @author zubov
 * @version $Id: AsyncResponseRemerge.java,v 1.3 2004/11/08 18:39:31 mog Exp $
 */
public class AsyncResponseRemerge extends AsyncResponse {
    private LinkedRemoteFile.CaseInsensitiveHashtable _files;
    private String _directory;

    public AsyncResponseRemerge(String directory,
        LinkedRemoteFile.CaseInsensitiveHashtable files) {
        super("Remerge");
        _files = files;
        _directory = directory;
    }

    public String getDirectory() {
        return _directory;
    }

    public LinkedRemoteFile.CaseInsensitiveHashtable getFiles() {
        return _files;
    }

    public String toString() {
        return getClass().getName() + "[path=" + getDirectory() + "]";
    }
}
