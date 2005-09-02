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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.RemoteSlave;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

import com.Ostermiller.util.StringTokenizer;


/**
 * @author zubov
 * @version $Id$
 */
public class Mirror extends FtpListener {
    private static final Logger logger = Logger.getLogger(Mirror.class);
    private ConnectionManager _cm;
    private ArrayList<GlobPathPermission> _perms;
    private boolean _mirrorAllSFV;
    private int _numberOfMirrors;

    public Mirror() {
        reload();
        logger.info("Mirror plugin loaded successfully");
    }

    public void actionPerformed(Event event) {
        if (event.getCommand().equals("RELOAD")) {
            reload();
            return;
        }

        if (event.getCommand().equals("STOR")) {
            actionPerformedSTOR((TransferEvent) event);
        }
    }

    public void actionPerformedSTOR(TransferEvent transevent) {

        LinkedRemoteFileInterface file = transevent.getDirectory();

        List<RemoteSlave> slaves = _cm.getGlobalContext().getSlaveManager().getSlaves();
        for(GlobPathPermission perm : _perms) {
        	if(perm.checkPath(file)) {
        		for(Iterator<RemoteSlave> iter = slaves.iterator();iter.hasNext();) {
        			if(!perm.check(iter.next())) iter.remove();
        		}
        		break;
        	}
        }

        int numToMirror = _numberOfMirrors;

        if (_mirrorAllSFV && file.getName().toLowerCase().endsWith(".sfv")) {
            numToMirror = _cm.getGlobalContext().getSlaveManager().getSlaves()
                             .size() / 2;
        }

        getGlobalContext().getJobManager().addJobToQueue(new Job(file,
                slaves, 0,
                numToMirror));
        logger.info("Done adding " + file.getPath() + " to the JobList");
    }

    public void init(GlobalContext gctx) {
        super.init(gctx);
        getGlobalContext().loadJobManager();
    }

    private void reload() {
        Properties props = new Properties();

        try {
            props.load(new FileInputStream("conf/mirror.conf"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        _numberOfMirrors = Integer.parseInt(PropertyHelper.getProperty(props,
                    "numberOfMirrors"));
        _mirrorAllSFV = PropertyHelper.getProperty(props, "mirrorAllSFV").equals("true");
        _perms = new ArrayList<GlobPathPermission>();

        for (int i = 1;; i++) {
        	StringTokenizer st;
        	try {
        		st = new com.Ostermiller.util.StringTokenizer(PropertyHelper.getProperty(props, "pathperm."+i));
        	} catch(NullPointerException e) {
        		break;
        	}
        	try {
				_perms.add(new GlobPathPermission(new GlobCompiler().compile(st.nextToken()), FtpConfig.makeUsers(st)));
			} catch (MalformedPatternException e1) {
				throw new RuntimeException(e1);
			}
        }
    }

    public void unload() {
    }
}
