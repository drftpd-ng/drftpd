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
package org.drftpd.plugins.prebw;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.pre.Pre;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.master.BaseFtpConnection;
import org.drftpd.master.master.ConnectionManager;
import org.drftpd.master.master.TransferState;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;
import org.drftpd.plugins.prebw.event.PREBWEvent;
import org.drftpd.slave.slave.Transfer;
import org.drftpd.slave.slave.TransferStatus;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * @author lh
 */
public class PREBWPostHook {
	private static final Logger logger = LogManager.getLogger(PREBWPostHook.class);
    private ArrayList<TimeSetting> _timeSettings = new ArrayList<>();
    private String _exclude;
    private String[] _sections;
	private boolean _realSpeed;
	private int _leechtopCount;

    public PREBWPostHook() {
        logger.info("Starting PREBW plugin");
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

    private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("prebw.conf");
		if (cfg == null) {
			logger.fatal("config/prebw.conf not found");
            return;
		}
        PreInfos.getPreInfosSingleton().clearPreInfos();
        _timeSettings.clear();
        _sections = cfg.getProperty("sections", "").split(";");
		_exclude = cfg.getProperty("exclude", "");
		_realSpeed = cfg.getProperty("real.speed", "false").equalsIgnoreCase("true");
		_leechtopCount = Integer.parseInt(cfg.getProperty("prebw.leechtop.count", "3"));
		for (int i = 1;; i++) {
			String times = cfg.getProperty(i + ".times");
			if (times == null) break;
			String interval = cfg.getProperty(i + ".interval");
			String countAvg = cfg.getProperty(i + ".count.avg");
			_timeSettings.add(new TimeSetting(times, interval, countAvg));
		}
	}

    @CommandHook(commands = "doPRE", type = HookType.POST)
	public void doPREPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
			// PRE failed, abort
			return;
		}

        String[] args = request.getArgument().split(" ");

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(args[1]);

        boolean validSection = false;
		for (String sec : _sections) {
			if (section.getName().equals(sec) || sec.equals("*")) {
				validSection = true;
                break;
            }
		}
		if (!validSection)
			return;

        DirectoryHandle dir = response.getObject(Pre.PREDIR, null);

        if (dir.getName().matches(_exclude)) {
            return;
        }

        LinkedList<PreInfo> preInfos = PreInfos.getPreInfosSingleton().getPreInfos();

        for(TimeSetting timeSetting : _timeSettings) {
            String startInterval = timeSetting.getStartInterval();
            String endInterval = timeSetting.getEndInterval();
            long parsedStartInterval;
            long parsedEndInterval;
            long dirSize;
            try {
                dirSize = dir.getSize();
            } catch (FileNotFoundException e) {
                // No need to continue
                logger.error("",e);
                return;
            }

            if (startInterval.equals("*")) {
                preInfos.addLast(new PreInfo(dir, section, timeSetting));
                getInfoThread thread = new getInfoThread(preInfos.getLast());
                thread.start();
                logger.info("New PreInfo added: startinterval = * [{}]", dir.getName());
                continue;
            } else {
                try {
                    parsedStartInterval = Long.parseLong(startInterval)*1048576L;
                } catch (NumberFormatException e) {
                    logger.warn("start interval in wrong format: {}", startInterval, e);
                    continue;
                }
            }
            if (endInterval.equals("*")) {
                if (dirSize > parsedStartInterval) {
                    preInfos.addLast(new PreInfo(dir, section, timeSetting));
                    getInfoThread thread = new getInfoThread(preInfos.getLast());
                    thread.start();
                    logger.info("New PreInfo added: dirSize({}) > startinterval({}) && endinterval = * [{}]", dirSize, parsedStartInterval, dir.getName());
                }
            } else {
                try {
                    parsedEndInterval = Long.parseLong(endInterval)*1048576L;
                } catch (NumberFormatException e) {
                    logger.warn("end interval in wrong format: {}", endInterval, e);
                    continue;
                }
                if (dirSize > parsedStartInterval && dirSize <= parsedEndInterval) {
                    preInfos.addLast(new PreInfo(dir, section, timeSetting));
                    getInfoThread thread = new getInfoThread(preInfos.getLast());
                    thread.start();
                    logger.info("New PreInfo added: dirSize({}) > startinterval({}) && dirSize({}) > endinterval({}) [{}]", dirSize, parsedStartInterval, dirSize, parsedEndInterval, dir.getName());
                }
            }
        }
	}

    @CommandHook(commands = "doRETR", priority = 10, type = HookType.POST)
    public void doRETRPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// Transfer failed, abort
			return;
		}
        LinkedList<PreInfo> preInfos = PreInfos.getPreInfosSingleton().getPreInfos();
        DirectoryHandle dir = request.getCurrentDirectory();
        for (PreInfo preInfo : preInfos) {
            if (dir.getPath().equals(preInfo.getDir().getPath())) {
                try {
                    User u = request.getUserObject();
                    UserInfo actUser = preInfo.getUser(u.getName());
                    TransferStatus status = response.getObject(DataConnectionHandler.XFER_STATUS, null);
                    if(actUser != null) {
                        actUser.addFiles(1);
                        actUser.addBytes(status.getTransfered());
                        actUser.addSpeed(status.getXferSpeed());
                    } else {
                        preInfo.addUser(new UserInfo(u.getName(), u.getGroup().getName(), 1, status.getTransfered(), status.getXferSpeed()));
                    }
                    preInfo.addGroup(u.getGroup().getName());
                } catch (NoSuchUserException | UserFileException ignored) {
                    // Strange.. and ignore
                }
            }
        }
    }

    private class getInfoThread extends Thread {
		private PreInfo _preInfo;

		public getInfoThread(PreInfo preInfo) {
			_preInfo = preInfo;
		}

        @Override
		public void run() {
			try {
				int prebwTimeTotal = 0;
				for (String prebwTime : _preInfo.getTimeSetting().getTimes().split(",")) {
					Thread.sleep(Integer.parseInt(prebwTime)*1000);
					prebwTimeTotal += Integer.parseInt(prebwTime);
                    long speed;
					if (_realSpeed)
						speed = getPreSpeed();
					else
						speed = GlobalContext.getGlobalContext().getSlaveManager().getAllStatus().getThroughputSending();
					_preInfo.addBW(speed);
					_preInfo.setMessures(prebwTimeTotal+"s", Bytes.formatBytes(speed)+"/s");
				}
				_preInfo.setMtime(prebwTimeTotal);
				GlobalContext.getEventService().publishAsync(new PREBWEvent(_preInfo, _leechtopCount));
				PreInfos.getPreInfosSingleton().getPreInfos().remove(_preInfo);
			} catch(NumberFormatException ex) {
				logger.warn("",ex);
			} catch(InterruptedException ex) {
				logger.warn("",ex);
			} catch(IndexOutOfBoundsException ex) {
				logger.warn("",ex);
			} catch(NullPointerException ex) {
                // Should not be possible as we declare and add a PreInfo object in doPREPostHook
				logger.error("",ex);
			}
		}

		private long getPreSpeed() {
			List<BaseFtpConnection> conns = ConnectionManager.getConnectionManager().getConnections();
			long speed = 0L;
			for (BaseFtpConnection conn : conns) {
                logger.info("conn.getCurrentDirectory(): {}", conn.getCurrentDirectory());
                logger.info("_preInfo.getDir(): {}", _preInfo.getDir());
				if (conn.isAuthenticated() && conn.isExecuting() &&
						conn.getCurrentDirectory().getPath().startsWith(_preInfo.getDir().getPath())) {
					logger.info("Valid");
					TransferState ts = conn.getTransferState();
					if (ts.isTransfering() && ts.getDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                        logger.info("Transfering & TRANSFER_SENDING_DOWNLOAD, speed: {}", ts.getXferSpeed());
						speed += ts.getXferSpeed();
					}
				}
			}
			return speed;
		}
	}

    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
}
