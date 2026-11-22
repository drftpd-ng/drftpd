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
package org.drftpd.zipscript.slave.zip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.drftpd.zipscript.common.zip.AsyncResponseDizInfo;
import org.drftpd.zipscript.common.zip.AsyncResponseZipCRCInfo;
import org.drftpd.zipscript.common.zip.DizInfo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Handler for Zip requests.
 *
 * @author djb61
 * @version $Id$
 */
public class ZipscriptZipHandler extends AbstractHandler {
    private static final Logger logger = LogManager.getLogger(ZipscriptZipHandler.class);
    // same match as before: a FILE_ID.DIZ in any directory of the zip, any case
    private static final Pattern DIZ_NAME = Pattern.compile("(?i).*file_id.diz");

    public ZipscriptZipHandler(SlaveProtocolCentral central) {
        super(central);
    }

    @Override
    public String getProtocolName() {
        return "ZipscriptZipProtocol";
    }

    public AsyncResponse handleZipCRC(AsyncCommandArgument ac) {
        return new AsyncResponseZipCRCInfo(ac.getIndex(),
                checkZipFile(getSlaveObject(), ac.getArgs()));
    }

    public AsyncResponse handleZipDiz(AsyncCommandArgument ac) {
        return new AsyncResponseDizInfo(ac.getIndex(),
                getDizInfo(getSlaveObject(), ac.getArgs()));
    }

    private boolean checkZipFile(Slave slave, String path) {
        File zip;
        try {
            zip = slave.getRoots().getFile(path);
        } catch (FileNotFoundException e) {
            return false;
        }
        try {
            checkZip(zip);
            return true;
        } catch (Throwable t) {
            logger.debug("Error validating integrity of {} : {}", path, t.toString());
            return false;
        }
    }

    /**
     * Reads every file in the zip to the end and checks its CRC against the one stored in the zip.
     * Uses {@link ZipFile}, which can be opened by several threads at once: the zip file system
     * ({@code FileSystems.newFileSystem}) refuses a second open of the same zip, which made the integrity
     * check fail at random when the DIZ was read at the same time.
     *
     * @throws IOException if the zip can't be read, a file is corrupt, or there is no file in it
     */
    static void checkZip(File zip) throws IOException {
        final byte[] buff = new byte[16384];
        int files = 0;
        try (ZipFile zipFile = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                CRC32 crc = new CRC32();
                try (CheckedInputStream in = new CheckedInputStream(new BufferedInputStream(
                        zipFile.getInputStream(entry)), crc)) {
                    while (in.read(buff) != -1) {
                        // only reading, the CRC is computed on the way
                    }
                } catch (IOException e) {
                    throw new IOException("CRC check failed for " + entry.getName(), e);
                }
                if (entry.getCrc() != -1 && entry.getCrc() != crc.getValue()) {
                    throw new IOException("CRC mismatch for " + entry.getName());
                }
                files++;
            }
        }
        if (files == 0) {
            throw new IOException("Zip file empty");
        }
    }

    private DizInfo getDizInfo(Slave slave, String path) {
        File zip;
        try {
            zip = slave.getRoots().getFile(path);
        } catch (FileNotFoundException e) {
            return new DizInfo();
        }
        try {
            return readDiz(zip);
        } catch (Throwable t) {
            logger.debug("Error getting diz info from {} : {}", path, t.toString());
            return new DizInfo();
        }
    }

    /**
     * Returns the info of the first FILE_ID.DIZ (any directory, any case) in the zip that holds a file total,
     * or an invalid {@link DizInfo} if there is none.
     */
    static DizInfo readDiz(File zip) throws IOException {
        DizInfo dizInfo = new DizInfo();
        try (ZipFile zipFile = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !DIZ_NAME.matcher(entry.getName()).matches()) {
                    continue;
                }
                String dizString;
                try (InputStream in = zipFile.getInputStream(entry)) {
                    dizString = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                }
                int total = getDizTotal(dizString);
                if (total > 0) {
                    dizInfo.setValid(true);
                    dizInfo.setTotal(total);
                    dizInfo.setString(Base64.getMimeEncoder().encodeToString(dizString.getBytes(StandardCharsets.ISO_8859_1)));
                    return dizInfo;
                }
            }
        }
        return dizInfo;
    }

    static int getDizTotal(String dizString) {
        int total = 0;
        String regex = "[\\[\\(\\<\\:\\s](?:\\s)?[0-9oOxX\\*]*(?:\\s)?/(?:\\s)?([0-9oOxX]*[0-9oO])(?:\\s)?[\\]\\)\\>\\s]";
        Pattern p = Pattern.compile(regex);

        // Compare the diz file to the pattern compiled above
        Matcher m = p.matcher(dizString);

        if (m.find()) {
            total = Integer.valueOf(m.group(1).replaceAll("[oOxX]", "0"));
        }

        return total;
    }
}
