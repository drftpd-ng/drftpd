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
package org.drftpd.zipscript.slave.sfv;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.drftpd.zipscript.common.sfv.AsyncResponseSFVInfo;
import org.drftpd.zipscript.common.sfv.SFVInfo;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Handler for SFV requests.
 *
 * @author fr0w
 * @version $Id$
 */
public class ZipscriptHandler extends AbstractHandler {
    public ZipscriptHandler(SlaveProtocolCentral central) {
        super(central);
    }

    @Override
    public String getProtocolName() {
        return "ZipscriptProtocol";
    }

    public AsyncResponse handleSfvFile(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseSFVInfo(ac.getIndex(),
                    getSFVFile(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private SFVInfo getSFVFile(Slave slave, String path) throws IOException {
        BufferedReader reader = null;
        CRC32 checksum;
        try {
            File file = slave.getRoots().getFile(path);
            checksum = new CRC32();
            reader = new BufferedReader(new InputStreamReader(new CheckedInputStream(new FileInputStream(file), checksum)));
            SFVInfo sfvInfo = SFVInfo.importSFVInfoFromFile(reader);
            sfvInfo.setSFVFileName(file.getName());
            sfvInfo.setChecksum(checksum.getValue());
            return sfvInfo;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
