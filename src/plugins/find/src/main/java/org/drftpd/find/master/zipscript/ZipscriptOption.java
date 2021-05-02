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
package org.drftpd.find.master.zipscript;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.find.master.FindSettings;
import org.drftpd.find.master.FindUtils;
import org.drftpd.find.master.option.OptionInterface;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.zipscript.master.sfv.indexation.ZipscriptQueryParams;

import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public class ZipscriptOption implements OptionInterface {

    private final Map<String, String> _options = Map.of(
            "incomplete", "Find incomplete zipscript enabled releases",
            "present", "<min files>:<max files> # Find zipscript enabled releases that have between min and max files uploaded",
            "missing", "<min files>:<max files> # Find zipscript enabled releases that have between min and max files missing",
            "percent", "<min percentage>:<max percentage> # Find zipscript enabled releases that are between min and max percentage complete"
    );

    @Override
    public Map<String, String> getOptions() {
        return _options;
    }

    @Override
    public void executeOption(String option, String[] args, AdvancedSearchParams params, FindSettings settings) throws ImproperUsageException {
        if (!option.equalsIgnoreCase("-incomplete") && args == null) {
            throw new ImproperUsageException("Missing argument for " + option + " option");
        }
        ZipscriptQueryParams queryParams;
        try {
            queryParams = params.getExtensionData(ZipscriptQueryParams.ZIPSCRIPTQUERYPARAMS);
        } catch (KeyNotFoundException e) {
            queryParams = new ZipscriptQueryParams();
            params.addExtensionData(ZipscriptQueryParams.ZIPSCRIPTQUERYPARAMS, queryParams);
        }
        if (option.equalsIgnoreCase("-incomplete")) {
            queryParams.setMinMissing(1);
            params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
        } else if (option.equalsIgnoreCase("-present")) {
            Integer[] range = getIntRange(args[0]);
            queryParams.setMinPresent(range[0]);
            queryParams.setMaxPresent(range[1]);
            params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
        } else if (option.equalsIgnoreCase("-missing")) {
            Integer[] range = getIntRange(args[0]);
            queryParams.setMinMissing(range[0]);
            queryParams.setMaxMissing(range[1]);
            params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
        } else if (option.equalsIgnoreCase("-percent")) {
            Integer[] range = getIntRange(args[0]);
            queryParams.setMinPercent(range[0]);
            queryParams.setMaxPercent(range[1]);
            params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
        }
    }

    private Integer[] getIntRange(String arg) throws ImproperUsageException {
        Integer[] intRange = new Integer[2];
        try {
            String[] range = FindUtils.getRange(arg, ":");
            if (range[0] != null && range[1] != null) {
                int from = Integer.parseInt(range[0]);
                int to = Integer.parseInt(range[1]);
                if (from > to) {
                    throw new ImproperUsageException("Range invalid, min value higher than max");
                }
                intRange[0] = from;
                intRange[1] = to;
            } else if (range[0] != null) {
                intRange[0] = Integer.valueOf(range[0]);
            } else if (range[1] != null) {
                intRange[0] = 0; // We dont want to search for negative values
                intRange[1] = Integer.valueOf(range[1]);
            }
        } catch (NumberFormatException e) {
            throw new ImproperUsageException(e);
        }
        return intRange;
    }

}
