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
package org.drftpd.plugins;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;

/**
 * @author mog
 * @version $Id$
 */
public class DelegatingArchive extends FtpListener {
	private ArrayList<FtpListener> _delegates = new ArrayList<FtpListener>();
	private static final Logger logger = Logger.getLogger(DelegatingArchive.class);
	private URLClassLoader _cl;

	public DelegatingArchive() {
	}

	public void actionPerformed(Event event) {
		if(event.getCommand().equals("RELOAD")) {
			unload();
			init(getGlobalContext());
			return;
		}
		for(FtpListener delegate : _delegates) {
			try {
				delegate.actionPerformed(event);
			} catch(Throwable t) {
				logger.error("Throwable from event handler", t);
			}
		}
	}

	public void unload() {
		for(FtpListener delegate : _delegates) {
			delegate.unload();
		}
	}
	public void init(GlobalContext gctx) {
		super.init(gctx);
		init2(gctx);
	}
	public void init2(GlobalContext gctx) {
		try {
			_cl = new URLClassLoader(new URL[] {new File("classes-archive").toURL()});
			_delegates.add((FtpListener)_cl.loadClass("org.drftpd.plugins.Archive").newInstance());
			_delegates.add((FtpListener)_cl.loadClass("org.drftpd.plugins.Mirror").newInstance());
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		for(FtpListener delegate : _delegates) {
			try {
			delegate.init(gctx);
			} catch(Throwable t) {
				logger.error("Throwable from init", t);
			}
		}
	}
}
