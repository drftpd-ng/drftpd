package net.sf.drftpd.master.config;

import java.util.Collection;

/**
 * @author mog
 *
 * @version $Id: RatioPathPermission.java,v 1.3 2003/12/23 13:38:20 mog Exp $
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
