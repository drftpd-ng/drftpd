package org.drftpd.slave.async;

import net.sf.drftpd.remotefile.LinkedRemoteFile;


/**
 * @author zubov
 * @version $Id: AsyncResponseRemerge.java,v 1.2 2004/11/02 07:32:53 zubov Exp $
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
}
