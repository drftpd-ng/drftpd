/*
*
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
package net.drmods.plugins.ftp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author Teflon
 * @inspired by P
 * @version $Id$
 * 
 */
public class AutoFreeSpace extends FtpListener {
    private static Logger logger = Logger.getLogger(AutoFreeSpace.class);
    private static final String CONFPATH = "conf/autofreespace.conf";
    private ConnectionManager _cm;
    private long _minFreeSpace;
    private ArrayList<String> _excludeSections;
    private long _cycleTime;
    private long _wipeAfter;
    private Timer timer;
    private SiteBot _irc;

    public AutoFreeSpace() {
        _excludeSections = new ArrayList<String>();
    }

    public void actionPerformed(Event event) {
        if ("RELOAD".equals(event.getCommand())) {
            reload();
            return;
        }
    }

    public void unload() {
        timer.cancel();
    }

    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
        timer = new Timer();
        reload();
    }

    protected void say(String text) {
        if (_irc == null) {
            return;
        }
        _irc.say(_irc.getChannelName(), text);
    }

    private void reload() {
        Properties p = new Properties();
        try {
            p.load(new FileInputStream(CONFPATH));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            _irc = (SiteBot) _cm.getGlobalContext().getFtpListener(SiteBot.class);
        } catch (ObjectNotFoundException e) {
            _irc = null;
        }
        timer.cancel();
        timer = new Timer();
        _minFreeSpace = Bytes.parseBytes(p
                .getProperty("keepFree"));
        _cycleTime = Long.parseLong(p.getProperty("cycleTime")) * 60000;
        _wipeAfter = Long.parseLong(p.getProperty("wipeAfter")) * 60000;
        for (int i = 1;; i++) {
            String sec = p.getProperty("excluded.section." + i);
            if (sec == null)
                break;
            _excludeSections.add(sec);
        }
        _excludeSections.trimToSize();
        timer.schedule(new MrCleanit(_cm, _excludeSections, _wipeAfter, _minFreeSpace), _cycleTime, _cycleTime);
    }

    public class MrCleanit extends TimerTask {
        private ConnectionManager _cm;
        private ArrayList _excludeSections;
        private long _minFreeSpace;
        private long _archiveAfter;

        public MrCleanit(ConnectionManager cm, ArrayList excludeSecs, long archiveAfter, long minFreeSpace) {
            _cm = cm;
            _excludeSections = excludeSecs;
            _minFreeSpace = minFreeSpace;
            _archiveAfter = archiveAfter;
        }

        public boolean allSlavesOnline() {
            List<RemoteSlave> slaves = _cm.getGlobalContext().getSlaveManager().getSlaves();
            for ( RemoteSlave slave : slaves) {
                if (!slave.isOnline())
                    return false;
            }
            return true;
        }
        
        public LinkedRemoteFileInterface getOldestFile(LinkedRemoteFileInterface dir)
        	throws ObjectNotFoundException {
            Iterator iter = dir.getFiles().iterator();
            if (!iter.hasNext())
                throw new ObjectNotFoundException();
            LinkedRemoteFileInterface oldestFile = (LinkedRemoteFileInterface) iter.next();
            for (; iter.hasNext();) {
                LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
                if (oldestFile.lastModified() > file.lastModified()) {
                    oldestFile = file;
                }
            }
            return oldestFile;
        }

    	private LinkedRemoteFileInterface getOldestRelease(Collection sections)
                throws FileNotFoundException {
            Collection files = new ArrayList();
            LinkedRemoteFileInterface oldest = null;
            for (Iterator iter = sections.iterator(); iter.hasNext();) {
                SectionInterface si = (SectionInterface) iter.next();
                if (_excludeSections.contains(si.getName())) {
                    continue;
                }
                try {
                    LinkedRemoteFileInterface file = getOldestFile(si.getBaseFile());
                    if (file == null)
                        continue;
                    long age = System.currentTimeMillis() - file.lastModified();
                    if ((oldest == null || file.lastModified() < oldest.lastModified()) 
                            && age > _archiveAfter) {
                        oldest = file;
                    }
                } catch (Exception e) {
                    logger.warn("",e);
                }
            }
            if (oldest == null)
                throw new FileNotFoundException("Nothing to wipe");
            return oldest;
        }

        public void run() {
            SectionManagerInterface sm = _cm.getGlobalContext().getSectionManager();
            long freespace = _cm.getGlobalContext().getSlaveManager().getAllStatus().getDiskSpaceAvailable();
            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            
            while (freespace < _minFreeSpace && allSlavesOnline()) {
                LinkedRemoteFileInterface file = null;
                try {
                    file = getOldestRelease(sm.getSections());
                } catch (FileNotFoundException e) {
                    break;
                }
                logger.info("AUTODELETE: wiped " + file.getName());
                env.add("path",file.getPath());
                env.add("dir",file.getName());
                env.add("size",Bytes.formatBytes(file.length()));
                env.add("date",(new SimpleDateFormat("MM/dd/yy h:mma")).format(new Date(file.lastModified())));
                say(ReplacerUtils.jprintf("autodelete",env,AutoFreeSpace.class));
                freespace += file.length();
                file.delete();
            }
        }
    }

}