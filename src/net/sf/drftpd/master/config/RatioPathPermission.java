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
public class RatioPathPermission extends StringPathPermission {
	private float ratio;
	/**
	 * @param path
	 * @param users
	 */
	public RatioPathPermission(float ratio, String path, Collection users) {
		super(path, users);
		this.ratio = ratio;
	}
	public float getRatio() {
		return this.ratio;
	}
}
