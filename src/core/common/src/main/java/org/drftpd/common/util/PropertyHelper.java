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

package org.drftpd.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author mog
 * @version $Id$
 */
public class PropertyHelper {
    private static final Logger logger = LogManager.getLogger(PropertyHelper.class);

    private PropertyHelper() {
    }

    public static String getProperty(Properties p, String name) throws NullPointerException {
        String result = p.getProperty(name);

        if (result == null) {
            throw new NullPointerException("Error getting setting " + name);
        }

        return result;
    }

    public static String getProperty(Properties p, String name, String defaultValue) throws NullPointerException {
        // result can't be null
        return p.getProperty(name, defaultValue);
    }

    /**
     * Function create a List of Strings representing key.x (where x is 1 -> infinity)
     *
     * @param p The properties configuration that holds the key we are looking for
     * @param key The key to look for
     * @return a new List of strings or null if no config items were found
     */
    public static List<String> getStringListedProperty(Properties p, String key) {
        List<String> items = new ArrayList<>();
        int x = 1;
        String item;
        while((item = p.getProperty(key + "." + x)) != null) {
            logger.debug("Checking out {} with value: {}", key+"."+x, item);
            items.add(item);
            x++;
        }
        return items.size() > 0 ? items : null;
    }
}
