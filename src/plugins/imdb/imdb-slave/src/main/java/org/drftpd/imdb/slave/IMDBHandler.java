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
package org.drftpd.imdb.slave;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.imdb.common.AsyncResponseIMDBInfo;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Handler for NFO requests.
 *
 * @author lh
 */
public class IMDBHandler extends AbstractHandler {

    public IMDBHandler(SlaveProtocolCentral central) {
        super(central);
    }

    @Override
    public String getProtocolName() {
        return "IMDBProtocol";
    }

    public AsyncResponse handleIMDBFile(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseIMDBInfo(ac.getIndex(),
                    getIMDBFile(getSlaveObject(), ac.getArgs()));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private IMDBInfo getIMDBFile(Slave slave, String path) throws IOException, FileNotFoundException {
        BufferedReader reader = null;
        File file = slave.getRoots().getFile(path);
        CRC32 checksum = new CRC32();
        try (CheckedInputStream in = new CheckedInputStream(new FileInputStream(file), checksum)) {
            byte[] buf = new byte[4096];
            while (in.read(buf) != -1) {
            }
        }
        try {
            reader = new BufferedReader(new FileReader(file));
            IMDBInfo imdbInfo = IMDBInfo.importNFOInfoFromFile(reader);
            imdbInfo.setNFOFileName(file.getName());
            imdbInfo.setChecksum(checksum.getValue());
            return imdbInfo;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
