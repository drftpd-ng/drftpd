package net.sf.drftpd.master.usermanager.glftpd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.CorruptUserFileException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class GlftpdUserManager implements UserManager {
	private static Logger logger =
		Logger.getLogger(GlftpdUserManager.class.getName());

	File userpathFile;

	Hashtable users = new Hashtable();

	private String userdirpath;
	private File passwdfile;
	LinkedRemoteFile root;
	/**
	 * Constructor for GlftpdUserManager.
	 */
	public GlftpdUserManager(Properties cfg) {
		//		super();
		//this.root = root;
		userdirpath =
			cfg.getProperty(
				"glftpd.users",
				cfg.getProperty("glftpd.root") + "/ftp-data/users/");
		userpathFile = new File(userdirpath);
		passwdfile =
			new File(
				cfg.getProperty(
					"glftpd.passwd",
					cfg.getProperty("glftpd.root") + "/etc/passwd"));
	}

	/**
	 * @deprecated
	 */
	public GlftpdUserManager() {
		this(System.getProperties());
	}
	/**
	 * @see net.sf.drftpd.master.UserManager#save(User)
	 */
	void save(User user) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see net.sf.drftpd.master.usermanager.UserManager#create(java.lang.String)
	 */
	public User create(String username) {
		throw new UnsupportedOperationException();
	}

	void load(User user) throws CorruptUserFileException, NoSuchUserException {

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

			//empty "stuff"

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
						user.setHomeDirectory(arg);
					} else if ("GENERAL".equals(param[0])) {
						// GENERAL: WKLY_ALLOTMENT, IDLE_TIME, MAX_DLSPEED, MAX_ULSPEED 
						gluser.setWeeklyAllotment(
							Integer.parseInt(param[1].substring(2)));
						int idleTime = Integer.parseInt(param[2]) * 60;
						if (idleTime < 0)
							idleTime = 0;
						user.setIdleTime(idleTime);
						user.setMaxDownloadRate(Integer.parseInt(param[3]));
						user.setMaxUploadRate(Integer.parseInt(param[4]));
					} else if ("LOGINS".equals(param[0])) {
						// max logins per account, max logins from the same IP,
						
						// max simult. downloads, max simult. uploads -1 is unlimited
						user.setMaxLogins(Integer.parseInt(param[1]));
						user.setMaxLoginsPerIP(Integer.parseInt(param[2]));
						user.setMaxSimDownloads(Integer.parseInt(param[3]));
						user.setMaxSimUploads(Integer.parseInt(param[4]));
					} else if ("FLAGS".equals(param[0])) {
						if (arg.indexOf('1') != -1)
							user.addGroup("siteop");
						if (arg.indexOf('2') != -1)
							user.addGroup("gadmin");
						if (arg.indexOf('3') != -1)
							user.addGroup("glock");
						if (arg.indexOf('4') != -1)
							user.addGroup("exempt");
						if (arg.indexOf('5') != -1)
							user.addGroup("color");
						if (arg.indexOf('6') != -1)
							user.addGroup("deleted");
						if (arg.indexOf('7') != -1)
							user.addGroup("useredit");
						if (arg.indexOf('8') != -1)
							user.addGroup("anonymous");
						if (arg.indexOf('A') != -1)
							user.addGroup("nuke");
						if (arg.indexOf('B') != -1)
							user.addGroup("unnuke");
						if (arg.indexOf('C') != -1)
							user.addGroup("undupe");
						if (arg.indexOf('D') != -1)
							user.addGroup("kick");
						if (arg.indexOf('E') != -1)
							user.addGroup("kill");
						if (arg.indexOf('F') != -1)
							user.addGroup("take");
						if (arg.indexOf('G') != -1)
							user.addGroup("give");
						if (arg.indexOf('H') != -1)
							user.addGroup("users");
						if (arg.indexOf('J') != -1)
							user.addGroup("cust1");
						if (arg.indexOf('K') != -1)
							user.addGroup("cust2");
						if (arg.indexOf('L') != -1)
							user.addGroup("cust3");
						if (arg.indexOf('M') != -1)
							user.addGroup("cust4");
						if (arg.indexOf('N') != -1)
							user.addGroup("cust5");
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
						/*} else if ("ALLUP".equals(param[0])) {
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
						*/
					} else if ("SLOTS".equals(param[0])) {
						//group slots, group leech slots
						gluser.setGroupSlots(Short.parseShort(param[1]));
						gluser.setGroupLeechSlots(Short.parseShort(param[2]));
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
						logger.info("Unrecognized userfile entry: "+line);
					}
				}
			} catch (Exception ex) {
				logger.warn("", ex);
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
						gluser.setUnixPassword(st.nextToken());
						foundpassword = true;
						break;
					}
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			if (!foundpassword) {
				logger.warn("couldn't find a password in " + passwdfile);
				return;
			}
		}
		//		return user
	}
	/**
	 * @throws NoSuchUserException, CorruptUserFileException
	 */
	public User getUserByNameUnchecked(String username)
		throws IOException, NoSuchUserException {
		if (!new File(getUserfilepath(username)).exists()) {
			throw new NoSuchUserException("No userfile for user " + username);
		}
		GlftpdUser user = new GlftpdUser(this, username);
		load(user); //throws CorruptUserFileException, NoSuchUserException
		return user;
	}

	public boolean exists(String name) {
		throw new UnsupportedOperationException();
	}

	private String getUserfilepath(String username) {
		return userdirpath + "/" + username;
	}

	public void saveAll() throws UserFileException {
		throw new UnsupportedOperationException();
	}
	public Collection getAllUsersByGroup(String group) throws IOException {
		Collection users = getAllUsers();
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			GlftpdUser user = (GlftpdUser) iter.next();
			if (!user.getGroupName().equals(group))
				iter.remove();
		}
		return users;
	}
	public List getAllUsers() throws IOException {
		System.out.println("userpathFile = " + userpathFile);
		if (!userpathFile.exists())
			throw new FileNotFoundException(userpathFile + " not found");
		String userpaths[] = userpathFile.list();
		ArrayList users = new ArrayList();

		for (int i = 0; i < userpaths.length; i++) {
			String userpath = userpaths[i];
			logger.debug(userpath);
			if (userpath.endsWith(".xml"))
				continue;
			if (!new File(getUserfilepath(userpath)).isFile())
				continue;
			if (userpath.endsWith(".user"))  //default.user
				continue;
			logger.debug("add " + userpath);
			try {
				users.add(getUserByName(userpath));
				// throws IOException
			} catch (NoSuchUserException e) {
				throw (IOException) new IOException().initCause(e);
			}
		}
		return users;
	}
	public Collection getAllGroups() throws IOException {
		throw new UnsupportedOperationException();

		/*Collection users = this.getAllUsers();
		ArrayList ret = new ArrayList();
		
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			Collection myGroups = myUser.getGroups();
			for (Iterator iterator = myGroups.iterator();
				iterator.hasNext();
				) {
				String myGroup = (String) iterator.next();
				if (!ret.contains(myGroup))
					ret.add(myGroup);
			}
		}
		
		return ret;
		*/
	}

	public void init(ConnectionManager mgr) {
	}

	public User getUserByName(String username) throws NoSuchUserException, IOException {
		User user = getUserByNameUnchecked(username);
		if(user.isDeleted()) throw new NoSuchUserException(user.getUsername()+" is deleted");
		return user;
	}

}
