package net.sf.drftpd.event;

import net.sf.drftpd.Initializeable;

/**
 * @author mog
 *
 */

/**
 * @version $Id: FtpListener.java,v 1.6 2003/12/23 13:38:18 mog Exp $
 */
public interface FtpListener extends Initializeable {
	public void actionPerformed(Event event);
	//public void init(ConnectionManager mgr);
}
