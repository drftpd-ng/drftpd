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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;

/**
 * @author mog
 * @version $Id: SectionManager.java,v 1.2 2004/03/01 00:21:09 mog Exp $
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
		//TODO lookup() in default section manager
		throw new UnsupportedOperationException();
	}

	public Collection getSections() {
		ArrayList sections = new ArrayList();
		for (Iterator iter =
			_cm.getSlaveManager().getRoot().getDirectories().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFileInterface dir =
				(LinkedRemoteFileInterface) iter.next();
			sections.add(new Section(dir));
		}
		return sections;
	}

	public class Section implements SectionInterface {
		private LinkedRemoteFileInterface _lrf;

		public Section(LinkedRemoteFileInterface lrf) {
			_lrf = lrf;
		}

		public String getName() {
			return _lrf.getName();
		}

		public LinkedRemoteFileInterface getFile() {
			return _lrf;
		}

		public String getPath() {
			return _lrf.getPath();
		}

		public Collection getFiles() {
			return Collections.singletonList(_lrf);
		}
	}
}
