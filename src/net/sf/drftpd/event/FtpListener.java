package net.sf.drftpd.event;

import net.sf.drftpd.Initializeable;

/**
 * @author mog
 *
 */

/**
 * @version $Id: FtpListener.java,v 1.7 2004/01/31 02:32:06 zubov Exp $
 */
public interface FtpListener extends Initializeable {
	public void actionPerformed(Event event);
	//public void init(ConnectionManager mgr);
	public void unload();
}
