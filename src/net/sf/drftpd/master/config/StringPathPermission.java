/*
 * Created on 2003-aug-13
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class StringPathPermission extends PathPermission {
	private String path;

	public StringPathPermission(String path, Collection users) {
		super(users);
		this.path = path;
	}

	private boolean myCheckPath(String path) {
		return path.startsWith(this.path);
	}

	public boolean checkPath(LinkedRemoteFile path) {
		assert path.isDirectory() : "Should be a directory";
		return myCheckPath(path.getPath()+"/");
	}
}