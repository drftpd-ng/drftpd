package net.sf.drftpd.master.usermanager;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.util.Crypt;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class GlftpdUserManager extends UserManager {
	public class GlftpdUser extends User {
		private UserManager usermanager;
		/**
		 * Constructor for GlftpdUser.
		 */
		public GlftpdUser(UserManager usermanager, String username) {
			super(username);
			this.usermanager = usermanager;
		}
		private float weeklyAllotment;
		private int slots;
		private String flags;
		private Vector privateGroups = new Vector();
		private String password;

		public void addPrivateGroup(String group) {
			addGroup(group);
			privateGroups.add(group);
		}
		public float getWeeklyAllotment() {
			return weeklyAllotment;
		}
		public void setWeeklyAllotment(float weeklyAllotment) {
			this.weeklyAllotment = weeklyAllotment;
		}

		public boolean login(String userPassword) {
			String userhash =
				Crypt.crypt(this.password.substring(0, 2), userPassword);
			if (password.equals(userhash)) {
				login();
				return true;
			}
			return false;
		}
		/**
		 * Returns the flags.
		 * @return String
		 */
		public String getFlags() {
			return flags;
		}

		/**
		 * Sets the flags.
		 * @param flags The flags to set
		 */
		public void setFlags(String flags) {
			this.flags = flags;
		}

		/**
		 * Returns the slots.
		 * @return float
		 */
		public int getSlots() {
			return slots;
		}

		/**
		 * Sets the slots.
		 * @param slots The slots to set
		 */
		public void setSlots(int slots) {
			this.slots = slots;
		}

		/**
		 * Returns the privateGroups.
		 * @return Vector
		 */
		public Collection getPrivateGroups() {
			return privateGroups;
		}

		/**
		 * Sets the password.
		 * @param password The password to set
		 */
		public void setPassword(String password) {
			this.password = password;
		}

	}
	/**
	 * @author mog
	 *
	 * To change this generated comment edit the template variable "typecomment":
	 * Window>Preferences>Java>Templates.
	 * To enable and disable the creation of type comments go to
	 * Window>Preferences>Java>Code Generation.
	 */
	private String userdirpath;
	private File passwdfile;
	LinkedRemoteFile root;
	/**
	 * Constructor for GlftpdUserManager.
	 */
	public GlftpdUserManager(Properties cfg) {
		//		super();
		this.root = root;
		userdirpath = cfg.getProperty("glftpd.root") + "/ftp-data/users/";
		passwdfile = new File(cfg.getProperty("glftpd.root") + "/etc/passwd");
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#save(User)
	 */
	public void save(User user) {
		System.setProperty("line.separator", "\n");
		getLock(user.getUsername());
		GlftpdUser gluser = null;
		if (user instanceof GlftpdUser)
			gluser = (GlftpdUser) user;
		PrintWriter out;
		try {
			out =
				new PrintWriter(
					new FileWriter(getUserfilepath(user.getUsername())));
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}

		out.println("USER " + user.getComment());
		if (gluser != null) {
			out.println(
				"GENERAL "
					+ Float.toString(gluser.getWeeklyAllotment()).replace(
						'.',
						',')
					+ " "
					+ user.getIdleTime()/60
					+ " "
					+ user.getMaxDownloadRate()
					+ " "
					+ user.getMaxUploadRate());
		} else {
			out.println(
				"GENERAL 0,0 "
					+ user.getIdleTime()/60
					+ " "
					+ user.getMaxDownloadRate()
					+ " "
					+ user.getMaxUploadRate());
		}
		out.println(
			"LOGINS "
				+ user.getMaxLogins()
				+ " "
				+ user.getMaxLoginsPerIP()
				+ " "
				+ user.getMaxSimDownloads()
				+ " "
				+ user.getMaxSimUploads());
		out.println("TIMEFRAME 0 0");
		out.println("FLAGS " + gluser.getFlags());
		out.println("TAGLINE " + user.getTagline());
		out.println("DIR " + user.getHomeDirectory());
		out.println("CREDITS " + user.getCredits() / 1000 + " ");
		//glftpd writes a space at the end
		out.println("RATIO " + (int) user.getRatio() + " ");
		// Xfer information: FILES, KILOBYTES, SECONDS
		out.println(
			"ALLUP "
				+ user.getUploadedFiles()
				+ " "
				+ user.getUploadedBytes() / 1000
				+ " "
				+ user.getUploadedSeconds()
				+ " ");
		out.println(
			"ALLDN "
				+ user.getDownloadedFiles()
				+ " "
				+ user.getDownloadedBytes() / 1000
				+ " "
				+ user.getDownloadedSeconds()
				+ " ");
		out.println(
			"WKUP "
				+ user.getUploadedFilesWeek()
				+ " "
				+ user.getUploadedBytesWeek() / 1000
				+ " "
				+ user.getUploadedSecondsWeek()
				+ " ");
		out.println(
			"WKDN "
				+ user.getDownloadedFilesWeek()
				+ " "
				+ user.getDownloadedBytesWeek() / 1000
				+ " "
				+ user.getDownloadedSecondsWeek()
				+ " ");
		out.println(
			"DAYUP "
				+ user.getUploadedFilesDay()
				+ " "
				+ user.getUploadedBytesDay() / 1000
				+ " "
				+ user.getUploadedSecondsDay()
				+ " ");
		out.println(
			"DAYDN "
				+ user.getDownloadedFilesDay()
				+ " "
				+ user.getDownloadedBytesDay() / 1000
				+ " "
				+ user.getDownloadedSecondsDay()
				+ " ");
		out.println(
			"MONTHUP "
				+ user.getUploadedFilesMonth()
				+ " "
				+ user.getUploadedBytesMonth() / 1000
				+ " "
				+ user.getUploadedSecondsMonth()
				+ " ");
		out.println(
			"MONTHDN "
				+ user.getDownloadedFilesMonth()
				+ " "
				+ user.getDownloadedBytesMonth() / 1000
				+ " "
				+ user.getDownloadedSecondsMonth()
				+ " ");
		// NUKE: Last Nuked, Times Nuked, Total MBytes Nuked
		out.println(
			"NUKE "
				+ user.getLastNuked()
				+ " "
				+ user.getTimesNuked()
				+ " "
				+ (user.getNukedBytes() / 1000000)
				+ " ");
		out.println(
			"TIME "
				+ user.getLogins()
				+ " "
				+ user.getLastAccessTime()
				+ " "
				+ user.getTimelimit()
				+ " "
				+ user.getTimeToday());
		out.println("SLOTS " + gluser.getSlots() + " -1");
		System.out.println("getGroups(): " + user.getGroups());
		{
			Iterator i = user.getGroups().iterator();
			while (i.hasNext())
				out.println("GROUP " + (String) i.next());
		}
		//		out.println("PRIVATE");
		System.out.println("getIpMasks(): " + user.getIpMasks());
		{
			Iterator i = user.getIpMasks().iterator();
			while (i.hasNext())
				out.println("IP " + (String) i.next());
		}
		out.close();
		releaseLock(user.getUsername());
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#delete(String)
	 */
	public void delete(String username) {
	}

	private void load(User user)
		throws CorruptUserFileException, NoSuchUserException {
		GlftpdUser gluser = null;
		if (user instanceof GlftpdUser)
			gluser = (GlftpdUser) user;

		{
			BufferedReader in;
			try {
				in =
					new BufferedReader(
						new FileReader(getUserfilepath(user.getUsername())));
			} catch (FileNotFoundException ex) {
				throw new NoSuchUserException(ex.toString());
			}

			String param[], line, arg;
			try {
				while (true) {
					try {
						if ((line = in.readLine()) == null)
							break;
						param = line.split(" ");
						arg = line.substring(param[0].length() + 1);
					} catch (IOException ex) {
						ex.printStackTrace();
						break;
					}
					if ("USER".equals(param[0])) {
						user.setComment(arg);
					} else if ("HOMEDIR".equals(param[0])) {
						user.setRoot(arg);
					} else if ("GENERAL".equals(param[0])) {
						// GENERAL: WKLY_ALLOTMENT, IDLE_TIME, MAX_DLSPEED, MAX_ULSPEED 
						gluser.setWeeklyAllotment(
							Integer.parseInt(param[1].substring(2)));
						user.setMaxIdleTime(Integer.parseInt(param[2])*60);
						user.setMaxDownloadRate(Integer.parseInt(param[3]));
						user.setMaxUploadRate(Integer.parseInt(param[4]));
					} else if ("LOGINS".equals(param[0])) {
						// max logins per account, max logins from the same IP,
						// max simult. downloads, max simult. uploads
						user.setMaxLogins(Integer.parseInt(param[1]));
						user.setMaxLoginsPerIP(Integer.parseInt(param[2]));
						user.setMaxSimDownloads(Integer.parseInt(param[3]));
						user.setMaxSimUploads(Integer.parseInt(param[4]));
					} else if ("FLAGS".equals(param[0])) {
						gluser.setFlags(arg);
						user.setAdmin(arg.indexOf("1") != -1);
						user.setNuker(arg.indexOf("A") != -1);
						user.setDeleted(arg.indexOf("6") != -1);
						user.setAnonymous(arg.indexOf("8") != -1);
					} else if ("TAGLINE".equals(param[0])) {
						user.setTagline(arg);
					} else if ("DIR".equals(param[0])) {
						// DIR is the start-up dir for this user
						user.setHomeDirectory(arg);
					} else if ("CREDITS".equals(param[0])) {
						user.setCredits(Long.parseLong(param[1]) * 1000);
					} else if ("RATIO".equals(param[0])) {
						user.setRatio(Float.parseFloat(param[1]));

						// Xfer information: FILES, KILOBYTES, SECONDS
					} else if ("ALLUP".equals(param[0])) {
						user.setUploadedFiles(Integer.parseInt(param[1]));
						user.setUploadedBytes(Long.parseLong(param[2]) * 1000);
						user.setUploadedSeconds(Integer.parseInt(param[3]));
					} else if ("MONTHUP".equals(param[0])) {
						user.setUploadedFilesMonth(Integer.parseInt(param[1]));
						user.setUploadedBytesMonth(
							Long.parseLong(param[2]) * 1000);
						user.setUploadedSecondsMonth(
							Integer.parseInt(param[3]));
					} else if ("WKUP".equals(param[0])) {
						user.setUploadedFilesWeek(Integer.parseInt(param[1]));
						user.setUploadedBytesWeek(
							Long.parseLong(param[2]) * 1000);
						user.setUploadedSecondsWeek(Integer.parseInt(param[3]));
					} else if ("DAYUP".equals(param[0])) {
						user.setUploadedFilesDay(Integer.parseInt(param[1]));
						user.setUploadedBytesDay(
							Long.parseLong(param[2]) * 1000);
						user.setUploadedSecondsDay(Integer.parseInt(param[3]));

					} else if ("ALLDN".equals(param[0])) {
						user.setDownloadedFiles(Integer.parseInt(param[1]));
						user.setDownloadedBytes(
							Long.parseLong(param[2]) * 1000);
						user.setDownloadedSeconds(Integer.parseInt(param[3]));
					} else if ("MONTHDN".equals(param[0])) {
						user.setDownloadedFilesMonth(
							Integer.parseInt(param[1]));
						user.setDownloadedBytesMonth(
							Long.parseLong(param[2]) * 1000);
						user.setDownloadedSecondsMonth(
							Integer.parseInt(param[3]));
					} else if ("WKDN".equals(param[0])) {
						user.setDownloadedFilesMonth(
							Integer.parseInt(param[1]));
						user.setDownloadedBytesMonth(
							Long.parseLong(param[2]) * 1000);
						user.setDownloadedSecondsMonth(
							Integer.parseInt(param[3]));
					} else if ("DAYDN".equals(param[0])) {
						user.setDownloadedFilesDay(Integer.parseInt(param[1]));
						user.setDownloadedBytesDay(
							Long.parseLong(param[2]) * 1000);
						user.setDownloadedSecondsDay(
							Integer.parseInt(param[3]));

					} else if ("TIME".equals(param[0])) {
						// TIME: Login Times, Last_On, Time Limit, Time on Today
						user.setLogins(Integer.parseInt(param[1]));
						user.setLastAccessTime(Integer.parseInt(param[2]));
						user.setTimelimit(Integer.parseInt(param[3]));
						user.setTimeToday(Integer.parseInt(param[4]));
					} else if ("NUKE".equals(param[0])) {
						// NUKE: Last Nuked, Times Nuked, Total MBytes Nuked
						user.setLastNuked(Integer.parseInt(param[1]));
						user.setTimesNuked(Integer.parseInt(param[2]));
						user.setNukedBytes(Long.parseLong(param[3]) * 1000000);
					} else if ("SLOTS".equals(param[0])) {
						gluser.setSlots(Integer.parseInt(param[1]));
					} else if ("TIMEFRAME".equals(param[0])) {
					} else if ("GROUP".equals(param[0])) {
						user.addGroup(arg);
					} else if ("PRIVATE".equals(param[0])) {
						gluser.addPrivateGroup(arg);
					} else if ("IP".equals(param[0])) {
						user.addIPMask(arg);
					} else if (param[0].startsWith("#")) {
						//ignore comments
					} else {
						System.out.print("Unknown userfile entry: ");
						for (int i = 0; i < param.length; i++) {
							System.out.print(param[i] + ", ");
						}
						System.out.println();
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				releaseLock(user.getUsername());
			}
		}
		{
			BufferedReader in;
			try {
				in = new BufferedReader(new FileReader(passwdfile));
			} catch (FileNotFoundException ex) {
				throw new RuntimeException(ex.toString());
			}
			String line;
			boolean foundpassword = false;
			try {
				while ((line = in.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(line, ":");
					String username2 = st.nextToken();
					if (user.getUsername().equals(username2)) {
						gluser.setPassword(st.nextToken());
						foundpassword = true;
						break;
					}
				}
				System.out.println(line);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			if (!foundpassword) {
				System.out.println("didn't find a password in " + passwdfile);
				return;
			}
		}
		//		return user
	}
	/**
	 * @see net.sf.drftpd.master.UserManager#getUserByName(String)
	 */
	public User getUserByName(String username) throws NoSuchUserException {
		getLock(username);
		GlftpdUser user = new GlftpdUser(this, username);
		try {
			load(user);
		} catch(CorruptUserFileException ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.toString());
		}
		return user;
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#getAllUserNames()
	 */
	public Collection getAllUserNames() {
		return null;
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#exists(String)
	 */
	public boolean exists(String name) {
		return false;
	}


	private void getLock(String username) {
		File lock = new File(userdirpath + "/" + username + ".lock");
		waitForLock(username);
		try {
			lock.createNewFile();
		} catch (IOException ex) {
			throw new RuntimeException(
				"Could not create lock file\n" + ex.toString());
		}
		//this causes the VM to crash at exit (sun jdk 1.4.0)..
		//lock.deleteOnExit();
	}
	private void releaseLock(String username) {
		File lock = new File(userdirpath + "/" + username + ".lock");
		lock.delete();
	}
	private void waitForLock(String username) {
		File lock = new File(userdirpath + "/" + username + ".lock");
		int yields = 0;
		long millis = 0L;
		if (lock.exists())
			millis = System.currentTimeMillis();
		while (lock.exists()) {
			//			try {
			//				Thread.sleep(100L);
			yields++;
			Thread.yield();
			//			} catch(InterruptedException ex) {}
		}
		if (millis != 0) {
			System.out.println(
				"Spent "
					+ (System.currentTimeMillis() - millis)
					+ " ms waiting for lock (yielded "
					+ yields
					+ " times)");
		}
	}
	private String getUserfilepath(String username) {
		return userdirpath + "/" + username;
	}
}
