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
package org.drftpd.mediainfo.slave;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.mediainfo.common.AsyncResponseMediaInfo;
import org.drftpd.mediainfo.common.MediaInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;

import java.io.IOException;

/**
 * Handler for MediaInfo requests.
 *
 * @author scitz0
 */
public class MediaInfoHandler extends AbstractHandler {

    private static final Logger logger = LogManager.getLogger(MediaInfo.class);

    public MediaInfoHandler(SlaveProtocolCentral central) {
        super(central);
        logger.info("Handler initialized");
    }

    @Override
    public String getProtocolName() {
        return "MediaInfoProtocol";
    }

    public AsyncResponse handleMediaInfo(AsyncCommandArgument ac) {
        try {
            if (MediaInfo.hasWorkingMediaInfo()) {
                return new AsyncResponseMediaInfo(ac.getIndex(),
                        getMediaInfo(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
            }
            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    private MediaInfo getMediaInfo(Slave slave, String path) throws IOException {
        return MediaInfo.getMediaInfoFromFile(slave.getRoots().getFile(path));
    }
}
