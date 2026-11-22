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
package org.drftpd.zipscript.master.sfv;

import org.drftpd.master.commands.ImproperUsageException;

import java.util.StringTokenizer;

/**
 * Parsed options of SITE RESCAN. The defaults reproduce the historical behaviour: checksums are
 * recalculated by the slave and bad and zero byte files are deleted.
 */
public class RescanOptions {
    public boolean recursive = false;
    public boolean force = true;
    public boolean deleteBad = true;
    public boolean deleteZeroByte = true;
    public boolean quiet = false;
    /** Skip files whose known CRC already matches the SFV, only checksum files without a known CRC. */
    public boolean quick = false;
    /** Rename files failing the SFV check to name.bad instead of deleting them. */
    public boolean markBad = false;
    /** The (still unresolved) file or path argument, or null. */
    public String fileOrPath = null;

    /**
     * @param argument the SITE RESCAN argument, may be null
     * @throws ImproperUsageException on more than one file/path argument
     */
    /** Suffix appended to the name of files marked as bad. */
    public static final String BAD_SUFFIX = ".bad";

    public static RescanOptions parse(String argument) throws ImproperUsageException {
        RescanOptions o = new RescanOptions();
        if (argument == null) {
            return o;
        }
        StringTokenizer args = new StringTokenizer(argument);
        while (args.hasMoreTokens()) {
            String arg = args.nextToken();
            if (arg.equalsIgnoreCase("-r")) {
                o.recursive = true;
            } else if (arg.equalsIgnoreCase("noforce")) {
                o.force = false;
            } else if (arg.equalsIgnoreCase("nodelete")) {
                o.deleteBad = false;
            } else if (arg.equalsIgnoreCase("nodelete0byte")) {
                o.deleteZeroByte = false;
            } else if (arg.equalsIgnoreCase("quiet")) {
                o.quiet = true;
            } else if (arg.equalsIgnoreCase("markbad")) {
                o.markBad = true;
            } else if (arg.equalsIgnoreCase("quick")) {
                o.quick = true;
            } else {
                if (o.fileOrPath != null) {
                    throw new ImproperUsageException();
                }
                o.fileOrPath = arg;
            }
        }
        if (o.quick) {
            // quick mode only ever uses the CRCs the master already knows
            o.force = false;
        }
        return o;
    }
}
