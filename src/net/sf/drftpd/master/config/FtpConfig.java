/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.config;

import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;

import org.drftpd.GlobalContext;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UserManagment;

import org.drftpd.master.ConnectionManager;
import org.drftpd.slave.Slave;

import org.drftpd.usermanager.User;

import com.Ostermiller.util.StringTokenizer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class FtpConfig {
    private static final Logger logger = Logger.getLogger(FtpConfig.class);
    private ArrayList<InetAddress> _bouncerIps;
    private boolean _capFirstDir;
    private boolean _capFirstFile;
    private String _cfgFileName;
    protected ConnectionManager _connManager;
    private ArrayList _creditcheck;
    private ArrayList _creditloss;
    private boolean _hideIps;
    private boolean _isLowerDir;
    private boolean _isLowerFile;
    private String _loginPrompt = Slave.VERSION + " http://drftpd.org";
    private int _maxUsersExempt;
    private int _maxUsersTotal = Integer.MAX_VALUE;
    private ArrayList _msgpath;
    private Hashtable<String, ArrayList> _patternPaths;
    private Hashtable<String, Permission> _permissions;
    private StringTokenizer _replaceDir;
    private StringTokenizer _replaceFile;
    private long _slaveStatusUpdateTime;
    private boolean _useDirNames;
    private boolean _useFileNames;
    private String newConf = "conf/perms.conf";
    protected PortRange _portRange;
	private Permission _shutdown;

    protected FtpConfig() {
    }

    /**
     * Constructor that allows reusing of cfg object
     */
    public FtpConfig(Properties cfg, String cfgFileName,
        ConnectionManager connManager) throws IOException {
        _cfgFileName = cfgFileName;
        loadConfig(cfg, connManager);
    }

    private static ArrayList makeRatioPermission(ArrayList<RatioPathPermission> arr,
        StringTokenizer st) throws MalformedPatternException {
        arr.add(new RatioPathPermission(new GlobCompiler().compile(
                    st.nextToken()), Float.parseFloat(st.nextToken()),
                makeUsers(st)));

        return arr;
    }

    public static ArrayList<String> makeUsers(Enumeration st) {
        ArrayList<String> users = new ArrayList<String>();

        while (st.hasMoreElements()) {
            users.add((String) st.nextElement());
        }

        return users;
    }

    public boolean checkDelete(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("delete", fromUser, path);
    }

    public boolean checkDeleteOwn(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("deleteown", fromUser, path);
    }

    public boolean checkDenyDataUnencrypted(User user) {
        return checkPermission("denydatauncrypted", user);
    }

    public boolean checkDenyDirUnencrypted(User user) {
        return checkPermission("denydiruncrypted", user);
    }

    /**
     * Returns true if the path has dirlog enabled.
     * @param fromUser The user who created the log event.
     * @param path The path in question.
     * @return true if the path has dirlog enabled.
     */
    public boolean checkDirLog(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("dirlog", fromUser, path);
    }

    /**
     * Also checks privpath for permission
     * @return true if fromUser is allowed to download the file path
     */
    public boolean checkDownload(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("download", fromUser, path);
    }

    public boolean checkGive(User user) {
        return checkPermission("give", user);
    }

    /**
     * @return true if fromUser should be hidden
     */
    public boolean checkHideInWho(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("hideinwho", fromUser, path);
    }

    /**
     * @return true if fromUser is allowed to mkdir in path
     */
    public boolean checkMakeDir(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("makedir", fromUser, path);
    }

    public boolean checkPathPermission(String key, User fromUser,
        LinkedRemoteFileInterface path) {
        return checkPathPermission(key, fromUser, path, false);
    }

    public boolean checkPathPermission(String key, User fromUser,
        LinkedRemoteFileInterface path, boolean defaults) {
        Collection coll = ((Collection) _patternPaths.get(key));

        if (coll == null) {
            return defaults;
        }

        Iterator iter = coll.iterator();

        while (iter.hasNext()) {
            PathPermission perm = (PathPermission) iter.next();

            if (perm.checkPath(path)) {
                return perm.check(fromUser);
            }
        }

        return defaults;
    }

    private boolean checkPermission(String key, User user) {
        Permission perm = _permissions.get(key);

        return (perm == null) ? false : perm.check(user);
    }

    /**
     * @return true if user fromUser is allowed to see path
     */
    public boolean checkPrivPath(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("privpath", fromUser, path, true);
    }

    public boolean checkRename(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("rename", fromUser, path);
    }

    public boolean checkRenameOwn(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("renameown", fromUser, path);
    }

    public boolean checkTake(User user) {
        return checkPermission("take", user);
    }

    /**
     * @return true if fromUser is allowed to upload in directory path
     */
    public boolean checkUpload(User fromUser, LinkedRemoteFileInterface path) {
        return checkPathPermission("upload", fromUser, path);
    }

    public boolean checkUserRejectInsecure(User user) {
        return checkPermission("userrejectinsecure", user);
    }

    public boolean checkUserRejectSecure(User user) {
        return checkPermission("userrejectsecure", user);
    }

    public void directoryMessage(Reply response, User user,
        LinkedRemoteFileInterface dir) {
        for (Iterator iter = _msgpath.iterator(); iter.hasNext();) {
            MessagePathPermission perm = (MessagePathPermission) iter.next();

            if (perm.checkPath(dir)) {
                if (perm.check(user)) {
                    perm.printMessage(response);
                }
            }
        }
    }

    /**
     * @return Returns the bouncerIp.
     */
    public List getBouncerIps() {
        return _bouncerIps;
    }

    public ConnectionManager getConnectionManager() {
        if (_connManager == null) {
            throw new NullPointerException();
        }

        return _connManager;
    }

    public float getCreditCheckRatio(LinkedRemoteFileInterface path,
        User fromUser) {
        for (Iterator iter = _creditcheck.iterator(); iter.hasNext();) {
            RatioPathPermission perm = (RatioPathPermission) iter.next();

            if (perm.checkPath(path)) {
                if (perm.check(fromUser)) {
                    return perm.getRatio();
                }

                return fromUser.getObjectFloat(UserManagment.RATIO);
            }
        }

        return fromUser.getObjectFloat(UserManagment.RATIO);
    }

    public float getCreditLossRatio(LinkedRemoteFileInterface path,
        User fromUser) {
        for (Iterator iter = _creditloss.iterator(); iter.hasNext();) {
            RatioPathPermission perm = (RatioPathPermission) iter.next();

            if (perm.checkPath(path)) {
                if (perm.check(fromUser)) {
                    return perm.getRatio();
                }
            }
        }

        //default credit loss ratio is 1
        return (fromUser.getObjectFloat(UserManagment.RATIO) == 0) ? 0 : 1;
    }

    public String getDirName(String name) {
        if (!_useDirNames) {
            return name;
        }

        String temp = new String(name);

        if (_isLowerDir) {
            temp = temp.toLowerCase();
        } else {
            temp = temp.toUpperCase();
        }

        if (_capFirstDir) {
            temp = temp.substring(0, 1).toUpperCase() +
                temp.substring(1, temp.length());
        }

        return replaceName(temp, _replaceDir);
    }

    public String getFileName(String name) {
        if (!_useFileNames) {
            return name;
        }

        String temp = new String(name);

        if (_isLowerFile) {
            temp = temp.toLowerCase();
        } else {
            temp = temp.toUpperCase();
        }

        if (_capFirstFile) {
            temp = temp.substring(0, 1).toUpperCase() +
                temp.substring(1, temp.length());
        }

        return replaceName(temp, _replaceFile);
    }

    public GlobalContext getGlobalContext() {
        return getConnectionManager().getGlobalContext();
    }

    public boolean getHideIps() {
        return _hideIps;
    }

    public String getLoginPrompt() {
        return _loginPrompt;
    }

    public int getMaxUsersExempt() {
        return _maxUsersExempt;
    }

    public int getMaxUsersTotal() {
        return _maxUsersTotal;
    }

    public long getSlaveStatusUpdateTime() {
        return _slaveStatusUpdateTime;
    }

    public void loadConfig(Properties cfg, ConnectionManager connManager)
        throws IOException {
        loadConfig2(new FileReader(newConf));
        if (_portRange == null) {
            //default portrange if none specified
            _portRange = new PortRange();
        }
        _connManager = connManager;
        loadConfig1(cfg);
    }

    protected void loadConfig1(Properties cfg) throws UnknownHostException {
        _slaveStatusUpdateTime = Long.parseLong(cfg.getProperty(
                    "slaveStatusUpdateTime", "3000"));

        /*        _serverName = cfg.getProperty("master.bindname", "slavemaster");
                _socketPort = Integer.parseInt(cfg.getProperty("master.socketport",
                            "1100"));*/
        _hideIps = cfg.getProperty("hideips", "").equalsIgnoreCase("true");

        StringTokenizer st = new StringTokenizer(cfg.getProperty("bouncer_ip",
                    ""), " ");

        ArrayList<InetAddress> bouncerIps = new ArrayList<InetAddress>();

        while (st.hasMoreTokens()) {
            bouncerIps.add(InetAddress.getByName(st.nextToken()));
            // throws UnknownHostException
        }

        _bouncerIps = bouncerIps;
    }

    protected void loadConfig2(Reader in2) throws IOException {
        Hashtable<String,ArrayList> patternPathPermissions = new Hashtable<String,ArrayList>();
        Hashtable<String,Permission> permissions = new Hashtable<String,Permission>();
        ArrayList<RatioPathPermission> creditcheck = new ArrayList<RatioPathPermission>();
        Permission shutdown = null;
        ArrayList<RatioPathPermission> creditloss = new ArrayList<RatioPathPermission>();
        ArrayList<MessagePathPermission> msgpath = new ArrayList<MessagePathPermission>();
        _useFileNames = false;
        _replaceFile = null;
        _useDirNames = false;
        _replaceDir = null;

        LineNumberReader in = new LineNumberReader(in2);

        try {
            String line;

            while ((line = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);

                if (!st.hasMoreTokens()) {
                    continue;
                }

                String cmd = st.nextToken();

                try {
                    // login_prompt <string>
                    if (cmd.equals("login_prompt")) {
                        _loginPrompt = line.substring(13);
                    }
                    //max_users <maxUsersTotal> <maxUsersExempt>
                    else if (cmd.equals("max_users")) {
                        _maxUsersTotal = Integer.parseInt(st.nextToken());
                        _maxUsersExempt = Integer.parseInt(st.nextToken());
                    } else if (cmd.equals("pasv_ports")) {
                        String[] temp = st.nextToken().split("-");
                        _portRange = new PortRange(Integer.parseInt(temp[0]),Integer.parseInt(temp[1]));
                    } else if (cmd.equals("dir_names")) {
                        _useDirNames = true;
                        _capFirstDir = st.nextToken().equals("true");
                        _isLowerDir = st.nextToken().equals("lower");
                        _replaceDir = st;
                    } else if (cmd.equals("file_names")) {
                        _useFileNames = true;
                        _capFirstFile = st.nextToken().equals("true");
                        _isLowerFile = st.nextToken().equals("lower");
                        _replaceFile = st;
                    }
                    //msgpath <path> <filename> <flag/=group/-user>
                    else if (cmd.equals("msgpath")) {
                        String path = st.nextToken();
                        String messageFile = st.nextToken();
                        msgpath.add(new MessagePathPermission(path,
                                messageFile, makeUsers(st)));
                    }
                    //creditloss <path> <multiplier> [<-user|=group|flag> ...]
                    else if (cmd.equals("creditloss")) {
                        makeRatioPermission(creditloss, st);
                    }
                    //creditcheck <path> <ratio> [<-user|=group|flag> ...]
                    else if (cmd.equals("creditcheck")) {
                        makeRatioPermission(creditcheck, st);
                    } else if (cmd.equals("pathperm")) {
                        makePatternPathPermission(patternPathPermissions,
                            st.nextToken(), st);

                        //						patternPathPermission.put(
                        //							st.nextToken(),
                        //							makePatternPathPermission(st));
                    } else if (cmd.equals("privpath") || cmd.equals("dirlog") ||
                            cmd.equals("hideinwho") || cmd.equals("makedir") ||
                            cmd.equals("pre") || cmd.equals("upload") ||
                            cmd.equals("download") || cmd.equals("delete") ||
                            cmd.equals("deleteown") || cmd.equals("rename") ||
                            cmd.equals("renameown") || cmd.equals("request")) {
                        makePatternPathPermission(patternPathPermissions, cmd,
                            st);

                        //						patternPathPermission.put(
                        //							cmd,
                        //							makePatternPathPermission(st));
                    } else if ("userrejectsecure".equals(cmd) ||
                            "userrejectinsecure".equals(cmd) ||
                            "denydiruncrypted".equals(cmd) ||
                            "denydatauncrypted".equals(cmd) ||
                            "give".equals(cmd) || "take".equals(cmd)) {
                        if (permissions.containsKey(cmd)) {
                            throw new RuntimeException(
                                "Duplicate key in perms.conf: " + cmd +
                                " line: " + in.getLineNumber());
                        }

                        permissions.put(cmd, new Permission(makeUsers(st)));
                    } else if("shutdown".equals(cmd)) {
                    	shutdown = new Permission(makeUsers(st));
                    } else {
                        if (!cmd.startsWith("#")) {
                            makePatternPathPermission(patternPathPermissions,
                                cmd, st);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Exception when reading " + newConf + " line " +
                        in.getLineNumber(), e);
                }
            }

            creditcheck.trimToSize();
            _creditcheck = creditcheck;

            creditloss.trimToSize();
            _creditloss = creditloss;

            msgpath.trimToSize();
            _msgpath = msgpath;

            _patternPaths = patternPathPermissions;
            _permissions = permissions;
            _shutdown = shutdown;
        } finally {
            in.close();
        }
    }

    private void makePatternPathPermission(Hashtable<String,ArrayList> patternPathPermission,
        String key, StringTokenizer st) throws MalformedPatternException {
        ArrayList perms = patternPathPermission.get(key);

        if (perms == null) {
            perms = new ArrayList();
            patternPathPermission.put(key, perms);
        }

        perms.add(new PatternPathPermission(new GlobCompiler().compile(
                    st.nextToken()), makeUsers(st)));
    }

    /**
     *
     * @param cfg
     * @throws NumberFormatException
     */
    public void reloadConfig() throws FileNotFoundException, IOException {
        Properties cfg = new Properties();
        cfg.load(new FileInputStream(_cfgFileName));
        loadConfig(cfg, _connManager);
    }

    private void replaceChars(StringBuffer source, Character oldChar,
        Character newChar) {
        if (newChar == null) {
            int x = 0;

            while (x < source.length()) {
                if (source.charAt(x) == oldChar.charValue()) {
                    source.deleteCharAt(x);
                } else {
                    x++;
                }
            }
        } else {
            int x = 0;

            while (x < source.length()) {
                if (source.charAt(x) == oldChar.charValue()) {
                    source.setCharAt(x, newChar.charValue());
                }

                x++;
            }
        }
    }

    private String replaceName(String source, StringTokenizer st) {
        StringBuffer sb = new StringBuffer(source);
        Character oldChar = null;
        Character newChar = null;

        while (true) {
            if (!st.hasMoreTokens()) {
                return sb.toString();
            }

            String nextToken = st.nextToken();

            if (nextToken.length() == 1) {
                oldChar = new Character(nextToken.charAt(0));
                newChar = null;
            } else {
                oldChar = new Character(nextToken.charAt(0));
                newChar = new Character(nextToken.charAt(1));
            }

            replaceChars(sb, oldChar, newChar);
        }
    }

    /**
     * Returns true if user is allowed into a shutdown server.
     */
    public boolean isLoginAllowed(User user) {
        return (_shutdown == null) ? true : _shutdown.check(user);
    }

    public PortRange getPortRange() {
        return _portRange;
    }
}
