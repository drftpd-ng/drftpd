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
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class GlftpdLog implements FtpListener {
	PrintWriter out;
	public GlftpdLog(File logfile) throws IOException {
		out = new PrintWriter(new FileWriter(logfile)); // throws IOException
	}
	private static Logger logger = Logger.getLogger(FtpListener.class.getName());
	static {
		logger.setLevel(Level.ALL);
	}

	public void actionPerformed(FtpEvent event) {
		// Sun Jun 29 21:15:26 2003 NUKE: "/site/tv/Seinfeld.S03E04.The.Dog.PDTV.DivX-LOL" "void0" "kaldur" "2 62.6" "tv.04"
		User user = event.getUser();
	
		if (event.getCommand().equals("SITE NUKE")) {
			NukeEvent nevent = (NukeEvent) event;
			Map nukees = nevent.getNukees();
			logger.fine("nukees"+nukees.size()+": "+nukees);
			for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {
				//User nukee = (User) iter.next();
				String nukeeUsername = (String)iter.next();
				Long amount = (Long)nukees.get(nukeeUsername);
				if(nukeeUsername == null) {
					nukeeUsername = "<unknown>";
				} else {
					//nukeeUsername = nukee.getUsername();
				}
				print(
					"NUKE: \""
						+ nevent.getDirectory()
						+ "\" \""
						+ user.getUsername()
						+ "\" \""
						+ nukeeUsername
						+ "\" \""
						+ nevent.getMultiplier()
						+ " "
						+ amount
						+ "\" \""
						+ nevent.getReason()
						+ "\"");

			}

		} else if(event.getCommand().equals("SITE UNNUKE")) {
			NukeEvent nevent = (NukeEvent) event;
			Map nukees = nevent.getNukees();
			logger.fine("nukees"+nukees.size()+": "+nukees);
			for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {
				//User nukee = (User) iter.next();
				String nukeeUsername = (String)iter.next();
				Long amount = (Long)nukees.get(nukeeUsername);
				if(nukeeUsername == null) {
					nukeeUsername = "<unknown>";
				} else {
					//nukeeUsername = nukee.getUsername();
				}
				print(
					"UNNUKE: \""
						+ nevent.getDirectory()
						+ "\" \""
						+ user.getUsername()
						+ "\" \""
						+ nukeeUsername
						+ "\" \""
						+ nevent.getMultiplier()
						+ " "
						+ amount
						+ "\" \""
						+ nevent.getReason()
						+ "\"");

			}
			
			//Tue Feb 25 03:30:33 2003 UNNUKE: "/site/tv/7th.Heaven.7x17.PDTV.SVCD.SD-6" "void0" "AZToR" "3 320.5" "None"
			//Tue Feb 25 03:30:33 2003 UNNUKE: "/site/tv/7th.Heaven.7x17.PDTV.SVCD.SD-6" "void0" "blademaster" "3 484.1" "None"
			
		}
	}
	DateFormat DATE_FMT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy ", Locale.ENGLISH);
	public void print(String line) {
		print(new Date(), line);
	}
	public void print(Date date, String line) {
		String str;
		out.println(str = DATE_FMT.format(date) + line);
		out.flush();
		System.out.println(str);		
	}
}
