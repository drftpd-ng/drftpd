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
package org.drftpd.commands.find.mp3;

import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.find.FindUtils;
import org.drftpd.commands.find.option.OptionInterface;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.lucene.extensions.mp3.MP3QueryParams;

/**
 * @author scitz0
 * @version $Id$
 */
public class MP3Option implements OptionInterface {

	@Override
	public void exec(String option, String[] args,
			AdvancedSearchParams params) throws ImproperUsageException {
		if (args == null) {
			throw new ImproperUsageException("Missing argument for "+option+" option");
		}
		MP3QueryParams queryParams;
		try {
			queryParams = params.getExtensionData(MP3QueryParams.MP3QUERYPARAMS);
		} catch (KeyNotFoundException e) {
			queryParams = new MP3QueryParams();
			params.addExtensionData(MP3QueryParams.MP3QUERYPARAMS, queryParams);
		}
		if (option.equalsIgnoreCase("-mp3genre")) {
			queryParams.setGenre(args[0]);
		} else if (option.equalsIgnoreCase("-mp3title")) {
			queryParams.setTitle(args[0]);
		} else if (option.equalsIgnoreCase("-mp3artist")) {
			queryParams.setArtist(args[0]);
		} else if (option.equalsIgnoreCase("-mp3album")) {
			queryParams.setAlbum(args[0]);
		} else if (option.equalsIgnoreCase("-mp3year")) {
			try {
				String[] range = FindUtils.getRange(args[0], ":");
				if (range[0] != null && range[1] != null) {
					Integer fromYear = Integer.valueOf(range[0]);
					Integer toYear = Integer.valueOf(range[1]);
					if (fromYear > toYear) {
						throw new ImproperUsageException("Year range invalid, min value higher than max");
					}
					queryParams.setMinYear(fromYear);
					queryParams.setMaxYear(toYear);
				} else if (range[0] != null) {
					queryParams.setMinYear(Integer.valueOf(range[0]));
				} else if (range[1] != null) {
					queryParams.setMinYear(1); // We dont want to search for years <= zero
					queryParams.setMaxYear(Integer.valueOf(range[1]));
				}
			} catch (NumberFormatException e) {
				throw new ImproperUsageException(e);
			}
		}
	}

}
