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
package org.drftpd.commands.find.tvmaze;

import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.find.option.OptionInterface;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.lucene.extensions.tvmaze.TvMazeQueryParams;

/**
 * @author scitz0
 * @version $Id: MP3Option.java 2491 2011-07-11 21:56:53Z scitz0 $
 */
public class TvMazeOption implements OptionInterface {

	@Override
	public void exec(String option, String[] args,
			AdvancedSearchParams params) throws ImproperUsageException {
		if (args == null) {
			throw new ImproperUsageException("Missing argument for "+option+" option");
		}
		TvMazeQueryParams queryParams;
		try {
			queryParams = params.getExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS);
		} catch (KeyNotFoundException e) {
			queryParams = new TvMazeQueryParams();
			params.addExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS, queryParams);
		}
		if (option.equalsIgnoreCase("-tvname")) {
			queryParams.setName(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvgenre")) {
			queryParams.setGenre(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvseason")) {
			queryParams.setSeason(Integer.parseInt(args[0]));
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvnumber")) {
			queryParams.setNumber(Integer.parseInt(args[0]));
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvtype")) {
			queryParams.setType(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvstatus")) {
			queryParams.setStatus(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvlanguage")) {
			queryParams.setLanguage(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvcountry")) {
			queryParams.setCountry(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		} else if (option.equalsIgnoreCase("-tvnetwork")) {
			queryParams.setNetwork(args[0]);
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		}
	}

}
