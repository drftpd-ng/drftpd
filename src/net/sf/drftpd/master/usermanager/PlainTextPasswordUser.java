package net.sf.drftpd.master.usermanager;

/**
 * @author mog
 *
 * @version $Id: PlainTextPasswordUser.java,v 1.2 2003/12/23 13:38:20 mog Exp $
 */
public interface PlainTextPasswordUser extends User {
	public String getPassword();
}
