/*
 * Created on 2003-sep-18
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 */
public abstract class PathPermission extends Permission {
	
	public PathPermission(Collection users) {
		super(users);
	}

	public abstract boolean checkPath(LinkedRemoteFile path);
}
