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
public class LeechersMultiplierPathPermission extends MultiplierPathPermission {
	private boolean leechers;
	/**
	 * @param path
	 * @param users
	 * @param multiplier
	 */
	public LeechersMultiplierPathPermission(
		String path,
		Collection users,
		float multiplier, boolean leechers) {
		super(path, users, multiplier);
		this.leechers = leechers;
	}

}
