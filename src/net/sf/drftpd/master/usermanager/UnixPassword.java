package net.sf.drftpd.master.usermanager;

/**
 * @author mog
 *
 * @version $Id: UnixPassword.java,v 1.2 2003/12/23 13:38:20 mog Exp $
 */
public interface UnixPassword extends User {
	public String getUnixPassword();
	public void setUnixPassword(String password);
}
