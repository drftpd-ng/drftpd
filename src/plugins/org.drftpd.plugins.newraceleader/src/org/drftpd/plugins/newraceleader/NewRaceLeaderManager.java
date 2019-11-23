/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.plugins.newraceleader;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.util.UploaderPosition;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TimerTask;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeaderManager.java 2393 2011-04-11 20:47:51Z cyber1331 $
 */
public class NewRaceLeaderManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(NewRaceLeaderManager.class);

	private static final long _delay = 1800000; // = 30 minutes

	private ArrayList<NewRaceLeader> _newraceleader;
	private TimerTask _newraceleaderTimer;

	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
		_newraceleader = new ArrayList<>();

		_newraceleaderTimer = new TimerTask() {
   			public void run() {
   				cleanOldRaces();
			}
		};

		try {
			GlobalContext.getGlobalContext().getTimer().schedule(_newraceleaderTimer,_delay,_delay);
		} catch (IllegalArgumentException e) {
			logger.error("Failed to start newraceleader timer: ",e);
		} catch (IllegalStateException e) {
			logger.error("Failed to restart newraceleader timer: ",e);
		}
	}

	private void cleanOldRaces() {
		synchronized (_newraceleader) {
			for (Iterator<NewRaceLeader> iter = _newraceleader.iterator(); iter.hasNext();) {
				NewRaceLeader nrl = iter.next();
				if ((nrl.getTime() + _delay + 5000) < System.currentTimeMillis()) {
                    logger.debug("Successfully age-deleted NewRaceLeader for: {}", nrl.getDir().getPath());
					iter.remove();
				}
			}
		}
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
		synchronized (this) {
			_newraceleader = new ArrayList<>();
		}
		if (_newraceleaderTimer != null) {
			_newraceleaderTimer.cancel();
		}
		GlobalContext.getGlobalContext().getTimer().purge();
	}

    /*
     * Get the NewRaceLEader Plugin
     */
    public static NewRaceLeaderManager getNewRaceLeaderManager() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof NewRaceLeaderManager) {
    			return (NewRaceLeaderManager) plugin;
    		}
    	}
    	throw new RuntimeException("NewRaceLeaderManager plugin is not loaded.");
    }

    public void delete(DirectoryHandle dir) {
    	synchronized (_newraceleader) {
	    	for (Iterator<NewRaceLeader> iter = _newraceleader.iterator(); iter.hasNext();) {
				NewRaceLeader nrl = iter.next();
				if (nrl.getDir().getPath().equals(dir.getPath())) {
                    logger.debug("Successfully deleted NewRaceLeader for: {}", nrl.getDir().getPath());
					iter.remove();
					break;
				}
			}
    	}
    }

    public synchronized void check(FileHandle file, int missing, int files, Collection<UploaderPosition> racers) {
		for (NewRaceLeader nrl : _newraceleader) {
			if (nrl.getDir().getPath().equals(file.getParent().getPath())) {
				try {
					if (racers.size() > 1) {
						nrl.check(file.getUsername(),missing,files,racers);
					}
					return;
				} catch (FileNotFoundException e) {
					// File no longer exists??
					return;
				}
			}
		}
		try {
			_newraceleader.add(new NewRaceLeader(file,racers,file.getUsername()));
		} catch (FileNotFoundException e) {
			// File No longer exists
			// Ignore
		}

    }
}
