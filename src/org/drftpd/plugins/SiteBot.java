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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

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
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.Blowfish;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.SFVFile;
import org.drftpd.Time;
import org.drftpd.SFVFile.SFVStatus;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.commands.UserManagement;
import org.drftpd.id3.ID3Tag;
import org.drftpd.master.SlaveManager;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.permissions.Permission;
import org.drftpd.remotefile.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.CommandRegister;
import f00f.net.irc.martyr.Debug;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.commands.InviteCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.NickCommand;
import f00f.net.irc.martyr.commands.NoticeCommand;
import f00f.net.irc.martyr.commands.PartCommand;
import f00f.net.irc.martyr.commands.RawCommand;
import f00f.net.irc.martyr.commands.WhoisCommand;
import f00f.net.irc.martyr.replies.WhoisUserReply;
import f00f.net.irc.martyr.services.AutoJoin;
import f00f.net.irc.martyr.services.AutoReconnect;
import f00f.net.irc.martyr.services.AutoRegister;
import f00f.net.irc.martyr.services.AutoResponder;
import f00f.net.irc.martyr.util.FullNick;


/**
 * @author mog
 * @version $Id$
 */
public class SiteBot extends FtpListener implements Observer {
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
    // Object<Method, IRCCommand, IRCPermission>[3]
	private HashMap<String,Object[]> _methodMap;
    protected IRCConnection _conn;
    private boolean _enableAnnounce;
    private int _maxUserAnnounce;
    private int _maxGroupAnnounce;
    private CaseInsensitiveHashMap<String,ChannelConfig> _channelMap;

    //private String _key;
    private Hashtable<String,SectionSettings> _sections;
    protected String _server;
    protected int _port;
    private Hashtable<String,User> _identWhoisQueue;

	private String _primaryChannelName;

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
    	try {
			if (event.getCommand().equals("RELOAD")) {
				try {
					reload();
				} catch (IOException e) {
					logger.log(Level.WARN, "", e);
				}
			} else if (event.getCommand().equals("SHUTDOWN")) {
				MessageEvent mevent = (MessageEvent) event;
				ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
				env.add("message", mevent.getMessage());

				sayGlobal(ReplacerUtils.jprintf("shutdown", env, SiteBot.class));
			} else if (event instanceof InviteEvent) {
				actionPerformedInvite((InviteEvent) event);
			} else if (_enableAnnounce) {
				if (event instanceof DirectoryFtpEvent) {
					actionPerformedDirectory((DirectoryFtpEvent) event);
				} else if (event instanceof NukeEvent) {
					actionPerformedNuke((NukeEvent) event);
				} else if (event instanceof SlaveEvent) {
					actionPerformedSlave((SlaveEvent) event);
				}
			}
		} catch (FormatterException ex) {
			logger.warn("", ex);
		}
    }

    private void actionPerformedDirectory(DirectoryFtpEvent direvent)
        throws FormatterException {
        if (!getGlobalContext().getConfig().checkPathPermission("dirlog", direvent.getUser(), direvent.getDirectory())) {
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

        // ANNOUNCE NFO FILE
        if (direvent.getDirectory().getName().toLowerCase().endsWith(".nfo")) {
            Ret ret = getPropertyFileSuffix("store.nfo", dir); 
            fillEnvSection(env, direvent, ret.getSection()); 
            say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
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
        String username = direvent.getUser().getName();
        SFVStatus sfvstatus = sfvfile.getStatus();
        // ANNOUNCE FIRST FILE RCVD 
        //   and EXPECTING xxxMB in xxx Files on same line.
        if( sfvstatus.getAvailable() == 1) {
            Ret ret = getPropertyFileSuffix("store.first", dir);
            fillEnvSection( env, direvent, ret.getSection(), direvent.getDirectory());
            env.add("files", Integer.toString(sfvfile.size()));
            env.add("expectedsize", (Bytes.formatBytes(sfvfile.getTotalBytes() * sfvfile.size())));
            say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
        }
        //check if new racer
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
                    raceuser = getGlobalContext().getUserManager()
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
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("monthup",
                    new Integer(TransferStatistics.getStatsPlace("MONTHUP",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("wkup",
                    new Integer(TransferStatistics.getStatsPlace("WKUP",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("dayup",
                    new Integer(TransferStatistics.getStatsPlace("DAYUP",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("aldn",
                    new Integer(TransferStatistics.getStatsPlace("ALDN",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("monthdn",
                    new Integer(TransferStatistics.getStatsPlace("MONTHDN",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("wkdn",
                    new Integer(TransferStatistics.getStatsPlace("WKDN",
                            raceuser, getGlobalContext().getUserManager())));
                raceenv.add("daydn",
                    new Integer(TransferStatistics.getStatsPlace("DAYDN",
                            raceuser, getGlobalContext().getUserManager())));

                say(ret.getSection(), SimplePrintf.jprintf(raceformat, raceenv));
                if (position >= _maxUserAnnounce)
                    break;
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
                if (position >= _maxGroupAnnounce)
                    break;
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

            User leaduser = null;
            try {
                leaduser = getGlobalContext().getUserManager()
                              .getUserByName(stat.getUsername());
            } catch (NoSuchUserException e3) {
                logger.log(Level.WARN, "", e3);
            } catch (UserFileException e3) {
                logger.log(Level.WARN, "", e3);
            }
            env.add("leaduser", leaduser != null ? leaduser.getName() : stat.getUsername());
            env.add("leadgroup", leaduser != null ? leaduser.getGroup() : "");

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

    private void actionPerformedInvite(InviteEvent event) throws FormatterException {
        String nick = event.getIrcNick();

        ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
        env.add("user",event.getUser());
        env.add("nick",event.getIrcNick());
        
        if ("INVITE".equals(event.getCommand()) ||
        		"SITE INVITE".equals(event.getCommand())) {

            ReplacerFormat format = ReplacerUtils.finalFormat(SiteBot.class,"invite.success");
            logger.info("Invited " + nick);
        	for (Enumeration e = getIRCConnection().getClientState().getChannels();
            	e.hasMoreElements();) {
            	Channel chan = (Channel) e.nextElement();

            	if (chan.findMember(getIRCConnection().getClientState().getNick()
                                    .getNick()).hasOps()) {
            		ChannelConfig cc = _channelMap.get(chan.getName());
            		if (cc != null) {
            			if (cc.checkPerms(event.getUser())) {
            				_conn.sendCommand(new InviteCommand(nick, chan.getName()));
            	    		say(chan.getName(),SimplePrintf.jprintf(format, env));
            	    		try {
            	    			notice(nick, "Channel key for " + chan.getName() + " is " + cc.getChannelKey(event.getUser()));
            	    		} catch (ObjectNotFoundException execption) {
            	    			// no key or not enough permissions
            	    		}
            			} else {
            				logger.warn("User does not have enough permissions to invite into " + chan.getName());
            			}
            		} else {
            			logger.error("Could not find ChannelConfig for " + chan.getName() + " this is a bug, please report it!", new Throwable());
            		}
            	}
            }
            _identWhoisQueue.put(nick,event.getUser());
            logger.info("Looking up "+ nick + " to set IRCIdent");
            _conn.sendCommand(new WhoisCommand(nick));        	
        } else if ("BINVITE".equals(event.getCommand())) {
            ReplacerFormat format = ReplacerUtils.finalFormat(SiteBot.class,"invite.failed");
    		sayGlobal(SimplePrintf.jprintf(format, env));       	
        }
        
    }

    private void actionPerformedNuke(NukeEvent event) throws FormatterException {
        String cmd = event.getCommand();
        SectionInterface section = getGlobalContext().getSectionManager()
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
                    raceuser = getGlobalContext().getUserManager()
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
                        raceuser.getKeyedMap().getObjectFloat(UserManagement.RATIO),
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
            
            ReplacerFormat raceformat = ReplacerUtils.finalFormat(SiteBot.class,
            "unnuke.nukees");

            int position = 1;
            long nobodyAmount = 0;

            for (Iterator iter = event.getNukees2().iterator(); iter.hasNext();) {
                Nukee stat = (Nukee) iter.next();

                User raceuser;

                try {
                    raceuser = getGlobalContext().getUserManager()
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
                        raceuser.getKeyedMap().getObjectFloat(UserManagement.RATIO),
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
    
    public class ChannelConfig {
    	private AutoJoin _autoJoin = null;
    	private String _blowKey = null;
    	private String _chanKey = null;
    	private WeakReference<Blowfish> _blowFish = null;
		private String _permissions = null;
    	private ChannelConfig(String blowKey, String chanKey, String permissions) {
    		_chanKey = chanKey;
    		_permissions = (permissions == null) ? "*" : permissions;
    		if (blowKey != null) {
        		_blowKey = blowKey;
    			_blowFish = new WeakReference<Blowfish>(new Blowfish(_blowKey));
    		}
    	}
    	public void setAutoJoin(AutoJoin aj) {
    		_autoJoin = aj;
    	}
    	
    	public AutoJoin getAutoJoin() {
    		return _autoJoin;
    	}
    	
    	public String getBlowfishKey(User user) throws ObjectNotFoundException {
    		if (checkPerms(user)) {
    			return _blowKey;
    		}
    		throw new ObjectNotFoundException("No Permissions");
    	}
    	
    	private Blowfish getBlowFish() {
    		if (_blowFish.get() == null) {
    			_blowFish = new WeakReference<Blowfish>(new Blowfish(_blowKey));
    		}
    		return _blowFish.get();
    	}
    	
    	public MessageCommand decrypt(MessageCommand cmd)
				throws UnsupportedEncodingException {
    		if (_blowKey == null) {
    			return cmd;
    		}
    		try {
    			return new MessageCommand(cmd.getSource(), cmd.getDest(), getBlowFish().Decrypt(cmd.getMessage()));
    		} catch (StringIndexOutOfBoundsException e) {
    			throw new UnsupportedEncodingException();
    		}
		}
    	
    	public String encrypt(String message) {
    		if (_blowKey == null) {
    			return message;
    		}
    		return getBlowFish().Encrypt(message);
    	}
    	
    	private boolean checkPerms(User user) {
    		Permission p = new Permission(FtpConfig
    				.makeUsers(new StringTokenizer(_permissions)));
    		return p.check(user);
    	}
    	
    	public String getChannelKey(User user) throws ObjectNotFoundException {
    		if (checkPerms(user) && _chanKey != null) {
    			return _chanKey;
    		}
    		throw new ObjectNotFoundException("No Permissions");
    	}
    }

    /**
     * Loads settings that require the IRCConnection _conn
     */
    private synchronized void connect(Properties ircCfg)
        throws UnknownHostException, IOException {
        disconnect();

        _conn = new IRCConnection();
        

         try {
            _conn.setSendDelay(Integer.parseInt(ircCfg.getProperty("irc.sendDelay")));
        } catch (NumberFormatException e1) {
            logger.warn("irc.sendDelay not set, defaulting to 300ms");
        }
        
        _autoReconnect = new AutoReconnect(_conn);
        _autoRegister = addAutoRegister(ircCfg);
        
        new AutoResponder(_conn);
        _conn.addCommandObserver(this);

        for (int i = 1;; i++) {
            String classname = ircCfg.getProperty("martyr.plugins." + i);

            if (classname == null) {
                break;
            }

            Observer obs;

            try {
                logger.info("Loading " + Class.forName(classname));
                obs = (Observer) Class.forName(classname)
                                      .getConstructor(new Class[] { SiteBot.class })
                                      .newInstance(new Object[] { this });
            } catch (Exception e) {
                logger.warn("", e);
                throw new RuntimeException("Error loading Martyr plugin :" +
                    classname, e);
            }
        }

        connect();
    }

    public void disconnect() {
    	if (_autoReconnect != null) {
    		_autoReconnect.disable();
    	}
    	if (_conn != null) {
    		_conn.disconnect();
    	}
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
            //env.add("size", Bytes.formatBytes(file.length()));
            env.add("totalfiles", "" + file.getFiles().size());
            //COULD BE multi-cd, PRE will have to get it owns fillEnvSection with sub-dir .sfv support!
        	if (ex instanceof FileNotFoundException) {
        		// no need to spam FileNotFound on SFVFile lookups
        		return;
        	}
            logger.warn("Couldn't get SFV file in announce");
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
    
    public String getPrimaryChannel() {
        return _primaryChannelName;
    }

    public IRCConnection getIRCConnection() {
        return _conn;
    }

    public Ret getPropertyFileSuffix(String prefix,
        LinkedRemoteFileInterface dir) {
        SectionInterface sectionObj = getGlobalContext()
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
        return getGlobalContext().getSlaveManager();
    }

    public void init(GlobalContext gctx) {
    	super.init(gctx);
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
    	synchronized(this) {
    		ircCfg.load(new FileInputStream("conf/irc.conf"));
    		reloadIRCCommands();
    	}
        reload(ircCfg);
    }

	private void reloadIRCCommands() throws IOException {
		_methodMap = new HashMap<String, Object[]>();
		HashMap<String, IRCCommand> ircCommands = new HashMap<String, IRCCommand>();
		LineNumberReader lineReader = new LineNumberReader(new FileReader(
				"conf/irccommands.conf"));
		String line = null;
		while ((line = lineReader.readLine()) != null) {
			if (line.startsWith("#") || line.trim().equals("")) {
				continue;
			}
			StringTokenizer st = new StringTokenizer(line);
			if (st.countTokens() < 4) {
				logger.error("Line is invalid -- not enough parameters \"" + line + "\"");
				continue;
			}
			String trigger = st.nextToken();
			String methodString = st.nextToken();
			String scopeList = st.nextToken();
			String permissions = st.nextToken("").trim();
			
			int index = methodString.lastIndexOf(".");
			String className = methodString.substring(0, index);
			methodString = methodString.substring(index + 1);
			Method m = null;
			try {
				IRCCommand ircCommand = ircCommands.get(className);
				if (ircCommand == null) {
					ircCommand = (IRCCommand) Class
							.forName(className)
							.getConstructor(new Class[] { GlobalContext.class })
							.newInstance(new Object[] { getGlobalContext() });
					ircCommands.put(className, ircCommand);
				}
				m = ircCommand.getClass().getMethod(methodString,
						new Class[] { String.class, MessageCommand.class});
				_methodMap.put(trigger, new Object[] { m, ircCommand,
						new IRCPermission(scopeList, permissions) });
			} catch (Exception e) {
				logger.error(
						"Invalid class/method listed in irccommands.conf - "
								+ line, e);
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Loads irc settings, then passes it off to connect(Properties)
	 */
	protected void reload(Properties ircCfg) throws IOException {
        _server = PropertyHelper.getProperty(ircCfg, "irc.server");
		_port = Integer
				.parseInt(PropertyHelper.getProperty(ircCfg, "irc.port"));

		_enableAnnounce = ircCfg.getProperty("irc.enable.announce", "false")
				.equals("true");
		CaseInsensitiveHashMap<String, ChannelConfig> oldChannelMap = new CaseInsensitiveHashMap<String, ChannelConfig>();
		if (_channelMap != null) { // reload config
			oldChannelMap.putAll(_channelMap);
		}
		synchronized (this) {
			_channelMap = new CaseInsensitiveHashMap<String, ChannelConfig>();

			for (int i = 1;; i++) {
				String channelName = ircCfg.getProperty("irc.channel." + i);
				String blowKey = ircCfg.getProperty("irc.channel." + i
						+ ".blowkey");
				String chanKey = ircCfg.getProperty("irc.channel." + i
						+ ".chankey");
				String permissions = ircCfg.getProperty("irc.channel." + i
						+ ".perms");
				if (channelName == null) {
					break;
				}
				if (i == 1) {
					_primaryChannelName = channelName.toUpperCase();
				}

				_channelMap.put(channelName, new ChannelConfig(blowKey,
						chanKey, permissions));
			}
			if (_channelMap.size() < 1) {
				throw new IllegalStateException(
						"SiteBot loaded with no channels, check your config");
			}

			_sections = new Hashtable<String, SectionSettings>();
			for (int i = 1;; i++) {
				String name = ircCfg.getProperty("irc.section." + i);

				if (name == null) {
					break;
				}

				String chan = ircCfg.getProperty("irc.section." + i
						+ ".channel");

				if (chan == null) {
					chan = _primaryChannelName;
				}

				_sections.put(name, new SectionSettings(ircCfg, i, chan));
			}

			if (_conn == null) {
				connect(ircCfg);
			}
			if ((!_conn.getClientState().getServer().equals(_server))
					|| (_conn.getClientState().getPort() != _port)) {
				logger.info("Reconnecting due to server change");
				connect(ircCfg);
			}

			if (_conn.getClientState().getNick() != null
					&& !_conn.getClientState().getNick().getNick().equals(
							PropertyHelper.getProperty(ircCfg, "irc.nick"))) {
				logger.info("Switching to new nick");
				_autoRegister.disable();
				_autoRegister = addAutoRegister(ircCfg);
				_conn.sendCommand(new NickCommand(ircCfg
						.getProperty("irc.nick")));
			}
			for (Iterator iter = new ArrayList(
					Collections.list(getIRCConnection().getClientState()
							.getChannelNames())).iterator(); iter.hasNext();) {
				String currentChannel = (String) iter.next();
				if (_channelMap.containsKey(currentChannel)) { // still in
					// channel
					ChannelConfig newCC = _channelMap.get(currentChannel);
					ChannelConfig oldCC = oldChannelMap.get(currentChannel);
					if (newCC == null || oldCC == null) {
						logger.debug("This is a bug! report me! -- channel="
								+ currentChannel + " newCC=" + newCC
								+ " oldCC=" + oldCC, new Throwable());
						continue;
					}
					newCC.setAutoJoin(oldCC.getAutoJoin());
				} else { // removed from channel
					ChannelConfig oldCC = oldChannelMap.get(currentChannel);
					if (oldCC == null) {
						logger.debug("This is a bug! report me! -- channel="
								+ currentChannel + " oldCC=" + oldCC,
								new Throwable());
						continue;
					}
					oldCC.getAutoJoin().disable();
					_conn.sendCommand(new PartCommand(currentChannel));
					_conn.getClientState().removeChannel(currentChannel);
					_conn.removeCommandObserver(oldCC.getAutoJoin());
				}
			}
		}
		for (String channelName : _channelMap.keySet()) {
        	ChannelConfig cc = _channelMap.get(channelName);
    		if (cc == null) {
    			logger.debug("This is a bug! report me! -- channel=" + channelName + " cc=" + cc, new Throwable());
    			continue;
    		}
        	if (cc.getAutoJoin() == null) { // new channel!
        		cc.setAutoJoin(new AutoJoin(_conn, channelName, cc._chanKey));
        	}
        }
        
        _identWhoisQueue = new Hashtable<String,User>();
		
        //maximum announcements for race results
		try {
            _maxUserAnnounce = Integer.parseInt(ircCfg.getProperty("irc.max.racers", "100"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid setting in irc.conf: irc.max.racers", e);
            _maxUserAnnounce = 100;
        }
		try {
            _maxGroupAnnounce = Integer.parseInt(ircCfg.getProperty("irc.max.groups", "100"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid setting in irc.conf: irc.max.groups", e);
            _maxGroupAnnounce = 100;
        }
   }

    public void say(SectionInterface section, String message) {
        SectionSettings sn = null;

        if (section != null) {
            sn = (SectionSettings) _sections.get(section.getName());
        }
        say((sn != null) ? sn.getChannel() : _primaryChannelName, message);
    }
    
    public void say(String message) {
    	say(_primaryChannelName,message);
    }

    public synchronized void say(String dest, String message) {
        if (message == null || message.equals("")) {
        	return;
        }
        boolean isChan = dest.startsWith("#");
        String[] lines = message.split("\n");
        for (String line : lines) {
			// don't encrypt private messages, at least not yet :)
			if (isChan) {
				ChannelConfig cc = _channelMap.get(dest);
				if (cc == null) {
	    			logger.debug("This is a bug! report me! -- channel=" + dest + " cc=" + cc, new Throwable());
	    			continue;
				}
				line = cc.encrypt(line);
			}
			_conn.sendCommand(new MessageCommand(dest, line));
		}
    }
    
    public synchronized void notice(String dest, String message) {
        if (message == null || message.equals("")) {
        	return;
        }
        boolean isChan = dest.startsWith("#");
        String[] lines = message.split("\n");
        for (String line : lines) {
			// don't encrypt private notices, at least not yet :)
			if (isChan) {
				ChannelConfig cc = _channelMap.get(dest);
				if (cc == null) {
	    			logger.debug("This is a bug! report me! -- channel=" + dest + " cc=" + cc, new Throwable());
	    			continue;
				}
				line = cc.encrypt(line);
			}
			_conn.sendCommand(new RawCommand("NOTICE " + dest + " :" + line));
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
		for (Iterator iter = new ArrayList(Collections.list(getIRCConnection()
				.getClientState().getChannelNames())).iterator(); iter
				.hasNext();) {
			say((String) iter.next(), string);
		}
	}

    // why the hell is this here? don't we already have 10 methods that do this?
    private String strippath(String path) {
        if (!path.startsWith("/")) {
            return path;
        }
        return path.substring(1);
    }

    public void unload() {
        disconnect();
    }

    public void update(Observable observer, Object updated) {
    	// add ident for those who use site invite
		if (updated instanceof WhoisUserReply) {
			WhoisUserReply whor = (WhoisUserReply) updated;
			String reply[] = whor.getParameter(whor.getSourceString(), 0)
					.split(" ");
			String nick = reply[3];
			String fullIdent = reply[3] + "!" + reply[4] + "@" + reply[5];

			for (Iterator i = _identWhoisQueue.keySet().iterator(); i.hasNext();) {
				String n = (String) i.next();
				if (n.toLowerCase().equals(nick.toLowerCase())) {
					User user = (User) _identWhoisQueue.get(n);
					user.getKeyedMap().setObject(UserManagement.IRCIDENT,
							fullIdent);
					try {
						user.commit();
						_identWhoisQueue.remove(nick);
						logger.info("Set IRCIdent to '" + fullIdent + "' for '"
								+ user.getName() + "'");
					} catch (UserFileException e) {
						logger.info("Could not set IRCIdent to '" + fullIdent
								+ "' for '" + user.getName() + "'", e);
					}
				}
			}
			// clear the queue to avoid excessive memory usage
			_identWhoisQueue.clear();
		} else if ((updated instanceof MessageCommand)) {
			synchronized (this) {
				MessageCommand msgc = (MessageCommand) updated;

				// recreate the MessageCommand with the decrypted text
				if (!msgc.isPrivateToUs(_conn.getClientState())) {
					try {
						ChannelConfig cc = _channelMap.get(msgc.getDest());
						if (cc == null) {
				    		logger.debug("This is a bug! report me! -- channel=" + msgc.getDest() + " cc=" + cc, new Throwable());
				    		return;
						}
						msgc = cc.decrypt(msgc);
					} catch (UnsupportedEncodingException e) {
						logger.warn("Unable to decrypt '"
								+ msgc.getSourceString() + "'");
						return; // should not accept plaintext messages
								// encrypted channels
					}
				}
				int index = msgc.getMessage().indexOf(" ");
				String args = "";
				String trigger = "";
				if (index == -1) {
					trigger = msgc.getMessage().toLowerCase();
				} else {
					trigger = msgc.getMessage().substring(0, index);
					args = msgc.getMessage().substring(index + 1).trim();
				}
				if (_methodMap.containsKey(trigger)) { // is a recognized
														// command
					Object[] objects = _methodMap.get(trigger);
					IRCPermission perm = (IRCPermission) objects[2];
					String scope = msgc.isPrivateToUs(_conn.getClientState()) ? "private"
							: msgc.getDest();
					if (!perm.checkScope(scope)) { // not a recognized command
													// on this channel or
													// through privmsg
						logger.warn(trigger + " is not in scope - " + scope);
						return;
					}
					if (!perm.checkPermission(msgc.getSource())) {
						ReplacerEnvironment env = new ReplacerEnvironment(
								SiteBot.GLOBAL_ENV);
						env.add("ircnick", msgc.getSource().getNick());
						say(msgc.getDest(), ReplacerUtils.jprintf(
								"ident.denymsg", env, SiteBot.class));
						logger
								.warn("Not enough permissions for user to execute "
										+ trigger + " to " + msgc.getDest());
						return;
					}
					try {
						ArrayList<String> list = (ArrayList) ((Method) objects[0])
								.invoke(objects[1], new Object[] { args, msgc });
						if (list == null || list.isEmpty()) {
							logger
									.debug("There is no direct output to return for command "
											+ trigger
											+ " by "
											+ msgc.getSource().getNick());
						} else {
							for (String output : list) {
								say(getSource(msgc), output);
							}
						}
					} catch (Exception e) {
						logger.error(
								"Error in method invocation on IRCCommand "
										+ trigger, e);
						say(getSource(msgc), e.getMessage());
					}
				}
			}
		}
    }
     
    /**
	 * Returns the source of the message, (channel or nickname)
	 */
	private String getSource(MessageCommand msgc) {
		return msgc.isPrivateToUs(_conn.getClientState()) ? msgc.getSource().getNick() : msgc.getDest();
	}
	
	public class IRCPermission {
    	
    	ArrayList<String> _scope = new ArrayList<String>();
    	String _permissions = null;
        public IRCPermission(String scope, String permissions) {
        	for (String s : scope.split(",")) {
        		_scope.add(s);
        	}
        	_permissions = permissions;
        }
        
        
        /**
         * Accepts channel names, "public", or "private"
         */
        public boolean checkScope(String scope) {
        	if (_scope.contains(scope)) { // matches private or channel names
        		return true;
        	}
        	return scope.startsWith("#") && _scope.contains("public");
        }
        
        public boolean checkPermission(FullNick fn) {
        	if (_permissions.equals("*")) {
        		return true;
        	}
        	try {
        	   	return new Permission(FtpConfig.makeUsers(new StringTokenizer(_permissions))).check(lookupUser(fn));
        	} catch (NoSuchUserException e) {
        	    logger.warn(e);
        		return false;
        	}
        }

        public User lookupUser(FullNick fn) throws NoSuchUserException {
         	String ident = fn.getNick() + "!" + fn.getUser() + "@" + fn.getHost();
         	try {
         	    return getGlobalContext().getUserManager().getUserByIdent(ident);
         	} catch (NoSuchUserException e3) {
         	    logger.warn("Could not identify " + ident);
         	} catch (UserFileException e3) {
         	    logger.warn("Could not identify " + ident);
         	}
    	    throw new NoSuchUserException("No user found");
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

	/**
	 * Returns the blowfish key for the channel specified
	 */
	public synchronized String getBlowfishKey(String channel, User user) throws ObjectNotFoundException {
		ChannelConfig cc = _channelMap.get(channel);
		if (cc != null) {
			return cc.getBlowfishKey(user);
		}
		throw new ObjectNotFoundException();
	}

	public String getBlowfishKey(User user) throws ObjectNotFoundException {
		return getBlowfishKey(_primaryChannelName, user);
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
