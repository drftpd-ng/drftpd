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
package org.drftpd.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;

import java.io.File;

/**
 * @author djb61
 * @version $Id$
 */
public class LoggingZipTask extends Zip {

	/**
	 * @see org.apache.tools.ant.taskdefs.Zip#execute()
	 */
	@Override
	public void execute() throws BuildException {
		// See if this is a slave plugin
		boolean slavePlugin = getProject().getProperty("slave.plugin").equalsIgnoreCase("true");
		FileSet slaveFiles = getProject().getReference("slave.fileset");
		// Run the actual Jar process
		super.execute();
		// Log created file if needed
		if (slavePlugin) {
			File zipFile = getDestFile();
			String installDir = getProject().getProperty("installdir");
			String relativePath = zipFile.getPath().substring(installDir.length()+1);
			slaveFiles.appendIncludes(new String[]{relativePath});
		}
	}
}
