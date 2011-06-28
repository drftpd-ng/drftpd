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
import org.drftpd.commands.find.FindUtils;
import org.drftpd.vfs.index.AdvancedSearchParams;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * @author scitz0
 * @version $Id$
 */
public class AgeOption implements OptionInterface {

	@Override
	public void exec(String option, String[] args, AdvancedSearchParams params) throws ImproperUsageException {
		SimpleDateFormat fullDate = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
		SimpleDateFormat shortDate = new SimpleDateFormat("yyyy.MM.dd");
		try {
			String[] range = FindUtils.getRange(args[0], ":");

			if (range[0] != null) {
				if (range[0].length() == 10)
					params.setMinAge(shortDate.parse(range[0]).getTime());
				else if (range[0].length() == 19)
					params.setMinAge(fullDate.parse(range[0]).getTime());
				else
					throw new ImproperUsageException("Invalid date format for min age.");
			}

			if (range[1] != null) {
				if (range[1].length() == 10)
					params.setMaxAge(shortDate.parse(range[1]).getTime());
				else if (range[1].length() == 19)
					params.setMaxAge(fullDate.parse(range[1]).getTime());
				else
					throw new ImproperUsageException("Invalid date format for max age.");
			}
		} catch (ParseException e) {
			throw new ImproperUsageException("Invalid date format", e);
		}
	}
}
