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

import org.drftpd.find.master.DupeUtils;
import org.drftpd.find.master.FindSettings;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

import java.util.Map;

/**
 * Restricts find results to directories whose name exists more than once (dupes).
 * Actions are only executed on the copies that are not kept.
 */
public class DupeOption implements OptionInterface {

    private final Map<String, String> _options = Map.of(
            "dupe", "[oldest|newest] # Only dirs whose name exists more than once, actions run on all copies but the kept one (default keeps oldest)"
    );

    @Override
    public Map<String, String> getOptions() {
        return _options;
    }

    @Override
    public void executeOption(String option, String[] args, AdvancedSearchParams params, FindSettings settings) throws ImproperUsageException {
        DupeUtils.KeepPolicy policy = DupeUtils.KeepPolicy.OLDEST;
        if (args != null) {
            if (args.length != 1) {
                throw new ImproperUsageException("Invalid argument for " + option + " option, expected oldest or newest");
            }
            if (args[0].equalsIgnoreCase("newest")) {
                policy = DupeUtils.KeepPolicy.NEWEST;
            } else if (!args[0].equalsIgnoreCase("oldest")) {
                throw new ImproperUsageException("Invalid argument for " + option + " option, expected oldest or newest");
            }
        }
        // Dupes are releases, i.e. directories
        params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
        settings.setDupe(policy);
    }
}
