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
package org.drftpd.autonuke.master.imdb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.autonuke.master.Config;
import org.drftpd.autonuke.master.ConfigData;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.master.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * @author scitz0
 */
public class IMDBConfig extends Config {
    private static final Logger logger = LogManager.getLogger(IMDBConfig.class);
    String _field, _operator, _value;

    public IMDBConfig(int i, Properties p) {
        super(i, p);
        _field = PropertyHelper.getProperty(p, i + ".imdb.field", "");
        _operator = PropertyHelper.getProperty(p, i + ".imdb.operator", "");
        _value = PropertyHelper.getProperty(p, i + ".imdb.value", "");
    }

    /**
     * Check imdb metadata if available
     *
     * @param configData Object holding return data
     * @param dir        Directory currently being handled
     * @return Return false if dir should be nuked, else true
     */
    public boolean process(ConfigData configData, DirectoryHandle dir) {
        try {
            IMDBInfo imdbInfo = dir.getPluginMetaData(IMDBInfo.IMDBINFO);
            if (_field.equalsIgnoreCase("Title")) {
                return _operator.equals("!") == imdbInfo.getTitle().matches(_value);
            } else if (_field.equalsIgnoreCase("Language")) {
                return _operator.equals("!") == imdbInfo.getLanguage().matches(_value);
            } else if (_field.equalsIgnoreCase("Country")) {
                return _operator.equals("!") == imdbInfo.getCountry().matches(_value);
            } else if (_field.equalsIgnoreCase("Year")) {
                return !handleDigitComparison(imdbInfo.getYear());
            } else if (_field.equalsIgnoreCase("Director")) {
                return _operator.equals("!") == imdbInfo.getDirector().matches(_value);
            } else if (_field.equalsIgnoreCase("Genres")) {
                return _operator.equals("!") == imdbInfo.getGenres().matches(_value);
            } else if (_field.equalsIgnoreCase("Plot")) {
                return _operator.equals("!") == imdbInfo.getPlot().matches(_value);
            } else if (_field.equalsIgnoreCase("Rating")) {
                return !handleDigitComparison(imdbInfo.getRating());
            } else if (_field.equalsIgnoreCase("Votes")) {
                return !handleDigitComparison(imdbInfo.getVotes());
            } else if (_field.equalsIgnoreCase("Runtime")) {
                return !handleDigitComparison(imdbInfo.getRuntime());
            }
        } catch (KeyNotFoundException e1) {
            // No IMDB info found, return true
        } catch (FileNotFoundException e2) {
            // Hmm...
        }
        return true;
    }

    /**
     * Handle comparison of fields containing digits
     *
     * @param meta Metadata for the field specified
     * @return Return true if mathematical equation are correct, else false
     */
    private boolean handleDigitComparison(Integer meta) {
        if (meta != null) {
            int conf_value = Integer.valueOf(_value.replaceAll("\\D", ""));
            switch (_operator) {
                case "<":
                    return meta < conf_value;
                case "=":
                    return meta == conf_value;
                case "!=":
                    return meta != conf_value;
                case ">":
                    return meta > conf_value;
            }
        }
        return false;
    }

}
