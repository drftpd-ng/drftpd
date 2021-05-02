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
import org.drftpd.find.master.FindUtils;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public class AgeOption implements OptionInterface {

    private final Map<String, String> _options = Map.of(
            "age", "<start date>:<end date> # Valid date formats: <yyyy.MM.dd.HH.mm.ss> or <yyyy.MM.dd>"
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
        SimpleDateFormat fullDate = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
        SimpleDateFormat shortDate = new SimpleDateFormat("yyyy.MM.dd");
        String[] range = FindUtils.getRange(args[0], ":");
        try {
            if (range[0] != null) {
                if (range[0].length() == 10) {
                    params.setMinAge(shortDate.parse(range[0]).getTime());
                } else if (range[0].length() == 19) {
                    params.setMinAge(fullDate.parse(range[0]).getTime());
                } else {
                    throw new ImproperUsageException("Invalid date format for min age.");
                }
            }
        } catch (ParseException e) {
            throw new ImproperUsageException("Invalid start date format", e);
        }

        try {
            if (range[1] != null) {
                if (range[1].length() == 10) {
                    params.setMaxAge(shortDate.parse(range[1]).getTime());
                } else if (range[1].length() == 19) {
                    params.setMaxAge(fullDate.parse(range[1]).getTime());
                } else {
                    throw new ImproperUsageException("Invalid date format for max age.");
                }
            }
        } catch (ParseException e) {
            throw new ImproperUsageException("Invalid end date format", e);
        }
    }
}
