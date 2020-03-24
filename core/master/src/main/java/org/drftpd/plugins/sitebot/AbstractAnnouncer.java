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
package org.drftpd.plugins.sitebot;

import org.drftpd.plugins.sitebot.config.AnnounceConfig;

import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id: AnnounceAnnouncer.java 2070 2010-09-18 00:15:11Z djb61 $
 */
public abstract class AbstractAnnouncer {

	private String _confDir;

	protected void setConfDir(String confDir) {
		_confDir = confDir;
	}

	protected String getConfDir() {
		return _confDir;
	}

	protected abstract void initialise(AnnounceConfig config, ResourceBundle bundle);

	protected abstract void stop();

	protected abstract String[] getEventTypes();
	
	protected abstract void setResourceBundle(ResourceBundle bundle);
	
	protected void sayOutput(String output, AnnounceWriter writer) {
		StringTokenizer st = new StringTokenizer(output,"\n");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			for (OutputWriter oWriter : writer.getOutputWriters()) {
				oWriter.sendMessage(token);
			}
		}
	}
	
}
