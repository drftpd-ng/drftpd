package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 *
 * @version $Id: StringPathPermission.java,v 1.4 2003/12/23 13:38:20 mog Exp $
 */
public class StringPathPermission extends PathPermission {
	private String path;

	public StringPathPermission(String path, Collection users) {
		super(users);
		this.path = path;
	}

	public boolean checkPath(LinkedRemoteFile path) {
		assert path.isDirectory() : "Should be a directory";
		return (path.getPath()+"/").startsWith(this.path);
	}
}