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
package org.drftpd.commands.find.action;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.find.FindUtils;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.plugins.jobmanager.JobManager;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.util.HashSet;

/**
 * @author scitz0
 * @version $Id$
 */
public class SendToSlavesAction implements ActionInterface {
	private HashSet<RemoteSlave> _destSlaves;

	private int _priority;

	private int _transferNum;

	@Override
	public void initialize(String action, String[] args) throws ImproperUsageException {
		if (args == null || args.length < 2) {
			throw new ImproperUsageException("Missing argument for "+action+" action");
		}
		// -sendtoslaves <numtransfers> <slave[,slave,..]> [priority]
		_transferNum = Integer.parseInt(args[0]);
		_priority = 0;
		if (args.length == 3) {
			_priority = Integer.parseInt(args[2]);
		}
		_destSlaves = FindUtils.parseSlaves(args[1], ",");
	}

	public JobManager getJobManager() {
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof JobManager) {
				return (JobManager) plugin;
			}
		}
		throw new RuntimeException("JobManager is not loaded");
	}

	@Override
	public String exec(CommandRequest request, InodeHandle inode) {
		FileHandle file = (FileHandle) inode;
		getJobManager().addJobToQueue(new Job(file, _priority, _transferNum, _destSlaves));
		return file.getName() + " added to jobqueue";
	}

	@Override
	public boolean execInDirs() {
		return false;
	}

	@Override
	public boolean execInFiles() {
		return true;
	}

	@Override
	public boolean failed() {
		return false;
	}
}
