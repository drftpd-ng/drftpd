package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: PathPermission.java,v 1.5 2004/01/15 20:37:08 zubov Exp $
 */
public abstract class PathPermission extends Permission {
	
	public PathPermission(Collection users) {
		super(users);
	}

	public abstract boolean checkPath(LinkedRemoteFile path);
}
