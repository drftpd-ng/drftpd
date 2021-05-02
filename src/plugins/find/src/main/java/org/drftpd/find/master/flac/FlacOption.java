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
package org.drftpd.find.master.flac;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.find.master.FindSettings;
import org.drftpd.find.master.FindUtils;
import org.drftpd.find.master.option.OptionInterface;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.zipscript.master.flac.indexation.FlacQueryParams;

import java.util.Map;

/**
 * @author norox
 */
public class FlacOption implements OptionInterface {

    private final Map<String, String> _options = Map.of(
            "flacgenre", "<name> # Search Flac releases that has the supplied genre",
            "flactitle", "<name> # Search Flac releases that has the supplied title",
            "flacartist", "<name> # Search Flac releases that has the supplied artist",
            "flacalbum", "<name> # Search Flac releases that has the supplied album",
            "flacyear", "<min year>:<max year> # Search Flac releases that fall within max and max year"
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
        FlacQueryParams queryParams;
        try {
            queryParams = params.getExtensionData(FlacQueryParams.FLACQUERYPARAMS);
        } catch (KeyNotFoundException e) {
            queryParams = new FlacQueryParams();
            params.addExtensionData(FlacQueryParams.FLACQUERYPARAMS, queryParams);
        }
        if (option.equalsIgnoreCase("-flacgenre")) {
            queryParams.setGenre(args[0]);
        } else if (option.equalsIgnoreCase("-flactitle")) {
            queryParams.setTitle(args[0]);
        } else if (option.equalsIgnoreCase("-flacartist")) {
            queryParams.setArtist(args[0]);
        } else if (option.equalsIgnoreCase("-flacalbum")) {
            queryParams.setAlbum(args[0]);
        } else if (option.equalsIgnoreCase("-flacyear")) {
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
