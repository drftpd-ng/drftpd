/*
 * Created on 2003-aug-11
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class XferLogListener implements FtpListener {

	/**
	 * 
	 */
	public XferLogListener() {
		super();
		new File("ftp-data/logs").mkdirs();
		try {
			//APPEND
			this.out = new PrintStream(new FileOutputStream("ftp-data/logs/xferlog", true));
		} catch (IOException e) {
			throw new FatalException(e);
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.Event)
	 */
	public void actionPerformed(Event event) {
		if (event instanceof TransferEvent)
			actionPerformed((TransferEvent) event);
	}

	/**
	 * xferlog.log - Contains all the upload/download information for all files
	          transferred (if logging of that is enabled). The format is the
	          following: current time, transfer time, user's hostname, number
	          of bytes sent, filename, 'a' if transfer was in ASCII mode or
	          'b' if BINARY, _ (meaningless), 'i' if incoming (user uploading)
	          or 'o' if outgoing (user downloading), 'r' (no meaning), user's
	          name, user's group, 1 if user had ident or 0 if not, user's ident
	
	              current-time   transfer-time   remote-host    file-
	          size    filename    transfer-type   special-action-
	          flag   direction    access-mode    username    ser­
	          vice-name    authentication-method   authenticated-
	          user-id   completion-status
	
	example lines:
	Mon Aug 11 14:03:30 2003 20 as1-2-3.ld.bonet.se 15000000 /site/tv-dvdrip/Babylon.5.S03E01.DVDRip.XviD-SFM/babylon5.s03e01.dvdrip.xvid-sfm.r16 b _ i r jh iND 0 *
	Mon Aug 11 14:03:31 2003 33 c-d2b470d5.012-16-67766c2.cust.bredbandsbolaget.se 15000000 /site/tv-dvdrip/Babylon.5.S03E01.DVDRip.XviD-SFM/babylon5.s03e01.dvdrip.xvid-sfm.r15 b _ i r void0 GUD 1 void0
	Mon Aug 11 14:03:44 2003 13 as1-2-3.ld.bonet.se 15000000 /site/tv-dvdrip/Babylon.5.S03E01.DVDRip.XviD-SFM/babylon5.s03e01.dvdrip.xvid-sfm.r17 b _ i r jh iND 0 *
	 */
	static SimpleDateFormat DATE_FMT =
		new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy ", Locale.ENGLISH);
	private PrintStream out;

	public void actionPerformed(TransferEvent event) {
		char direction;
		if (event.getCommand().equals("STOR")) {
			direction = 'i';
		} else if (event.getCommand().equals("RETR")) {
			direction = 'o';
		} else {
			return;
		}

		char transferType;
		if (event.getType() == 'I') { // IMAGE
			transferType = 'b';
		} else if (event.getType() == 'A') { // ASCII
			transferType = 'a';
		} else {
			throw new FatalException("Invalid transfer type");
		}

		char completed = event.isComplete() ? 'c' : 'i';

		out.println(
			DATE_FMT.format(new Date(event.getTime()))
				+ " "
				+ event.getDirectory().getXfertime() / 1000
				+ " "
				+ event.getUserHost()
				+ " "
				+ event.getDirectory().length()
				+ " "
				+ event.getDirectory().getPath()
				+ " "
				+ transferType
				+ " _ "
				+ direction
				+ " r "
				+ event.getUser().getUsername()
				+ " "
				+ event.getUser().getGroupName()
				+ " 0 * "
				+ completed);
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager mgr) {
	}

}
