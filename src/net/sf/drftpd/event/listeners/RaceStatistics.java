/*
 * Created on Dec 2, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author zubov
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class RaceStatistics implements FtpListener {

	ConnectionManager _cm;
	
	public RaceStatistics(ConnectionManager cm){
		init(cm);
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.Event)
	 */
	public void actionPerformed(Event event) {
		if ( !(event instanceof DirectoryFtpEvent))
			return;
		DirectoryFtpEvent direvent = (DirectoryFtpEvent) event;
		if ( !direvent.getCommand().equals("STOR"))
			return;
		LinkedRemoteFile dir;
		try {
			dir = direvent.getDirectory().getParentFile();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
		SFVFile sfvfile;
		try {
			sfvfile = dir.lookupSFVFile();
			// throws IOException, ObjectNotFoundException, NoAvailableSlaveException
		} catch (FileNotFoundException ex) {
			// can't save stats with no sfv file
			return;
		} catch (NoAvailableSlaveException e) {
			// can't save stats with no sfv file
			return;
		} catch (IOException e) {
			// can't save stats with no sfv file
			return;
		}

		if (!sfvfile.hasFile(direvent.getDirectory().getName()))
			return;

		//COMPLETE
		if (sfvfile.finishedFiles() != sfvfile.size())
			return;
		Collection racers = IRCListener.topFileUploaders(sfvfile.getFiles());
		if ( racers.size() <= 1 )
			return; // no race
		int count = 1;
		for ( Iterator iter = racers.iterator(); iter.hasNext(); count++) {
			UploaderPosition racer = (UploaderPosition) iter;
			User user;
			try {
				user = _cm.getUserManager().getUserByName(racer.getUsername());
			} catch (NoSuchUserException ex) {
				// this should not happen, but if it does, it means the user was deleted
				// we want to ignore their stats, but the race still did happen
				continue;
			} catch (UserFileException ex) {
				//if ( ex instanceof CorruptUserFileException )
				// don't add stats to badd users
				continue;
			}
			if ( count == 1 )
				user.addRacesWon();
			else if ( count == racers.size())
				user.addRacesLost();
			else user.addRacesParticipated();
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}
}
