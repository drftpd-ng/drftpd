/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.sections.def;

import net.sf.drftpd.master.ConnectionManager;

import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;

/**
 * @author mog
 * @version $Id: SectionManager.java,v 1.1 2004/02/26 21:11:08 mog Exp $
 */
public class SectionManager implements SectionManagerInterface {

	private ConnectionManager _cm;

	public SectionManager(ConnectionManager cm) {
		_cm = cm;
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	public SectionInterface lookup(String string) {
		throw new UnsupportedOperationException();
	}

}
