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

import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;

import org.drftpd.GlobalContext;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UserManagment;

import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.permissions.MessagePathPermission;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.PatternPathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.permissions.RatioPathPermission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.Slave;

import org.drftpd.usermanager.User;

import com.Ostermiller.util.StringTokenizer;

import java.io.FileInputStream;
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
import java.util.Observable;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class FtpConfig extends Observable implements ConfigInterface {
    private static final Logger logger = Logger.getLogger(FtpConfig.class);
    private ArrayList<InetAddress> _bouncerIps;
    private boolean _capFirstDir;
    private boolean _capFirstFile;
    private String _cfgFileName;
    private ArrayList<RatioPathPermission> _creditcheck = new ArrayList<RatioPathPermission>();
    private ArrayList<RatioPathPermission> _creditloss = new ArrayList<RatioPathPermission>();
    private boolean _hideIps;
    private boolean _isLowerDir;
    private boolean _isLowerFile;
    private String _loginPrompt = Slave.VERSION + " http://drftpd.org";
    private int _maxUsersExempt;
    private int _maxUsersTotal = Integer.MAX_VALUE;
    private ArrayList<MessagePathPermission> _msgpath = new ArrayList<MessagePathPermission>();
    private Hashtable<String, ArrayList<PathPermission>> _pathsPerms = new Hashtable<String, ArrayList<PathPermission>>();
    private Hashtable<String, Permission> _permissions = new Hashtable<String,Permission>();
    private StringTokenizer _replaceDir = null;
    private StringTokenizer _replaceFile = null;
    private long _slaveStatusUpdateTime;
    private boolean _useDirNames = false;
    private boolean _useFileNames = false;
    private String newConf = "conf/perms.conf";
    protected PortRange _portRange;
	private Permission _shutdown;
	protected Properties _zsCfg;
	protected GlobalContext _gctx;

    protected FtpConfig() {
    }

    /**
     * Constructor that allows reusing of cfg object
     */
    public FtpConfig(Properties cfg, String cfgFileName,
        GlobalContext gctx) throws IOException {
        _cfgFileName = cfgFileName;
        loadConfig(cfg, gctx);
    }
    
    public FtpConfig(String cfgFileName, GlobalContext gctx) throws IOException {
        Properties cfg = new Properties();
        cfg.load(new FileInputStream(cfgFileName));
        loadConfig(cfg, gctx);
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

    public boolean checkPathPermission(String key, User fromUser,
        LinkedRemoteFileInterface path) {
        return checkPathPermission(key, fromUser, path, false);
    }

    public boolean checkPathPermission(String key, User fromUser,
        LinkedRemoteFileInterface path, boolean defaults) {
        Collection coll = ((Collection) _pathsPerms.get(key));

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

    public boolean checkPermission(String key, User user) {
        Permission perm = _permissions.get(key);

        return (perm == null) ? false : perm.check(user);
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

    public float getCreditCheckRatio(LinkedRemoteFileInterface path,
        User fromUser) {
        for (Iterator iter = _creditcheck.iterator(); iter.hasNext();) {
            RatioPathPermission perm = (RatioPathPermission) iter.next();

            if (perm.checkPath(path)) {
                if (perm.check(fromUser)) {
                    return perm.getRatio();
                }

                return fromUser.getKeyedMap().getObjectFloat(UserManagment.RATIO);
            }
        }

        return fromUser.getKeyedMap().getObjectFloat(UserManagment.RATIO);
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
        return (fromUser.getKeyedMap().getObjectFloat(UserManagment.RATIO) == 0) ? 0 : 1;
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

        String temp = name;

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
    	assert _gctx != null;
        return _gctx;
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

	public Properties getZsConfig() {
		assert _zsCfg != null;
		return _zsCfg;
	}

	public void loadConfig(Properties cfg, GlobalContext gctx)
        throws IOException {
        loadConfig2(new FileReader(newConf));
        if (_portRange == null) {
            //default portrange if none specified
            _portRange = new PortRange();
        }
        _gctx = gctx;
        loadConfig1(cfg);
        _zsCfg = new Properties();
        _zsCfg.load(new FileInputStream("conf/zipscript.conf"));
    }

    protected void loadConfig1(Properties cfg) throws UnknownHostException {
        _slaveStatusUpdateTime = Long.parseLong(cfg.getProperty(
                    "slaveStatusUpdateTime", "3000"));

        _hideIps = cfg.getProperty("hideips", "").equalsIgnoreCase("true");

        StringTokenizer st = new StringTokenizer(cfg.getProperty("bouncer_ip",
                    ""), " ");

        ArrayList<InetAddress> bouncerIps = new ArrayList<InetAddress>();

        while (st.hasMoreTokens()) {
            bouncerIps.add(InetAddress.getByName(st.nextToken()));
        }

        _bouncerIps = bouncerIps;
    }

    protected void loadConfig2(Reader in2) throws IOException {
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
                        _msgpath.add(new MessagePathPermission(path,
                                messageFile, makeUsers(st)));
                    }
                    //creditloss <path> <multiplier> [<-user|=group|flag> ...]
                    else if (cmd.equals("creditloss")) {
                        makeRatioPermission(_creditloss, st);
                    }
                    //creditcheck <path> <ratio> [<-user|=group|flag> ...]
                    else if (cmd.equals("creditcheck")) {
                        makeRatioPermission(_creditcheck, st);
                    } else if (cmd.equals("pathperm")) {
                        addGlobPathPermission(st.nextToken(), st);

                    } else if (cmd.equals("privpath") || cmd.equals("dirlog") ||
                            cmd.equals("hideinwho") || cmd.equals("makedir") ||
                            cmd.equals("pre") || cmd.equals("upload") ||
                            cmd.equals("download") || cmd.equals("delete") ||
                            cmd.equals("deleteown") || cmd.equals("rename") ||
                            cmd.equals("renameown") || cmd.equals("request")) {
                        addGlobPathPermission(cmd,
                            st);
                    } else if(cmd.equals("makedir2")) {
                    	addPatternPathPermission(cmd.substring(0,
								cmd.length() - 1), st);
                    } else if ("userrejectsecure".equals(cmd) ||
                            "userrejectinsecure".equals(cmd) ||
                            "denydiruncrypted".equals(cmd) ||
                            "denydatauncrypted".equals(cmd) ||
                            "give".equals(cmd) || "take".equals(cmd)) {
                        if (_permissions.containsKey(cmd)) {
                            throw new RuntimeException(
                                "Duplicate key in perms.conf: " + cmd +
                                " line: " + in.getLineNumber());
                        }

                        _permissions.put(cmd, new Permission(makeUsers(st)));
                    } else if("shutdown".equals(cmd)) {
                    	_shutdown = new Permission(makeUsers(st));
                    } else {
                        if (!cmd.startsWith("#")) {
                            addGlobPathPermission(cmd, st);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Exception when reading " + newConf + " line " +
                        in.getLineNumber(), e);
                }
            }

            //notify any observers so they can add their own permissions
            notifyObservers();
        } finally {
            in.close();
        }
    }

    private void addGlobPathPermission(String key, StringTokenizer st) throws MalformedPatternException {
        addPathPermission(key, new GlobPathPermission(new GlobCompiler().compile(
                    st.nextToken()), makeUsers(st)));
    }

    private void addPatternPathPermission(String key, StringTokenizer st) {
    	addPathPermission(key, new PatternPathPermission(st.nextToken(), makeUsers(st)));
    }

    public void addPathPermission(String key, PathPermission permission) {
        ArrayList<PathPermission> perms = _pathsPerms.get(key);

        if (perms == null) {
            perms = new ArrayList<PathPermission>();
            _pathsPerms.put(key, perms);
        }
        perms.add(permission);		
	}

    private static void replaceChars(StringBuffer source, Character oldChar,
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

    private static String replaceName(String source, StringTokenizer st) {
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
