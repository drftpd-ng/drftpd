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
package net.sf.drftpd.master.usermanager.glftpd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;

import org.apache.log4j.Logger;


/**
 * @author mog
 * @author zubov
 * @version $Id: GlftpdUserManager.java,v 1.20 2004/11/03 05:43:22 zubov Exp $
 */
public class GlftpdUserManager extends UserManager {
    private static final Logger logger = Logger.getLogger(GlftpdUserManager.class.getName());
    private File passwdfile;
    private String userdirpath;
    private File userpathFile;

    /**
     * Constructor for GlftpdUserManager.
     *
     * Used properties:
     *
     * <pre>
     *
     *  glftpd.root
     *  glftpd.users
     *  glftpd.passwd
     *
     * </pre>
     */
    public GlftpdUserManager() {
        this(System.getProperties());
    }

    public GlftpdUserManager(Properties cfg) {
        //		super();
        //this.root = root;
        String glftpdRoot = cfg.getProperty("glftpd.root", ".");
        userdirpath = cfg.getProperty("glftpd.users",
                glftpdRoot + "/ftp-data/users/");
        userpathFile = new File(userdirpath);
        passwdfile = new File(cfg.getProperty("glftpd.passwd",
                    glftpdRoot + "/etc/passwd"));
    }

    public User createUser(String username) {
        throw new UnsupportedOperationException();
    }

    public void delete(String string) {
        throw new UnsupportedOperationException();
    }

    public Collection getAllUsers() throws UserFileException {
        if (!userpathFile.exists()) {
            throw new UserFileException(userpathFile + " not found");
        }

        String[] userpaths = userpathFile.list();
        ArrayList users = new ArrayList();

        for (int i = 0; i < userpaths.length; i++) {
            String userpath = userpaths[i];
            logger.debug(userpath);

            if (userpath.endsWith(".xml")) {
                continue;
            }

            if (!new File(getUserfilepath(userpath)).isFile()) {
                continue;
            }

            if (userpath.endsWith(".user")) { //default.user

                continue;
            }

            logger.debug("add " + userpath);

            try {
                users.add(getUserByName(userpath));

                // throws IOException
            } catch (NoSuchUserException e) {
                throw new UserFileException("", e);
            }
        }

        return users;
    }

    public User getUserByName(String username)
        throws NoSuchUserException, UserFileException {
        User user = getUserByNameUnchecked(username);

        if (user.isDeleted()) {
            throw new NoSuchUserException(user.getUsername() + " is deleted");
        }

        return user;
    }

    public User getUserByNameUnchecked(String username)
        throws UserFileException, NoSuchUserException {
        if (!new File(getUserfilepath(username)).exists()) {
            throw new NoSuchUserException("No userfile for user " + username);
        }

        GlftpdUser user = new GlftpdUser(this, username);

        try {
            load(user);

            //throws CorruptUserFileException, NoSuchUserException
        } catch (IOException e) {
            throw new UserFileException(e);
        }

        return user;
    }

    private String getUserfilepath(String username) {
        return userdirpath + "/" + username;
    }

    void load(User user) throws NoSuchUserException, IOException {
        GlftpdUser gluser = null;

        if (user instanceof GlftpdUser) {
            gluser = (GlftpdUser) user;
        }

        {
            BufferedReader in;

            try {
                in = new BufferedReader(new FileReader(getUserfilepath(
                                user.getUsername())));
            } catch (FileNotFoundException ex) {
                throw new NoSuchUserException(ex.toString());
            }

            //empty "stuff"
            String[] param;

            //empty "stuff"
            String line;

            //empty "stuff"
            String arg;
            int temp = 0;

            try {
                while (true) {
                    try {
                        if ((line = in.readLine()) == null) {
                            break;
                        }

                        param = line.split(" ");
                        arg = line.substring(param[0].length() + 1);
                    } catch (IOException ex) {
                        ex.printStackTrace();

                        break;
                    }

                    if ("USER".equals(param[0])) {
                        user.setComment(arg);

                        //					} else if ("HOMEDIR".equals(param[0])) {
                        //						user.setHomeDirectory(arg);
                    } else if ("GENERAL".equals(param[0])) {
                        // GENERAL: WKLY_ALLOTMENT, IDLE_TIME, MAX_DLSPEED,
                        // MAX_ULSPEED
                        gluser.setWeeklyAllotment(Integer.parseInt(param[1].substring(
                                    2)));

                        int idleTime = Integer.parseInt(param[2]) * 60;

                        if (idleTime < 0) {
                            idleTime = 0;
                        }

                        user.setIdleTime(idleTime);

                        //user.setMaxDownloadRate(Integer.parseInt(param[3]));
                        //user.setMaxUploadRate(Integer.parseInt(param[4]));
                    } else if ("LOGINS".equals(param[0])) {
                        // max logins per account, max logins from the same IP,
                        // max simult. downloads, max simult. uploads -1 is
                        // unlimited
                        user.setMaxLogins(Integer.parseInt(param[1]));
                        user.setMaxLoginsPerIP(Integer.parseInt(param[2]));
                        user.setMaxSimDownloads(Integer.parseInt(param[3]));
                        user.setMaxSimUploads(Integer.parseInt(param[4]));
                    } else if ("FLAGS".equals(param[0])) {
                        if (arg.indexOf('1') != -1) {
                            user.addSecondaryGroup("siteop");
                        }

                        if (arg.indexOf('2') != -1) {
                            user.addSecondaryGroup("gadmin");
                        }

                        if (arg.indexOf('3') != -1) {
                            user.addSecondaryGroup("glock");
                        }

                        if (arg.indexOf('4') != -1) {
                            user.addSecondaryGroup("exempt");
                        }

                        if (arg.indexOf('5') != -1) {
                            user.addSecondaryGroup("color");
                        }

                        if (arg.indexOf('6') != -1) {
                            user.addSecondaryGroup("deleted");
                        }

                        if (arg.indexOf('7') != -1) {
                            user.addSecondaryGroup("useredit");
                        }

                        if (arg.indexOf('8') != -1) {
                            user.addSecondaryGroup("anonymous");
                        }

                        if (arg.indexOf('A') != -1) {
                            user.addSecondaryGroup("nuke");
                        }

                        if (arg.indexOf('B') != -1) {
                            user.addSecondaryGroup("unnuke");
                        }

                        if (arg.indexOf('C') != -1) {
                            user.addSecondaryGroup("undupe");
                        }

                        if (arg.indexOf('D') != -1) {
                            user.addSecondaryGroup("kick");
                        }

                        if (arg.indexOf('E') != -1) {
                            user.addSecondaryGroup("kill");
                        }

                        if (arg.indexOf('F') != -1) {
                            user.addSecondaryGroup("take");
                        }

                        if (arg.indexOf('G') != -1) {
                            user.addSecondaryGroup("give");
                        }

                        if (arg.indexOf('H') != -1) {
                            user.addSecondaryGroup("users");
                        }

                        if (arg.indexOf('J') != -1) {
                            user.addSecondaryGroup("cust1");
                        }

                        if (arg.indexOf('K') != -1) {
                            user.addSecondaryGroup("cust2");
                        }

                        if (arg.indexOf('L') != -1) {
                            user.addSecondaryGroup("cust3");
                        }

                        if (arg.indexOf('M') != -1) {
                            user.addSecondaryGroup("cust4");
                        }

                        if (arg.indexOf('N') != -1) {
                            user.addSecondaryGroup("cust5");
                        }
                    } else if ("TAGLINE".equals(param[0])) {
                        user.setTagline(arg);

                        //} else if ("DIR".equals(param[0])) {
                        // DIR is the start-up dir for this user
                        //	user.setHomeDirectory(arg);
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
                        user.setUploadedBytesMonth(Long.parseLong(param[2]) * 1000);
                        user.setUploadedSecondsMonth(Integer.parseInt(param[3]));
                    } else if ("WKUP".equals(param[0])) {
                        user.setUploadedFilesWeek(Integer.parseInt(param[1]));
                        user.setUploadedBytesWeek(Long.parseLong(param[2]) * 1000);
                        user.setUploadedSecondsWeek(Integer.parseInt(param[3]));
                    } else if ("DAYUP".equals(param[0])) {
                        user.setUploadedFilesDay(Integer.parseInt(param[1]));
                        user.setUploadedBytesDay(Long.parseLong(param[2]) * 1000);
                        user.setUploadedSecondsDay(Integer.parseInt(param[3]));
                    } else if ("ALLDN".equals(param[0])) {
                        user.setDownloadedFiles(Integer.parseInt(param[1]));
                        user.setDownloadedBytes(Long.parseLong(param[2]) * 1000);
                        user.setDownloadedSeconds(Integer.parseInt(param[3]));
                    } else if ("MONTHDN".equals(param[0])) {
                        user.setDownloadedFilesMonth(Integer.parseInt(param[1]));
                        user.setDownloadedBytesMonth(Long.parseLong(param[2]) * 1000);
                        user.setDownloadedSecondsMonth(Integer.parseInt(
                                param[3]));
                    } else if ("WKDN".equals(param[0])) {
                        user.setDownloadedFilesMonth(Integer.parseInt(param[1]));
                        user.setDownloadedBytesMonth(Long.parseLong(param[2]) * 1000);
                        user.setDownloadedSecondsMonth(Integer.parseInt(
                                param[3]));
                    } else if ("DAYDN".equals(param[0])) {
                        user.setDownloadedFilesDay(Integer.parseInt(param[1]));
                        user.setDownloadedBytesDay(Long.parseLong(param[2]) * 1000);
                        user.setDownloadedSecondsDay(Integer.parseInt(param[3]));
                    } else if ("TIME".equals(param[0])) {
                        // TIME: Login Times, Last_On, Time Limit, Time on Today
                        user.setLogins(Integer.parseInt(param[1]));
                        user.setLastAccessTime(Integer.parseInt(param[2]));

                        //user.setTimelimit(Integer.parseInt(param[3]));
                        //user.setTimeToday(Integer.parseInt(param[4]));
                    } else if ("NUKE".equals(param[0])) {
                        // NUKE: Last Nuked, Times Nuked, Total MBytes Nuked
                        user.setLastNuked(Integer.parseInt(param[1]));
                        user.setTimesNuked(Integer.parseInt(param[2]));
                        user.setNukedBytes(Long.parseLong(param[3]) * 1000000);
                    } else if ("SLOTS".equals(param[0])) {
                        //group slots, group leech slots
                        gluser.setGroupSlots(Short.parseShort(param[1]));
                        gluser.setGroupLeechSlots(Short.parseShort(param[2]));
                    } else if ("TIMEFRAME".equals(param[0])) {
                    } else if ("GROUP".equals(param[0])) {
                        if (temp == 0) {
                            user.setGroup(arg);
                            temp = 1;
                        }

                        user.addSecondaryGroup(arg);
                    } else if ("PRIVATE".equals(param[0])) {
                        gluser.addPrivateGroup(arg);
                    } else if ("IP".equals(param[0])) {
                        user.addIPMask(arg);
                    } else if (param[0].startsWith("#")) {
                        //ignore comments
                    } else {
                        logger.info("Unrecognized userfile entry: " + line);
                    }
                }
            } catch (DuplicateElementException e) {
                logger.warn("", e);
            } finally {
                in.close();
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
            } finally {
                in.close();
            }

            if (!foundpassword) {
                logger.warn("couldn't find a password in " + passwdfile);

                return;
            }
        }

        //		return user
    }

    /**
     * @see net.sf.drftpd.master.UserManager#save(User)
     */
    void save(User user) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void saveAll() throws UserFileException {
        throw new UnsupportedOperationException();
    }
}
