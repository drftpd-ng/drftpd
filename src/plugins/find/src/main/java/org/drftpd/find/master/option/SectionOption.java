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

import org.drftpd.find.master.FindSettings;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public class SectionOption implements OptionInterface {

    private final Map<String, String> _options = Map.of(
            "section", "<Section name> # Search only in specified section"
    );

    @Override
    public Map<String, String> getOptions() {
        return _options;
    }

    @Override
    public void executeOption(String option, String[] args, AdvancedSearchParams params, FindSettings settings) throws ImproperUsageException {
        if (args == null) {
            throw new ImproperUsageException("Missing argument for " + option + " option");
        }
        settings.setDirectoryHandle(GlobalContext.getGlobalContext().getSectionManager().getSection(args[0]).getBaseDirectory());
    }
}
