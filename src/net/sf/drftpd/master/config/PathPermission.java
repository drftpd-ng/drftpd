/*
 * Created on 2003-aug-13
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.Collection;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class PathPermission {
	private String command;
	private String path;
	
	public PathPermission(String command, String path, Collection users) {
		this.command = command;
		this.path = path;
		
	}

}
