/*
 * Created on 2003-aug-25
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
public class MultiplierPathPermission extends PathPermission {
	private float multiplier;
	/**
	 * @param path
	 * @param users
	 */
	public MultiplierPathPermission(String path, Collection users, float multiplier) {
		super(path, users);
		this.multiplier = multiplier;
	}

}
