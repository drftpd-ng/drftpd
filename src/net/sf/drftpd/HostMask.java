package net.sf.drftpd;

import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * @author mog
 * @version $Id: HostMask.java,v 1.3 2003/12/07 22:31:44 mog Exp $
 */
public class HostMask {
	private static final Logger logger = Logger.getLogger(HostMask.class);
	private String _hostMask;
	private String _identMask;

	public HostMask(String string) {
		int pos = string.indexOf('@');
		_identMask = string.substring(0, pos);
		_hostMask = string.substring(pos + 1);
	}

	public String getHostMask() {
		return _hostMask;
	}

	public String getIdentMask() {
		return _identMask;
	}

	/**
	 * Is ident used?
	 * @return false is ident mask equals "*"
	 */
	public boolean isIdentMaskSignificant() {
		return !_identMask.equals("*");
	}

	public boolean matches(String ident, InetAddress address) {
		Perl5Matcher m = new Perl5Matcher();

		GlobCompiler c = new GlobCompiler();
		System.out.println(
			"comparing "
				+ ident
				+ "@"
				+ address.getHostAddress()
				+ " and "
				+ getIdentMask()
				+ "@"
				+ getHostMask());
		try {
			if (!isIdentMaskSignificant() || m.matches(ident, c.compile(getIdentMask()))) {
				Pattern p = c.compile(getHostMask());
				if (m.matches(address.getHostAddress(), p) || m.matches(address.getHostName(), p)) {
					return true;
				}
			}
			return false;
		} catch (MalformedPatternException ex) {
			logger.warn("", ex);
			return false;
		}
	}
}
