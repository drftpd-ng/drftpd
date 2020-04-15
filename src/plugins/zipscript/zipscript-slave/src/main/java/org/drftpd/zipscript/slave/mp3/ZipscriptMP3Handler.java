/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.zipscript.slave.mp3;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.drftpd.zipscript.common.mp3.AsyncResponseMP3Info;
import org.drftpd.zipscript.common.mp3.MP3Info;

import java.io.IOException;

/**
 * Handler for MP3 info requests.
 *
 * @author djb61
 * @version $Id$
 */
public class ZipscriptMP3Handler extends AbstractHandler {

    public ZipscriptMP3Handler(SlaveProtocolCentral central) {
        super(central);
    }

    @Override
    public String getProtocolName() {
        return "ZipscriptMP3Protocol";
    }

    public AsyncResponse handleMP3File(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseMP3Info(ac.getIndex(),
                    getMP3File(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private MP3Info getMP3File(Slave slave, String path) throws IOException {
        MP3Parser mp3parser = new MP3Parser(slave.getRoots().getFile(path));
        MP3Info mp3info = mp3parser.getMP3Info();
        return mp3info;
    }
}
