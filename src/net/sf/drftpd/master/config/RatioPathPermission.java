package net.sf.drftpd.master.config;

import java.util.Collection;

import org.apache.oro.text.regex.Pattern;

/**
 * @author mog
 *
 * @version $Id: RatioPathPermission.java,v 1.4 2003/12/29 19:14:35 zubov Exp $
 */
public class RatioPathPermission extends PatternPathPermission {
	private float ratio;
	/**
	 * @param path
	 * @param users
	 */
	public RatioPathPermission(Pattern pattern, float ratio, Collection users) {
		super(pattern, users);
		this.ratio = ratio;
	}
	public float getRatio() {
		return this.ratio;
	}
}
