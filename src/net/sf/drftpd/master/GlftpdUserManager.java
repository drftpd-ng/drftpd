package net.sf.drftpd.master;

import java.util.Collection;
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

		/**
		 * Constructor for GlftpdUser.
		 */
		public GlftpdUser() {
			super();
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
			System.out.println("this.password == "+this.password);
			String userhash = Crypt.crypt(this.password.substring(0, 2), userPassword);
			System.out.println("userhash: "+userhash);
			System.out.println("hash: "+password);
			return password.equals(userhash);
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
	public void save(User user) throws Exception {
		/*
		File lock = new File(userdirpath + user.getName() + ".lock");
		while (lock.exists()) {
			System.out.println(
				user.getName() + "'s userfile is locked, sleeping.");
			Thread.sleep(100);
		}
		if (!lock.createNewFile()) {
			throw new RuntimeException("Cannot create lock file: " + lock);
		}
		lock.deleteOnExit();
		PrintWriter out =
			new PrintWriter(
				new FileOutputStream(userdirpath + user.getName()));
		
		out.println("USER "+user.getComment());
		//GENERAL GENERAL: WKLY_ALLOTMENT, IDLE_TIME, MAX_DLSPEED, MAX_ULSPEED
		out.println("GENERAL 0,0 "+user.getMaxIdleTime()+" "+user.getMaxDownloadRate()+" "+user.getMaxUploadRate());
		//LOGINS <num_logins>
		//max logins per account, max logins from the same IP, max simult. downloads, max simult. uploads
		out.println("LOGINS 3 0 -1 -1");
		out.println("TIMEFRAME 0 0");
		out.print("FLAGS ");
		//		if(user.
		out.println("FLAGS 15ABCDFGHIL");
		out.println("TAGLINE The Internet is my LAN");
		out.println("DIR /");
		out.println("CREDITS 2937670");
		out.println("RATIO 3");
		out.println("ALLUP 68 974510 1834");
		out.println("ALLDN 3854 60665004 77316");
		out.println("WKUP 0 0 0");
		out.println("WKDN 0 0 0");
		out.println("DAYUP 0 0 0");
		out.println("DAYDN 0 0 0");
		out.println("MONTHUP 2 0 2");
		out.println("MONTHDN 530 9915436 7980");
		out.println("NUKE 1026611111 1 568");
		out.println("TIME 680 1026911996 0 708");
		out.println("SLOTS -1 -1");
		out.println("GROUP STAFF");
		out.println("PRIVATE STAFF");
		out.println("IP *@mail.gw.nynashamn.se");
		out.println("IP *@*.043-58-73746f31.cust.bredbandsbolaget.se");
		out.println("IP *@127.0.0.1");
		out.println("IP *@193.111.132.20");
		*/
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#delete(String)
	 */
	public void delete(String username) throws Exception {
	}

	/**
	 * @see net.sf.drftpd.master.UserManager#getUserByName(String)
	 */
	public User getUserByName(String username) {
		getLock(username);
		GlftpdUser user = new GlftpdUser();
		user.setUsername(username);

		{
			BufferedReader in;
			try {
				in =
					new BufferedReader(
						new FileReader(getUserfilepath(username)));
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
				return null;
			}
			String param[], line, arg;
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
					user.setWeeklyAllotment(
						Integer.parseInt(param[1].substring(2)));
					user.setMaxIdleTime(Integer.parseInt(param[2]));
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
					user.setFlags(arg);
					user.setAdmin(arg.indexOf("1") != -1);
					user.setNuker(arg.indexOf("A") != -1);
					user.setDeleted(arg.indexOf("6") != -1);
					user.setAnonymous(arg.indexOf("8") != -1);
				} else if ("TAGLINE".equals(param[0])) {
					user.setTagline(arg);
				} else if ("DIR".equals(param[0])) {
					// DIR is the start-up dir for this user
					user.setHomeDirectory(arg);
				} else if ("RATIO".equals(param[0])) {
					user.setRatio(Float.parseFloat(param[1]));
				} else if ("CREDITS".equals(param[0])) {
					user.setCredits(Long.parseLong(param[1]));

					// statistics
				} else if ("ALLUP".equals(param[0])) {
					user.setUploadedBytes(Integer.parseInt(param[1]));
				} else if ("ALLDN".equals(param[0])) {
					user.setDownloadedBytes(Integer.parseInt(param[1]));
				} else if ("WKUP".equals(param[0])) {
					user.setUploadedBytesWeek(Integer.parseInt(param[1]));
				} else if ("WKDN".equals(param[0])) {
					user.setDownloadedBytesWeek(Integer.parseInt(param[1]));
				} else if ("DAYUP".equals(param[0])) {
					user.setUploadedBytesDay(Integer.parseInt(param[1]));
				} else if ("DAYDN".equals(param[0])) {
					user.setDownloadedBytesDay(Integer.parseInt(param[1]));
				} else if ("MONTHUP".equals(param[0])) {
					user.setUploadedBytesMonth(Integer.parseInt(param[1]));
				} else if ("MONTHDN".equals(param[0])) {
					user.setDownloadedBytesMonth(Integer.parseInt(param[1]));
				} else if ("TIME".equals(param[0])) {
					// TIME: Login Times, Last_On, Time Limit, Time on Today
					user.setLogins(Integer.parseInt(param[1]));
					user.setLastAccessTime(Integer.parseInt(param[2]));
					user.setTimelimit(Integer.parseInt(param[3]));
					user.setTimeToday(Integer.parseInt(param[4]));
				} else if ("NUKE".equals(param[0])) {
					// NUKE: Last Nuked, Times Nuked, Total MBytes Nuked
					user.setLastNuked(Integer.parseInt(param[1]));
					user.setNuked(Integer.parseInt(param[2]));
					user.setNukedBytes(Long.parseLong(param[3]));
				} else if ("SLOTS".equals(param[0])) {
					user.setSlots(Integer.parseInt(param[1]));
				} else if ("TIMEFRAME".equals(param[0])) {
				} else if ("GROUP".equals(param[0])) {
					user.addGroup(arg);
				} else if ("PRIVATE".equals(param[0])) {
					user.addPrivateGroup(arg);
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
			releaseLock(username);
		}
		{
			BufferedReader in;
			try {
				in = new BufferedReader(new FileReader(passwdfile));
			} catch (FileNotFoundException ex) {
				throw new RuntimeException(ex.toString());
			}
			String line;
			boolean foundpassword=false;
			try {
				while ((line = in.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(line, ":");
					String username2 = st.nextToken();
					System.out.println("comparing "+user.getUsername()+" and "+username2);
					if (user.getUsername().equals(username2)) {
						user.setPassword(st.nextToken());
						foundpassword = true;
						break;
					}
				}
				System.out.println(line);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			if(!foundpassword) {
				System.out.println("didn't find a password in "+passwdfile);
				return null;
			}
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

	/**
	 * @see net.sf.drftpd.master.UserManager#authenticate(String, String)
	 */
	public boolean authenticate(String login, String password) {
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
		lock.deleteOnExit();
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
