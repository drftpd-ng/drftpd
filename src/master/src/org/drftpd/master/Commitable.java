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

package org.drftpd.master;

import java.io.IOException;

/**
 * Helps create a lazy write delay for serializing objects
 * 
 * @author zubov
 * @version $Id$
 */
public interface Commitable {
	
	
	/**
	 * Adds itself to the CommitManager by calling CommitManager.add()
	 *
	 */
    void commit();
	
	
	/**
	 * This is the actual serialization method to write this Commitable to disk
	 * @throws IOException
	 */
    void writeToDisk() throws IOException;
	
	/**
	 * Returns a textual descriptive name for `this` object that a drftpd user can recognize
	 * Used in error reporting
	 * @return
	 */
    String descriptiveName();
}
