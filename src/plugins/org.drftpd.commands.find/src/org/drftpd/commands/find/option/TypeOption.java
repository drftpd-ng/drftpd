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
package org.drftpd.commands.find.option;

import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.vfs.index.AdvancedSearchParams;

/**
 * @author scitz0
 * @version $Id$
 */
public class TypeOption implements OptionInterface {

	@Override
	public void exec(String option, String[] args, AdvancedSearchParams params) throws ImproperUsageException {
		if (option.equalsIgnoreCase("-f") || option.equalsIgnoreCase("-file")) {
			params.setInodeType(AdvancedSearchParams.InodeType.FILE);
		} else if (option.equalsIgnoreCase("-d") || option.equalsIgnoreCase("-dir")) {
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		}
	}
}
