/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.find.master.option;

import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

/**
 * @author scitz0
 * @version $Id$
 */
public class SortOption implements OptionInterface {

    @Override
    public void exec(String option, String[] args, AdvancedSearchParams params) throws ImproperUsageException {

        if (option.equalsIgnoreCase("-sort")) {
            if (args == null) {
                throw new ImproperUsageException("Missing argument for " + option + " option");
            }
            params.setSortField(args[0]);
            if (args.length == 2) {
                // Sort order also specified
                params.setSortOrder(args[1].equalsIgnoreCase("desc"));
            }
        } else if (option.equalsIgnoreCase("-random")) {
            params.setSortOrder(null);
        }
    }
}
