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

import org.drftpd.common.util.Bytes;
import org.drftpd.find.master.FindUtils;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;

/**
 * @author scitz0
 * @version $Id$
 */
public class SizeOption implements OptionInterface {

    @Override
    public void exec(String option, String[] args, AdvancedSearchParams params) throws ImproperUsageException {
        if (option.equalsIgnoreCase("-size")) {
            if (args == null) {
                throw new ImproperUsageException("Missing argument for " + option + " option");
            }
            try {
                String[] range = FindUtils.getRange(args[0], ":");
                if (range[0] != null && range[1] != null) {
                    long minSize = Bytes.parseBytes(range[0]);
                    long maxSize = Bytes.parseBytes(range[1]);
                    if (minSize > maxSize) {
                        throw new ImproperUsageException("Size range invalid, min value higher than max");
                    }
                    params.setMinSize(minSize);
                    params.setMaxSize(maxSize);
                } else if (range[0] != null) {
                    params.setMinSize(Bytes.parseBytes(range[0]));
                } else if (range[1] != null) {
                    params.setMaxSize(Bytes.parseBytes(range[1]));
                }
            } catch (NumberFormatException e) {
                throw new ImproperUsageException(e);
            }
        } else if (option.equalsIgnoreCase("-0byte")) {
            params.setMinSize(0L);
            params.setMaxSize(0L);
        }
    }
}
