/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class GlftpdLog implements FtpListener {
	PrintWriter out;
	public GlftpdLog(File logfile) throws IOException {
		out = new PrintWriter(new FileWriter(logfile)); // throws IOException
	}

	public void actionPerformed(FtpEvent event) {
		// Sun Jun 29 21:15:26 2003 NUKE: "/site/tv/Seinfeld.S03E04.The.Dog.PDTV.DivX-LOL" "void0" "kaldur" "2 62.6" "tv.04"
		User user = event.getUser();

		if (event.getCommand().equals("NUKE")) {
			NukeEvent nevent = (NukeEvent) event;
			Map nukees = nevent.getNukees();
			for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {
				User nukee = (User) iter.next();
				print(
					"NUKE: \""
						+ nevent.getDirectory()
						+ "\" \""
						+ user.getUsername()
						+ "\" \""
						+ nukee.getUsername()
						+ "\" \""
						+ nevent.getMultiplier()
						+ " "
						+ nukees.get(nukee)
						+ "\" \""
						+ nevent.getReason()
						+ "\"");

			}

		} else if(event.getCommand().equals("UNNUKE")) {
			//Tue Feb 25 03:30:33 2003 UNNUKE: "/site/tv/7th.Heaven.7x17.PDTV.SVCD.SD-6" "void0" "AZToR" "3 320.5" "None"
			//Tue Feb 25 03:30:33 2003 UNNUKE: "/site/tv/7th.Heaven.7x17.PDTV.SVCD.SD-6" "void0" "blademaster" "3 484.1" "None"
			
		}
	}
	DateFormat DATE_FMT = new SimpleDateFormat("EEE, MMM d, HH:mm:ss yyyy ");
	public void print(String line) {
		out.println(DATE_FMT.format(new Date()) + line);
	}
}
