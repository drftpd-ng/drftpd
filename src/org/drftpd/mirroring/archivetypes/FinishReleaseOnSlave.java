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
package org.drftpd.mirroring.archivetypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author zubov
 * @version $Id: FinishReleaseOnSlave.java,v 1.2 2004/04/23 00:47:24 mog Exp $
 */
public class FinishReleaseOnSlave extends MoveReleaseToMostFreeSlave {

	public FinishReleaseOnSlave() {
		super();
	}

	public class SlaveCount {
		public SlaveCount() {
		}
		private int value = 1;
		public void addOne() {
			value++;
		}
		public int getValue() {
			return value;
		}
	}

	public ArrayList findDestinationSlaves() {
		HashMap slaveMap = new HashMap();
		for (Iterator iter = getDirectory().getFiles().iterator(); iter.hasNext();) {
			Collection tempSlaveList =
				((LinkedRemoteFileInterface) iter.next()).getSlaves();
				for (Iterator iter2 = tempSlaveList.iterator(); iter2.hasNext();) {
					RemoteSlave rslave = (RemoteSlave) iter2.next();
					if (rslave.isAvailable()) {
						SlaveCount i = (SlaveCount) slaveMap.get(rslave);
						if (i == null) {
							slaveMap.put(rslave,new SlaveCount());
						}
						else i.addOne();
					}
				}
		}
		RemoteSlave highSlave = null;
		int highCount = 0;
		for (Iterator iter = slaveMap.keySet().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			SlaveCount sc = (SlaveCount) slaveMap.get(rslave);
			if (sc.getValue() > highCount) {
				highCount = sc.getValue();
				highSlave = rslave;
			}
		}
		ArrayList returnMe = new ArrayList();
		logger.debug("choosing " + highSlave.getName() + " as the slave to archive to");
		returnMe.add(highSlave);
		return returnMe;
		}

}
