package net.sf.drftpd.master.usermanager;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;

import net.sf.drftpd.DuplicateElementException;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class UserManagerConverter {

	private static Logger logger = Logger.getLogger(UserManagerConverter.class);

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, UserFileException {
		if(args.length != 2) {
			System.out.println("arguments: <from usermanager class> <to usermanager class>");
		}
		UserManager from = (UserManager)Class.forName(args[0]).newInstance();
		UserManager to = (UserManager)Class.forName(args[1]).newInstance();
		for (Iterator iter = from.getAllUsers().iterator(); iter.hasNext();) {
			User user = (User) iter.next();
			convert(user, to.create(user.getUsername()));
		}
	}

	public static void convert(User from, User to) {
		for (Iterator iter = from.getGroups().iterator(); iter.hasNext();) {
			try {
				to.addGroup((String) iter.next());
			} catch (DuplicateElementException e) {
				logger.warn("", e);
			}
		}

		for (Iterator iter = from.getIpMasks().iterator(); iter.hasNext();) {
			String ipmask = (String) iter.next();
			try {
				to.addIPMask(ipmask);
			} catch (DuplicateElementException e) {
				logger.warn("", e);
			}
		}	

		to.setComment(from.getComment());

		to.setCredits(from.getCredits());
		
		to.setDeleted(from.isDeleted());

		to.setGroup(from.getGroupName());

		to.setGroupLeechSlots(from.getGroupLeechSlots());

		to.setGroupSlots(from.getGroupLeechSlots());

		to.setIdleTime(from.getIdleTime());

		to.setLastAccessTime(from.getLastAccessTime());

		to.setLastNuked(from.getLastNuked());

		to.setLogins(from.getLogins());

		to.setMaxDownloadRate(from.getMaxDownloadRate());

		to.setMaxLogins(from.getMaxLogins());

		to.setMaxLoginsPerIP(from.getMaxLoginsPerIP());

		to.setMaxSimDownloads(from.getMaxSimDownloads());

		to.setMaxSimUploads(from.getMaxSimUploads());

		to.setMaxUploadRate(from.getMaxUploadRate());

		to.setNukedBytes(from.getNukedBytes());

		if(from instanceof PlainTextPasswordUser) {
			to.setPassword(((PlainTextPasswordUser)from).getPassword());
		} else {
			logger.warn("Don't know how to convert password from "+from.getUsername());
		}

		to.setRatio(from.getRatio());
	
		to.setTagline(from.getTagline());

		to.setTimelimit(from.getTimelimit());

		to.setTimesNuked(from.getTimesNuked());

		to.setTimeToday(from.getTimeToday());



	}
}
