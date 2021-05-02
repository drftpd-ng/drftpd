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
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public interface OptionInterface {

    /**
     * Function to return all options the class has that implements this interface
     *
     * @return Map<String, String> A map consisting of optionName and help for that option
     */
    public Map<String, String> getOptions();

    /**
     * Function to execute an option
     *
     * @param option The option to be executed
     * @param args optional arguments (array) send to this option
     * @param params The AdvancedSearchParams class that holds all configurations to the find command
     * @param settings Settings for the find function itself
     * @throws ImproperUsageException When something is send that is incorrect or incomplete
     */
    public void executeOption(String option, String[] args, AdvancedSearchParams params, FindSettings settings) throws ImproperUsageException;

}
