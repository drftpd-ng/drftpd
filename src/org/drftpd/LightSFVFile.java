/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

/**
 * @author mog
 * @version $Id: LightSFVFile.java,v 1.3 2004/11/15 01:12:13 mog Exp $
 */
public class LightSFVFile extends AbstractSFVFile implements Serializable {
    /**
     * Constructor for SFVFile.
     */
    public LightSFVFile(BufferedReader in) throws IOException {
        String line;
        _entries = new HashMap();
        try {
            while ((line = in.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }

                if (line.charAt(0) == ';') {
                    continue;
                }

                int separator = line.indexOf(" ");

                if (separator == -1) {
                    continue;
                }

                String fileName = line.substring(0, separator);
                String checkSumString = line.substring(separator + 1);
                Long checkSum;

                try {
                    checkSum = Long.valueOf(checkSumString, 16);
                } catch (NumberFormatException e) {
                    continue;
                }
                _entries.put(fileName, checkSum);
            }
        } finally {
            in.close();
        }
        System.out.println("Parsed "+getClass().getName()+"[size="+size()+"]");
    }
}
