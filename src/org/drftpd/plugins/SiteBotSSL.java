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
package org.drftpd.plugins;

import net.sf.drftpd.FatalException;

import org.apache.log4j.Logger;

import org.drftpd.BlindTrustManager;

import java.io.IOException;

import java.security.GeneralSecurityException;

import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;


/**
 * @author mog
 * @version $Id: SiteBotSSL.java,v 1.6 2004/11/02 07:32:51 zubov Exp $
 */
public class SiteBotSSL extends SiteBot {
    private static final Logger logger = Logger.getLogger(SiteBot.class);
    private boolean _useSSL;

    public SiteBotSSL() throws IOException {
        super();
    }

    protected void reload(Properties ircCfg) throws IOException {
        _useSSL = ircCfg.getProperty("irc.ssl", "false").equals("true");
        logger.debug("useSSL: " + _useSSL);
        super.reload(ircCfg);
    }

    public void connect() throws IOException {
        if (_useSSL) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                TrustManager[] tms = { new BlindTrustManager() };

                ctx.init(null, tms, null);
                _conn.connect(ctx.getSocketFactory().createSocket(_server, _port),
                    _server);

                return;
            } catch (GeneralSecurityException e) {
                throw new FatalException(e);
            }
        }
        super.connect();
    }
}
