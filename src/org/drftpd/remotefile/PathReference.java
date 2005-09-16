package org.drftpd.remotefile;

/**
 * @author zubov
 * @version $Id$
 * Helper class gives a way to call methods on the FileManager
 * Most of the methods in LinkedRemoteFile will move here
 */

public final class PathReference {

	private String _path = null;
	public PathReference(String path) {
		_path = path;
	}
	
	public String getPath() {
		return _path;
	}

}
