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
package org.drftpd.plugins;

import f00f.net.irc.martyr.Debug;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.commands.InviteCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.NickCommand;
import f00f.net.irc.martyr.services.AutoJoin;
import f00f.net.irc.martyr.services.AutoReconnect;
import f00f.net.irc.martyr.services.AutoRegister;
import f00f.net.irc.martyr.services.AutoResponder;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.Time;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.SFVFile;
import org.drftpd.SFVFile.SFVStatus;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.commands.UserManagment;
import org.drftpd.id3.ID3Tag;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.SlaveManager;

import org.drftpd.remotefile.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.slave.SlaveStatus;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.ResourceBundle;


/**
 * @author mog
 * @version $Id$
 */
public class SiteBot implements FtpListener, Observer {
    public static final ReplacerEnvironment GLOBAL_ENV = new ReplacerEnvironment();

    static {
        GLOBAL_ENV.add("bold", "\u0002");
        GLOBAL_ENV.add("coloroff", "\u000f");
        GLOBAL_ENV.add("color", "\u0003");
    }

    private static final Logger logger = Logger.getLogger(SiteBot.class);

    //private AutoJoin _autoJoin;
    private AutoReconnect _autoReconnect;
    private AutoRegister _autoRegister;
    private String _channelName;
    private ConnectionManager _cm;
    private String _commands;
    protected IRCConnection _conn;
    private boolean _enableAnnounce;

    //private String _key;
    protected int _port;
    private Hashtable<String,SectionSettings> _sections;
    protected String _server;

    public SiteBot() throws IOException {
        new File("logs").mkdirs();
        Debug.setOutputStream(new PrintStream(
                new FileOutputStream("logs/sitebot.log", true)));

        Debug.setDebugLevel(Debug.VERBOSE);
    }

    public static ArrayList<Nukee> map2nukees(Map nukees) {
        ArrayList<Nukee> ret = new ArrayList<Nukee>();

        for (Iterator iter = nukees.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();
            ret.add(new Nukee((String) element.getKey(),
                    ((Long) element.getValue()).longValue()));
        }

        Collections.sort(ret);

        return ret;
    }

    public static Collection<GroupPosition> topFileGroup(Collection files) {
        ArrayList<GroupPosition> ret = new ArrayList<GroupPosition>();

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
            String groupname = file.getGroupname();

            GroupPosition stat = null;

            for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
                GroupPosition stat2 = (GroupPosition) iter2.next();

                if (stat2.getGroupname().equals(groupname)) {
                    stat = stat2;

                    break;
                }
            }

            if (stat == null) {
                stat = new GroupPosition(groupname, file.length(), 1,
                        file.getXfertime());
                ret.add(stat);
            } else {
                stat.updateBytes(file.length());
                stat.updateFiles(1);
                stat.updateXfertime(file.getXfertime());
            }
        }

        Collections.sort(ret);

        return ret;
    }

    public static Collection userSort(Collection files, String type, String sort) {
        ArrayList<UploaderPosition> ret = new ArrayList<UploaderPosition>();

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            UploaderPosition stat = null;

            for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
                UploaderPosition stat2 = (UploaderPosition) iter2.next();

                if (stat2.getUsername().equals(file.getUsername())) {
                    stat = stat2;

                    break;
                }
            }

            if (stat == null) {
                stat = new UploaderPosition(file.getUsername(), file.length(),
                        1, file.getXfertime());
                ret.add(stat);
            } else {
                stat.updateBytes(file.length());
                stat.updateFiles(1);
                stat.updateXfertime(file.getXfertime());
            }
        }

        Collections.sort(ret, new UserComparator(type, sort));

        return ret;
    }

    public void actionPerformed(Event event) {
        if (_enableAnnounce) {
            try {
                if (event instanceof DirectoryFtpEvent) {
                    actionPerformedDirectory((DirectoryFtpEvent) event);
                } else if (event instanceof NukeEvent) {
                    actionPerformedNuke((NukeEvent) event);
                } else if (event instanceof SlaveEvent) {
                    actionPerformedSlave((SlaveEvent) event);
                } else if (event instanceof InviteEvent) {
                    actionPerformedInvite((InviteEvent) event);
                } else if (event.getCommand().equals("RELOAD")) {
                    try {
                        reload();
                    } catch (IOException e) {
                        logger.log(Level.WARN, "", e);
                    }
                } else if (event.getCommand().equals("SHUTDOWN")) {
                    MessageEvent mevent = (MessageEvent) event;
                    ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
                    env.add("message", mevent.getMessage());

                    sayGlobal(ReplacerUtils.jprintf("shutdown", env,
                            SiteBot.class));
                }
            } catch (FormatterException ex) {
                say(null,
                    event.getCommand() + " FormatterException: " +
                    ex.getMessage());
                logger.warn("", ex);
            }
        }
    }

    private void actionPerformedDirectory(DirectoryFtpEvent direvent)
        throws FormatterException {
        if (!getConfig().checkDirLog(direvent.getUser(), direvent.getDirectory())) {
            return;
        }

        if ("MKD".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "mkdir");
        } else if ("REQUEST".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "request");
        } else if ("REQFILLED".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "reqfilled");
        } else if ("RMD".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "rmdir");
        } else if ("WIPE".equals(direvent.getCommand())) {
            if (direvent.getDirectory().isDirectory()) {
                sayDirectorySection(direvent, "wipe");
            }
        } else if ("PRE".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "pre");
        } else if ("STOR".equals(direvent.getCommand())) {
            actionPerformedDirectorySTOR((TransferEvent) direvent);
        }
    }

    private void actionPerformedDirectoryID3(TransferEvent direvent)
        throws FormatterException {
        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        LinkedRemoteFile dir;

        try {
            dir = direvent.getDirectory().getParentFile();
        } catch (FileNotFoundException e) {
            throw new FatalException(e);
        }

        ID3Tag id3tag;

        try {
            id3tag = dir.lookupFile(dir.lookupMP3File()).getID3v1Tag();
        } catch (FileNotFoundException ex) {
            logger.info("No id3tag info for " +
                direvent.getDirectory().getPath() +
                ", can't publish id3tag info");

            return;
        } catch (NoAvailableSlaveException e) {
            logger.info("No available slave with id3 info");

            return;
        } catch (IOException e) {
            logger.warn("IO error reading id3 info", e);

            return;
        }

        env.add("path", dir.getName());
        env.add("genre", id3tag.getGenre().trim());
        env.add("year", id3tag.getYear().trim());
        env.add("album", id3tag.getAlbum().trim());
        env.add("artist", id3tag.getArtist().trim());
        env.add("title", id3tag.getTitle().trim());

        Ret ret = getPropertyFileSuffix("id3tag", dir);
        say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
    }

    private void actionPerformedDirectorySTOR(TransferEvent direvent)
        throws FormatterException {
        if (!direvent.isComplete()) {
            return;
        }

        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        LinkedRemoteFile dir;

        try {
            dir = direvent.getDirectory().getParentFile();
        } catch (FileNotFoundException e) {
            throw new FatalException(e);
        }

        SFVFile sfvfile;

        try {
            sfvfile = dir.lookupSFVFile();

            // throws IOException, ObjectNotFoundException, NoAvailableSlaveException
        } catch (FileNotFoundException ex) {
            logger.info("No sfv file in " + direvent.getDirectory().getPath() +
                ", can't publish race info");

            return;
        } catch (NoAvailableSlaveException e) {
            logger.info("No available slave with .sfv");

            return;
        } catch (IOException e) {
            logger.warn("IO error reading .sfv", e);

            return;
        }

        if (!sfvfile.hasFile(direvent.getDirectory().getName())) {
            return;
        }

        int halfway = (int) Math.floor((double) sfvfile.size() / 2);

        if (sfvfile.getStatus().getPresent() == 1) {
            actionPerformedDirectoryID3(direvent);
        }

        ///// start ///// start ////
        //check if new racer
        String username = direvent.getUser().getName();
        SFVStatus sfvstatus = sfvfile.getStatus();

        if ((sfvfile.size() - sfvstatus.getMissing()) != 1) {
            for (Iterator iter = sfvfile.getFiles().iterator(); iter.hasNext();) {
                LinkedRemoteFile sfvFileEntry = (LinkedRemoteFile) iter.next();

                if (sfvFileEntry == direvent.getDirectory()) {
                    continue;
                }

                if (sfvFileEntry.getUsername().equals(username)) {
                    break;
                }

                if (!iter.hasNext()) {
                    Ret ret = getPropertyFileSuffix("store.embraces",
                            direvent.getDirectory());
                    String format = ret.getFormat();
                    fillEnvSection(env, direvent, ret.getSection(),
                        direvent.getDirectory());
                    env.add("filesleft",
                        Integer.toString(sfvstatus.getMissing()));

                    say(ret.getSection(), SimplePrintf.jprintf(format, env));
                }
            }
        }

        //		env.add(
        //			"averagespeed",
        //			Bytes.formatBytes(
        //				direvent.getDirectory().length() / (racedtimeMillis / 1000)));
        //COMPLETE
        if (sfvstatus.isFinished()) {
            Collection racers = userSort(sfvfile.getFiles(), "bytes", "high");
            Collection groups = topFileGroup(sfvfile.getFiles());

            //Collection fast = userSort(sfvfile.getFiles(), "xferspeed", "high");
            //Collection slow = userSort(sfvfile.getFiles(), "xferspeed", "low");
            //// store.complete ////
            Ret ret = getPropertyFileSuffix("store.complete", dir);

            try {
                fillEnvSection(env, direvent, ret.getSection(),
                    direvent.getDirectory().getParentFile());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            env.add("racers", Integer.toString(racers.size()));
            env.add("groups", Integer.toString(groups.size()));
            env.add("files", Integer.toString(sfvfile.size()));
            env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()));
            env.add("speed", Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
            say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));

            //// store.complete.racer ////
            ret = getPropertyFileSuffix("store.complete.racer", dir);

            ReplacerFormat raceformat;

            // already have section from ret.section
            raceformat = ReplacerFormat.createFormat(ret.getFormat());

            try {
                fillEnvSection(env, direvent, ret.getSection(),
                    direvent.getDirectory().getParentFile());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            int position = 1;

            for (Iterator iter = racers.iterator(); iter.hasNext();) {
                UploaderPosition stat = (UploaderPosition) iter.next();

                User raceuser;

                try {
                    raceuser = _cm.getGlobalContext().getUserManager()
                                  .getUserByName(stat.getUsername());
                } catch (NoSuchUserException e2) {
                    continue;
                } catch (UserFileException e2) {
                    logger.log(Level.FATAL, "Error reading userfile", e2);

                    continue;
                }

                ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);

                raceenv.add("user", raceuser.getName());
                raceenv.add("group", raceuser.getGroup());

                raceenv.add("position", new Integer(position++));
                raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
                raceenv.add("files", Integer.toString(stat.getFiles()));
                raceenv.add("percent",
                    Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                    "%");
                raceenv.add("speed",
                    Bytes.formatBytes(stat.getXferspeed()) + "/s");
                raceenv.add("alup",
                    new Integer(TransferStatistics.getStatsPlace("ALUP",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("monthup",
                    new Integer(TransferStatistics.getStatsPlace("MONTHUP",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("wkup",
                    new Integer(TransferStatistics.getStatsPlace("WKUP",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("dayup",
                    new Integer(TransferStatistics.getStatsPlace("DAYUP",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("aldn",
                    new Integer(TransferStatistics.getStatsPlace("ALDN",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("monthdn",
                    new Integer(TransferStatistics.getStatsPlace("MONTHDN",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("wkdn",
                    new Integer(TransferStatistics.getStatsPlace("WKDN",
                            raceuser, _cm.getGlobalContext().getUserManager())));
                raceenv.add("daydn",
                    new Integer(TransferStatistics.getStatsPlace("DAYDN",
                            raceuser, _cm.getGlobalContext().getUserManager())));

                say(ret.getSection(), SimplePrintf.jprintf(raceformat, raceenv));
            }

            Ret ret3 = getPropertyFileSuffix("store.complete.group", dir);

            // already have section from ret.section
            raceformat = ReplacerFormat.createFormat(ret3.getFormat());
            say(ret.getSection(),
                SimplePrintf.jprintf(getPropertyFileSuffix(
                        "store.complete.group.header", dir).getFormat(), env));

            position = 1;

            for (Iterator iter = groups.iterator(); iter.hasNext();) {
                GroupPosition stat = (GroupPosition) iter.next();

                ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);

                raceenv.add("group", stat.getGroupname());

                raceenv.add("position", new Integer(position++));
                raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
                raceenv.add("files", Integer.toString(stat.getFiles()));
                raceenv.add("percent",
                    Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                    "%");
                raceenv.add("speed",
                    Bytes.formatBytes(stat.getXferspeed()) + "/s");

                say(ret.getSection(), SimplePrintf.jprintf(raceformat, raceenv));
            }

            //HALFWAY
        } else if ((sfvfile.size() >= 4) &&
                (sfvstatus.getMissing() == halfway)) {
            Collection uploaders = userSort(sfvfile.getFiles(), "bytes", "high");

            //			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
            UploaderPosition stat = (UploaderPosition) uploaders.iterator()
                                                                .next();

            env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
            env.add("leadfiles", Integer.toString(stat.getFiles()));
            env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
            env.add("leadpercent",
                Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                "%");
            env.add("filesleft", Integer.toString(sfvstatus.getMissing()));

            User leaduser;

            try {
                leaduser = _cm.getGlobalContext().getUserManager()
                              .getUserByName(stat.getUsername());
                env.add("leaduser", leaduser.getName());
                env.add("leadgroup", leaduser.getGroup());
            } catch (NoSuchUserException e3) {
                logger.log(Level.WARN, "", e3);
            } catch (UserFileException e3) {
                logger.log(Level.WARN, "", e3);
            }

            Ret ret = getPropertyFileSuffix("store.halfway", dir);
            fillEnvSection(env, direvent, ret.getSection());

            say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));

            //					for (Iterator iter =
            //						topFileUploaders2(sfvfile.getFiles()).iterator();
            //						iter.hasNext();
            //						) {
            //						UploaderPosition stat = (UploaderPosition) iter.next();
            //						String str1;
            //						try {
            //							str1 =
            //								formatUser(
            //									_cm.getUsermanager().getUserByName(
            //										stat.getUsername()));
            //						} catch (NoSuchUserException e2) {
            //							continue;
            //						} catch (IOException e2) {
            //							logger.log(
            //								Level.FATAL,
            //								"Error reading userfile",
            //								e2);
            //							continue;
            //						}
            //						say(
            //							str1
            //								+ " ["
            //								+ stat.getFiles()
            //								+ "f/"
            //								+ Bytes.formatBytes(stat.getBytes())
            //								+ "]");
            //					}
        }
    }

    private void actionPerformedInvite(InviteEvent event) {
        String user = event.getIrcNick();
        logger.info("Invited " + user + " through SITE INVITE");

        for (Enumeration e = getIRCConnection().getClientState().getChannels();
                e.hasMoreElements();) {
            Channel chan = (Channel) e.nextElement();

            if (chan.findMember(getIRCConnection().getClientState().getNick()
                                        .getNick()).hasOps()) {
                _conn.sendCommand(new InviteCommand(user, chan.getName()));
            }
        }
    }

    private void actionPerformedNuke(NukeEvent event) throws FormatterException {
        String cmd = event.getCommand();
        SectionInterface section = _cm.getGlobalContext().getSectionManager()
                                      .lookup(event.getPath());
        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        env.add("size", Bytes.formatBytes(event.getSize()));
        env.add("section", section.getName());

        //Ret ret = getPropertyFileSuffix("nuke", event.getPath());
        env.add("path", event.getPath());
        env.add("reason", event.getReason());
        env.add("multiplier", String.valueOf(event.getMultiplier()));

        env.add("user", event.getUser().getName());
        env.add("group", event.getUser().getGroup());

        //env.add("nukees", event.getNukees().keySet());
        if (cmd.equals("NUKE")) {
            say(section, ReplacerUtils.jprintf("nuke", env, SiteBot.class));

            ReplacerFormat raceformat = ReplacerUtils.finalFormat(SiteBot.class,
                    "nuke.nukees");

            int position = 1;
            long nobodyAmount = 0;

            for (Iterator iter = event.getNukees2().iterator(); iter.hasNext();) {
                Nukee stat = (Nukee) iter.next();

                User raceuser;

                try {
                    raceuser = _cm.getGlobalContext().getUserManager()
                                  .getUserByName(stat.getUsername());
                } catch (NoSuchUserException e2) {
                    nobodyAmount += stat.getAmount();

                    continue;
                } catch (UserFileException e2) {
                    logger.log(Level.FATAL, "Error reading userfile", e2);

                    continue;
                }

                ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);

                raceenv.add("user", raceuser.getName());
                raceenv.add("group", raceuser.getGroup());

                raceenv.add("position", "" + position++);
                raceenv.add("size", Bytes.formatBytes(stat.getAmount()));

                long nukedamount = Nuke.calculateNukedAmount(stat.getAmount(),
                        raceuser.getObjectFloat(UserManagment.RATIO),
                        event.getMultiplier());
                raceenv.add("nukedamount", Bytes.formatBytes(nukedamount));
                say(section, SimplePrintf.jprintf(raceformat, raceenv));
            }

            if (nobodyAmount != 0) {
                ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);

                raceenv.add("user", "nobody");
                raceenv.add("group", "nogroup");

                raceenv.add("position", "?");
                raceenv.add("size", Bytes.formatBytes(nobodyAmount));

                say(section, SimplePrintf.jprintf(raceformat, raceenv));
            }
        } else if (cmd.equals("UNNUKE")) {
            say(section, ReplacerUtils.jprintf("unnuke", env, SiteBot.class));
        }
    }

    private void actionPerformedSlave(SlaveEvent event)
        throws FormatterException {
        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        env.add("slave", event.getRSlave().getName());
        env.add("message", event.getMessage());

        if (event.getCommand().equals("ADDSLAVE")) {
            SlaveStatus status;

            try {
                status = event.getRSlave().getSlaveStatusAvailable();
            } catch (SlaveUnavailableException e) {
                logger.warn("in ADDSLAVE event handler", e);

                return;
            }

            fillEnvSlaveStatus(env, status, getSlaveManager());

            sayGlobal(ReplacerUtils.jprintf("addslave", env, SiteBot.class));
        } else if (event.getCommand().equals("DELSLAVE")) {
            sayGlobal(ReplacerUtils.jprintf("delslave", env, SiteBot.class));
        }
    }

    private AutoRegister addAutoRegister(Properties ircCfg) {
        return new AutoRegister(_conn,
            PropertyHelper.getProperty(ircCfg, "irc.nick"),
            PropertyHelper.getProperty(ircCfg, "irc.user"),
            PropertyHelper.getProperty(ircCfg, "irc.name"));
    }

    public void connect() throws UnknownHostException, IOException {
        logger.info("connecting to " + _server + ":" + _port);
        _conn.connect(_server, _port);
    }

    private void connect(Properties ircCfg)
        throws UnknownHostException, IOException {
        if (_conn != null) {
            _autoReconnect.disable();
            _conn.disconnect();
        }

        _conn = new IRCConnection();

        _autoReconnect = new AutoReconnect(_conn);
        _autoRegister = addAutoRegister(ircCfg);

        for (int i = 1;; i++) {
            String channelName = ircCfg.getProperty("irc.channel." + i);
            String key;

            if (channelName == null) {
                if (i == 1) {
                    channelName = PropertyHelper.getProperty(ircCfg, "irc.channel");
                    key = ircCfg.getProperty("irc.key");
                } else {
                    break;
                }
            } else {
                key = ircCfg.getProperty("irc.channel." + i + ".key");
            }

            if (i == 1) {
                _channelName = channelName;
            }

            new AutoJoin(_conn, channelName, key);
        }

        //_autoJoin = new AutoJoin(_conn, _channelName, _key);
        new AutoResponder(_conn);
        _conn.addCommandObserver(this);
        _commands = "";

        for (int i = 1;; i++) {
            String classname = ircCfg.getProperty("irc.plugins." + i);

            if (classname == null) {
                break;
            }

            Observer obs;

            try {
                logger.info("Loading " + Class.forName(classname));
                obs = (Observer) Class.forName(classname)
                                      .getConstructor(new Class[] { SiteBot.class })
                                      .newInstance(new Object[] { this });

                //_conn.addCommandObserver(obs);
                IRCPluginInterface plugin = (IRCPluginInterface) obs;

                if (plugin.getCommands() != null) {
                    _commands = _commands + plugin.getCommands() + " ";
                }
            } catch (Exception e) {
                logger.warn("", e);
                throw new RuntimeException("Error loading IRC plugin :" +
                    classname, e);
            }
        }

        connect();
    }

    public void disconnect() {
        _autoReconnect.disable();
        _conn.disconnect();
    }

    private void fillEnvSection(ReplacerEnvironment env,
        DirectoryFtpEvent direvent, SectionInterface section) {
        fillEnvSection(env, direvent, section, direvent.getDirectory());
    }

    private void fillEnvSection(ReplacerEnvironment env,
        DirectoryFtpEvent direvent, SectionInterface section,
        LinkedRemoteFileInterface file) {
        env.add("user", direvent.getUser().getName());
        env.add("group", direvent.getUser().getGroup());
        env.add("section", section.getName());

        LinkedRemoteFileInterface dir = file;

        if (dir.isFile()) {
            dir = dir.getParentFileNull();
        }

        long starttime;

        try {
            starttime = FileUtils.getOldestFile(dir).lastModified();
        } catch (ObjectNotFoundException e) {
            starttime = dir.lastModified();
        }

        //starttime = System.currentTimeMillis() - starttime;
        //env.add("elapsed", "" + starttime);
        env.add("size", Bytes.formatBytes(file.length()));
        env.add("path",
            strippath(dir.getPath().substring(section.getPath().length())));
        env.add("file", file.getName());

        if (file.isFile()) {
            env.add("speed",
                Bytes.formatBytes(file.getXferspeed() * 1000) + "/s");
            file = file.getParentFileNull(); // files always have parent dirs.
        }

        long elapsed = (direvent.getTime() - starttime);

        env.add("secondstocomplete",
            Time.formatTime(elapsed));

        long elapsedSeconds = elapsed / 1000;
        env.add("averagespeed",
            (elapsedSeconds == 0) ? "n/a"
                                  : (Bytes.formatBytes(
                dir.length() / elapsedSeconds) + "/s"));

        //			ArrayList dirs = new ArrayList();
        //			LinkedRemoteFileUtils.getAllDirectories(file, dirs);
        //			int files = 0;
        //
        //			for (Iterator iter = dirs.iterator(); iter.hasNext();) {
        //				LinkedRemoteFile subdir = (LinkedRemoteFile) iter.next();
        //				files += subdir.dirSize();
        //			}
        SFVFile sfvfile;

        try {
            sfvfile = file.lookupSFVFile();

            //env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()()));
            env.add("totalfiles", "" + sfvfile.size());
            env.add("totalspeed", Bytes.formatBytes(sfvfile.getXferspeed()));
        } catch (Exception ex) {
            //COULD BE multi-cd, PRE will have to get it owns fillEnvSection with sub-dir .sfv support!
            logger.warn("Couldn't get SFV file in announce", ex);

            //env.add("size", Bytes.formatBytes(file.length()));
            env.add("totalfiles", "" + file.getFiles().size());
        }
    }

    public static void fillEnvSlaveStatus(ReplacerEnvironment env,
        SlaveStatus status, SlaveManager slaveManager) {
        env.add("disktotal", Bytes.formatBytes(status.getDiskSpaceCapacity()));
        env.add("diskfree", Bytes.formatBytes(status.getDiskSpaceAvailable()));
        env.add("diskused", Bytes.formatBytes(status.getDiskSpaceUsed()));

        if (status.getDiskSpaceCapacity() == 0) {
            env.add("diskfreepercent", "n/a");
            env.add("diskusedpercent", "n/a");
        } else {
            env.add("diskfreepercent",
                ((status.getDiskSpaceAvailable() * 100) / status.getDiskSpaceCapacity()) +
                "%");
            env.add("diskusedpercent",
                ((status.getDiskSpaceUsed() * 100) / status.getDiskSpaceCapacity()) +
                "%");
        }

        env.add("xfers", "" + status.getTransfers());
        env.add("xfersdn", "" + status.getTransfersSending());
        env.add("xfersup", "" + status.getTransfersReceiving());
        env.add("xfersdown", "" + status.getTransfersSending());

        env.add("throughput", Bytes.formatBytes(status.getThroughput()) + "/s");

        env.add("throughputup",
            Bytes.formatBytes(status.getThroughputReceiving()) + "/s");

        env.add("throughputdown",
            Bytes.formatBytes(status.getThroughputSending()) + "/s");

        try {
            env.add("slaves", "" + slaveManager.getAvailableSlaves().size());
        } catch (NoAvailableSlaveException e2) {
            env.add("slaves", "0");
        }
    }

    public String getChannelName() {
        return _channelName;
    }

    public FtpConfig getConfig() {
        return _cm.getGlobalContext().getConfig();
    }

    public ConnectionManager getConnectionManager() {
        return _cm;
    }

    public IRCConnection getIRCConnection() {
        return _conn;
    }

    public Ret getPropertyFileSuffix(String prefix,
        LinkedRemoteFileInterface dir) {
        SectionInterface sectionObj = getConnectionManager().getGlobalContext()
                                          .getSectionManager().lookup(dir.getPath());

        //		LinkedRemoteFile section = null;
        //		LinkedRemoteFile tmp2 = dir, tmp1 = dir;
        //		try {
        //			while (true) {
        //				section = tmp2;
        //				tmp2 = tmp1;
        //				tmp1 = tmp1.getParentFile();
        //			}
        //		} catch (FileNotFoundException success) {
        //		}
        return new Ret(ResourceBundle.getBundle(SiteBot.class.getName())
                                     .getString(prefix), sectionObj);
    }

    public SlaveManager getSlaveManager() {
        return getConnectionManager().getGlobalContext().getSlaveManager();
    }

    public void init(ConnectionManager mgr) {
        _cm = mgr;

        try {
            reload();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void reconnect() {
        _conn.disconnect();
    }

    protected void reload() throws FileNotFoundException, IOException {
        Properties ircCfg = new Properties();
        ircCfg.load(new FileInputStream("conf/irc.conf"));
        reload(ircCfg);
    }

    protected void reload(Properties ircCfg) throws IOException {
        _server = PropertyHelper.getProperty(ircCfg, "irc.server");
        _port = Integer.parseInt(PropertyHelper.getProperty(ircCfg, "irc.port"));

        //String oldchannel = _channelName;
        //_channelName = FtpConfig.getProperty(ircCfg, "irc.channel");
        //_key = ircCfg.getProperty("irc.key");
        _enableAnnounce = ircCfg.getProperty("irc.enable.announce", "false")
                                .equals("true");

        //if (_key.equals(""))
        //	_key = null;
        Hashtable<String,SectionSettings> sections = new Hashtable<String,SectionSettings>();

        for (int i = 1;; i++) {
            String name = ircCfg.getProperty("irc.section." + i);

            if (name == null) {
                break;
            }

            String chan = ircCfg.getProperty("irc.section." + i + ".channel");

            if (chan == null) {
                chan = _channelName;
            }

            sections.put(name, new SectionSettings(ircCfg, i, chan));
        }

        _sections = sections;

        if ((_conn == null) ||
                !_conn.getClientState().getServer().equals(_server) ||
                (_conn.getClientState().getPort() != _port)) {
            logger.info("Reconnecting due to server change");
            connect(ircCfg);
        } else {
            if (!_conn.getClientState().getNick().getNick().equals(PropertyHelper.getProperty(
                            ircCfg, "irc.nick"))) {
                logger.info("Switching to new nick");
                _autoRegister.disable();
                _autoRegister = addAutoRegister(ircCfg);
                _conn.sendCommand(new NickCommand(ircCfg.getProperty("irc.nick")));
            }

            //if (!_conn.getClientState().isOnChannel(_channelName)) {
            //	_autoJoin.disable();
            //	_conn.removeCommandObserver(_autoJoin);
            //	_conn.sendCommand(new PartCommand(oldchannel));
            //	_autoJoin = new AutoJoin(_conn, _channelName, _key);
            //}
        }
    }

    public void say(SectionInterface section, String message) {
        SectionSettings sn = null;

        if (section != null) {
            sn = (SectionSettings) _sections.get(section.getName());
        }

        sayChannel((sn != null) ? sn.getChannel() : _channelName, message);
    }

    public void sayChannel(String chan, String message) {
        if (message.equals("")) {
            throw new IllegalArgumentException(
                "Cowardly refusing to send empty message");
        }

        String[] lines = message.split("\n");

        for (int i = 0; i < lines.length; i++) {
            _conn.sendCommand(new MessageCommand(chan, lines[i]));
        }
    }

    private void sayDirectorySection(DirectoryFtpEvent direvent, String string)
        throws FormatterException {
        Ret ret = getPropertyFileSuffix(string, direvent.getDirectory());
        String format = ret.getFormat();

        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        fillEnvSection(env, direvent, ret.getSection());

        say(ret.getSection(), SimplePrintf.jprintf(format, env));
    }

    public void sayGlobal(String string) {
        for (Enumeration e = getIRCConnection().getClientState()
                                 .getChannelNames(); e.hasMoreElements();) {
            sayChannel((String) e.nextElement(), string);
        }
    }

    public String strippath(String path) {
        if (!path.startsWith("/")) {
            return path;
        }
        return path.substring(1);
    }

    public void unload() {
        disconnect();
    }

    public void update(Observable observer, Object updated) {
        if (!(updated instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) updated;

        if (msgc.isPrivateToUs(getIRCConnection().getClientState())) {
            return;
        }

        if (!msgc.getDest().equalsIgnoreCase(_channelName)) {
            return;
        }

        String msg = msgc.getMessage();

        if (msg.equals("!help")) {
            sayChannel(msgc.getDest(), "Available commands: " + _commands);
        }
    }

    public static class Ret {
        private String _format;
        private SectionInterface _section;

        public Ret(String string, SectionInterface sectionObj) {
            _format = string;
            _section = sectionObj;
        }

        public String getFormat() {
            return _format;
        }

        public SectionInterface getSection() {
            return _section;
        }
    }

    public static class SectionSettings {
        private String _channel;
        private ReplacerEnvironment _env = new ReplacerEnvironment();

        public SectionSettings(Properties p, int i) {
        }

        public SectionSettings(Properties ircCfg, int i, String channel) {
            _channel = channel;
        }

        public String getChannel() {
            return _channel;
        }

        public ReplacerEnvironment getEnv() {
            return _env;
        }
    }
}


class UserComparator implements Comparator {
    private String _sort;
    private String _type;

    public UserComparator(String type, String sort) {
        _type = type;
        _sort = sort;
    }

    static long getType(String type, UploaderPosition user) {
        if (type.equals("bytes")) {
            return user.getBytes();
        } else if (type.equals("xferspeed")) {
            return user.getXferspeed();
        } else if (type.equals("xfertime")) {
            return user.getXfertime();
        }

        return 0;
    }

    public int compare(Object o1, Object o2) {
        UploaderPosition u1 = (UploaderPosition) o1;
        UploaderPosition u2 = (UploaderPosition) o2;

        long thisVal = getType(_type, u1);
        long anotherVal = getType(_type, u2);

        if (_sort.equals("low")) {
            return ((thisVal < anotherVal) ? (-1)
                                           : ((thisVal == anotherVal) ? 0 : 1));
        }

        return ((thisVal > anotherVal) ? (-1) : ((thisVal == anotherVal) ? 0 : 1));
    }
}
