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
package org.drftpd.commands.find;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.UserManagement;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.plugins.jobmanager.JobManager;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;


/**
 * SITE FIND <options>-action <action>Options: -user <user>-group
 * <group>-nogroup -nouser Options: -mtime [-]n -type [f|d] -slave <slave>-size
 * [-]size Options: -name <name>(* for wildcard) -incomplete -offline Actions:
 * print, wipe, delete Multipe options and actions are allowed. If multiple
 * options are given a file must match all options for action to be taken.
 * 
 * @author pyrrhic
 * @author mog
 * @author fr0w
 * @version $Id$
 */
public class Find extends CommandInterface {
	public static final Logger logger = Logger.getLogger(Find.class);
	
	private static interface Action {
		public String exec(CommandRequest request, InodeHandle inode);		
		public boolean execInDirs();
		public boolean execInFiles();
	}
	
	private static class ActionDeleteFromSlaves implements Action {
		private HashSet<RemoteSlave> _deleteFromSlaves;

		public ActionDeleteFromSlaves(HashSet<RemoteSlave> deleteFromSlaves) {
			assert deleteFromSlaves != null;
			_deleteFromSlaves = deleteFromSlaves;
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			FileHandle file = (FileHandle) inode;
			
			HashSet<RemoteSlave> deleteFromSlaves = new HashSet<RemoteSlave>(_deleteFromSlaves);
			String ret = file.getPath() + " deleted from ";
			
			for (RemoteSlave rslave : deleteFromSlaves) {
				rslave.simpleDelete(file.getPath());
				ret = ret + rslave.getName() + ",";
			}

			return ret.substring(0, ret.length() - 1);
		}

		public boolean execInDirs() {
			return false;
		}

		public boolean execInFiles() {
			return true;
		}		
	}


	private static class ActionDelete implements Action {
		private String doDELE(CommandRequest request, InodeHandle inode) {
			String reply = "";
			try {
				// check permission
				User user = request.getSession().getUserNull(request.getUser());
				FileHandle file = (FileHandle) inode;

				try {
					file.delete(user);
				} catch (PermissionDeniedException e) {
					return "Access denied for " + file.getPath();
				}

				reply = "Deleted " + file.getPath();

				User uploader = GlobalContext.getGlobalContext().getUserManager().getUserByName(file.getUsername());
				uploader.updateCredits((long) -(file.getSize() * uploader.getKeyedMap().getObjectFloat(UserManagement.RATIO)));				
			} catch (UserFileException e) {
				reply += "Error removing credits: " + e.getMessage();
			} catch (NoSuchUserException e) {
				reply += "Error removing credits: " + e.getMessage();
			} catch (FileNotFoundException e) {
				logger.error("The file was there and now it's gone, how?", e);
			}		

			return reply;
		}
		
		public boolean execInDirs() {
			return false;
		}

		public boolean execInFiles() {
			return true;
		}	

		public String exec(CommandRequest request, InodeHandle file) {
			return doDELE(request, file);
		}
	}

	private static class ActionPrint implements Action {
		public String exec(CommandRequest request, InodeHandle inode) {
			return inode.getPath();
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return true;
		}
	}

	private static class ActionPrintf implements Action {
		private String _format;

		private String parent;

		public ActionPrintf(String f) {
			_format = f;
			if (_format == null) {
				throw new NullPointerException();
			}
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			return formatOutput(inode);
		}

		private String formatOutput(InodeHandle inode)
				throws NumberFormatException {
			
			HashMap<String, String> formats = new HashMap<String, String>();
			
			try {
				formats.put("#f", inode.getName());
				formats.put("#s", Bytes.formatBytes(inode.getSize()));
				formats.put("#u", inode.getUsername());
				formats.put("#g", inode.getGroup());
				formats.put("#t", new Date(inode.lastModified()).toString());

				if (inode.isFile())			
					formats.put("#x", ((FileHandle) inode).getSlaves().toString());
				else
					formats.put("#x", "no slaves");
				
				formats.put("#h", parent); 
			} catch (FileNotFoundException e) {
				logger.error("The file was there and now it's gone, how?", e);
			}
			
			String temp = _format;

			for (Map.Entry<String, String> entry : formats.entrySet()) {
				temp = temp.replaceAll(entry.getKey(), entry.getValue());
			}

			return temp;
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return true;
		}
	}

	private static class ActionSendToSlaves implements Action {
		private HashSet<RemoteSlave> _destSlaves;

		private int _priority;

		private int _transferNum;

		public ActionSendToSlaves(int transferNum,
				HashSet<RemoteSlave> destSlaves, int priority) {
			_transferNum = transferNum;
			_destSlaves = destSlaves;
			_priority = priority;
		}
		
		public JobManager getJobManager() {
			for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
				if (plugin instanceof JobManager) {
					return (JobManager) plugin;
				}
			}
			throw new RuntimeException("JobManager is not loaded");
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			FileHandle file = (FileHandle) inode;
			getJobManager().addJobToQueue(new Job(file, _priority, _transferNum, _destSlaves));
			return file.getName() + " added to jobqueue";
		}

		public boolean execInDirs() {
			return false;
		}

		public boolean execInFiles() {
			return true;
		}

	}

	private static class ActionWipe implements Action {

		public String exec(CommandRequest request, InodeHandle inode) {
			try {
				inode.deleteUnchecked(); // TODO does wipe needs to be checked against delete perms?
			} catch (FileNotFoundException e) {
				logger.error("The file was there and now it's gone, how?", e);
			}
			return "Wiped " + inode.getPath();
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return true;
		}
	}

	private static interface Option {
		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException;
	}

	private static class OptionGroup implements Option {
		private String groupname;

		public OptionGroup(String g) {
			groupname = g;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			return inode.getGroup().equals(groupname);
		}
	}

	//TODO can we depend on the zipscript?
	private static class OptionIncomplete implements Option {
		private int _minPercent;

		public OptionIncomplete() {
		}

		public OptionIncomplete(int minPercent) {
			_minPercent = minPercent;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			DirectoryHandle dir = (DirectoryHandle) inode;
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
				SFVInfo info = sfvData.getSFVInfo();
				SFVStatus status = sfvData.getSFVStatus();
				if (_minPercent == 0)
					return !status.isFinished();
				return status.getPresent() * 100 / info.getSize()< _minPercent;
			} catch (Exception e) {
				return false;
			}
		}
	}

	private static class OptionMTime implements Option {
		boolean after;

		private Date date;

		public OptionMTime(int h) {
			after = true;

			if (h < 0) {
				after = false;
				h = Math.abs(h);
			}

			long t = (long) h * 24 * 60 * 60 * 1000;
			Date currentDate = new Date();
			date = new Date(currentDate.getTime() - t);
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			Date fileDate = new Date(inode.lastModified());
			return after ? fileDate.after(date) : fileDate.before(date);
		}
	}

	private static class OptionName implements Option {
		Pattern pattern;

		public OptionName(String str) {
		    str = str.replaceAll("\\[", "\\\\[");
		    str = str.replaceAll("\\]", "\\\\]");
		    str = str.replaceAll("\\(", "\\\\(");
		    str = str.replaceAll("\\)", "\\\\)");
		    str = str.replaceAll("[*]", ".*");
			pattern = Pattern.compile(str);
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			Matcher m = pattern.matcher(inode.getName());
			return m.matches();
		}
	}

	private static class OptionOffline implements Option {
		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			DirectoryHandle dir = (DirectoryHandle) inode;
			return dir.hasOfflineFiles();
		}
	}

	private static class OptionSize implements Option {
		boolean bigger;

		long size;

		public OptionSize(long s, boolean b) {
			bigger = b;
			size = s;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			return bigger ? (inode.getSize() >= size) : (inode.getSize() <= size);
		}
	}

	private static class OptionSlave implements Option {
		RemoteSlave slave;

		public OptionSlave(RemoteSlave s) {
			slave = s;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			FileHandle file = (FileHandle) inode;
			return file.getSlaves().contains(slave);
		}
	}

	private static class OptionType implements Option {
		boolean dirs;

		boolean files;

		public OptionType(boolean f, boolean d) {
			files = f;
			dirs = d;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			if (files && dirs) {
				return true;
			} else if (files && !dirs) {
				return inode.isFile();
			} else if (!files && dirs) {
				return inode.isDirectory();
			}

			return true;
		}
	}

	private static class OptionUser implements Option {
		private String username;

		public OptionUser(String u) {
			username = u;
		}

		public boolean isTrueFor(InodeHandle inode) throws FileNotFoundException {
			return inode.getUsername().equals(username);
		}
	}

	private static void findFile(CommandRequest request, CommandResponse response, DirectoryHandle dir, 
			Collection<Option> options,	ArrayList<Action> actions, boolean files, boolean dirs) throws FileNotFoundException {
		
		User user = null;
		try {
			user = request.getUserObject();
		} catch (NoSuchUserException e) {
			logger.error("The user just issued the command, how doesnt it exist?", e);
			return;
		} catch (UserFileException e) {
			logger.error("Error reading userfile", e);
			return;
		}

		for (InodeHandle inode : dir.getInodeHandles(user)) {
			if (inode.isDirectory()) {
				logger.debug("findFile("+inode.getPath()+")");
				findFile(request, response, (DirectoryHandle) inode, options, actions, files, dirs);
			}

			if ((dirs && inode.isDirectory()) || (files && inode.isFile())) {
				boolean checkIt = true;

				for (Option findOption : options) {
					if (response.size() >= 100) {
						return;
					}

					if (!findOption.isTrueFor(inode)) {
						logger.debug(findOption.getClass()+".isTrueFor("+inode.getPath()+") return false");
						checkIt = false;
						break;
					}
				}

				if (!checkIt) {
					continue;
				}

				for (Action findAction : actions) {
					if ((inode.isFile() && findAction.execInFiles()) ||
							inode.isDirectory() && findAction.execInDirs()) {
						logger.debug("Action "+ findAction.getClass() + " executing on " + inode.getPath());
						response.addComment(findAction.exec(request, inode));
					}
					

					if (response.size() == 100 && actions.size() == 1
							&& actions.get(0) instanceof ActionPrint) {
						response.addComment("<snip>");
						return;
					}
				}
			}
		}
	}

	private static String getArgs(String str) {
		int start = str.indexOf("(");
		int end = str.indexOf(")");

		if ((start == -1) || (end == -1)) {
			return null;
		}

		if (start > end) {
			return null;
		}

		return str.substring(start + 1, end);
	}

	public CommandResponse doFIND(CommandRequest request) throws ImproperUsageException {

		Collection<String> argsList = new ArrayList<String>();
		
		if (request.hasArgument())
			for (String s : request.getArgument().split(" "))
				argsList.add(s);
		

		//Collection<String> c = Arrays.asList(args);
		ArrayList<Option> options = new ArrayList<Option>();
		ArrayList<Action> actions = new ArrayList<Action>();
		boolean files = true;
		boolean dirs = true;
		boolean forceFilesOnly = false;
		boolean forceDirsOnly = false;

		for (Iterator<String> iter = argsList.iterator();iter.hasNext();) {
			String arg = iter.next();
			
			if (!arg.equalsIgnoreCase("-offline") && !arg.equalsIgnoreCase("-nouser") && !arg.equalsIgnoreCase("-nogroup")
					&& !arg.equalsIgnoreCase("-incomplete") && !iter.hasNext()) {
				throw new ImproperUsageException();			
			}
			
			if (arg.equalsIgnoreCase("-user")) {
				options.add(new OptionUser(iter.next()));
			} else if (arg.equalsIgnoreCase("-group")) {
				options.add(new OptionGroup(iter.next()));
			} else if (arg.equalsIgnoreCase("-name")) {
				options.add(new OptionName(iter.next()));
			} else if (arg.equalsIgnoreCase("-slave")) {
				RemoteSlave rs = null;
				String slaveName = iter.next();

				try {
					rs = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
				} catch (ObjectNotFoundException e) {
					return new CommandResponse(500, "Slave " + slaveName+ " was not found.");
				}

				forceFilesOnly = true;
				options.add(new OptionSlave(rs));
			} else if (arg.equalsIgnoreCase("-mtime")) {
				int offset = 0;

				try {
					offset = Integer.parseInt(iter.next());
				} catch (NumberFormatException e) {
					throw new ImproperUsageException();
				}

				options.add(new OptionMTime(offset));
			} else if (arg.equalsIgnoreCase("-size")) {
				long size = 0;
				boolean bigger = true;
				String bytes = iter.next();

				if (bytes.startsWith("-")) {
					bigger = false;
					bytes = bytes.substring(1);
				}

				try {
					size = Bytes.parseBytes(bytes);
				} catch (NumberFormatException e) {
					throw new ImproperUsageException();
				}

				options.add(new OptionSize(size, bigger));
			} else if (arg.equalsIgnoreCase("-type")) {
				String type = iter.next().toLowerCase();

				if (type.equals("f")) {
					dirs = false;
				} else if (type.equals("d")) {
					files = false;
				} else {
					throw new ImproperUsageException();
				}
			} else if (arg.equalsIgnoreCase("-nouser")) {
				options.add(new OptionUser("nobody"));
			} else if (arg.equalsIgnoreCase("-incomplete")) {
				forceDirsOnly = true;
			
				if (iter.hasNext()) {
					String parm = iter.next();
					int i = Math.abs(Integer.parseInt(parm));
					options.add(new OptionIncomplete(i));
				} else {
					options.add(new OptionIncomplete());
				}
			} else if (arg.equalsIgnoreCase("-offline")) {
				forceDirsOnly = true;
				options.add(new OptionOffline());
			} else if (arg.equalsIgnoreCase("-nogroup")) {
				options.add(new OptionGroup("drftpd"));
			} else if (arg.equalsIgnoreCase("-action")) {
				
				String action = iter.next();

				if (action.indexOf("(") != -1) {
					String cmd = action.substring(0, action.indexOf("("));
					boolean go = true;

					while (go) {
						if (action.endsWith(")")) {
							Action findAction = getActionWithArgs(cmd,
									getArgs(action));
							actions.add(findAction);
							go = false;

							continue;
						} else if (!iter.hasNext()) {
							throw new ImproperUsageException();
						} else {
							action += (" " + iter.next());
						}
					}
				} else if (action.equals("sendtoslaves")) {
					if (!checkCustomPermission(request, "sendToSlaves", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for sendToSlaves");
					}
					// -action sendtoslaves
					// <numtransfers[:slave[,slave,..][:priority]]>
					List<String> actionArgs = Arrays.asList(iter.next().split(
							":"));
					int numOfSlaves = Integer.parseInt(actionArgs.get(0));
					int priority = 0;
					if (actionArgs.size() >= 3) {
						priority = Integer.parseInt(actionArgs.get(2));
					}
					actions.add(new ActionSendToSlaves(numOfSlaves, parseSlaves(iter.next()), priority));
				} else if (action.equals("deletefromslaves")) {
					if (!checkCustomPermission(request, "deleteFromSlaves", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for deleteFromSlaves");
					}
					// -action deletefromslaves <slave[,slave[,...]]>
					actions.add(new ActionDeleteFromSlaves(parseSlaves(iter.next())));
				} else {
					Action findAction = getAction(action.toLowerCase());

					if (findAction == null) {
						throw new ImproperUsageException();
					}

					if (findAction instanceof ActionWipe) {
						if (!checkCustomPermission(request, "wipe", "=siteop")) {
							return new CommandResponse(500, "You do not have the proper permissions for wipe");
						}					}

					actions.add(findAction);
				}
			} else {
				throw new ImproperUsageException();
			}
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		if (actions.size() == 0) {
			actions.add(new ActionPrint());
		}

		if (!dirs && !files) {
			dirs = true;
			files = true;
		}

		if (forceFilesOnly && forceDirsOnly) {
			return new CommandResponse(500,"Option conflict. Possibly -slave and -incomplete.");
		} else if (forceFilesOnly) {
			dirs = false;
			response.addComment("Forcing a file only search because of -slave option.");
		} else if (forceDirsOnly) {
			files = false;
			response.addComment("Forcing a dir only search.");
		}

		options.add(new OptionType(files, dirs));
		
		try {
			findFile(request, response, request.getCurrentDirectory(), options, actions, files, dirs);
		} catch (FileNotFoundException e) {
			logger.error("The file was there and now it's gone, how?", e);
		}
		
		return response;
	}

	private Action getAction(String actionName) {
		if (actionName.equals("print")) {
			return new ActionPrint();
		} else if (actionName.equals("wipe")) {
			return new ActionWipe();
		} else if (actionName.equals("delete")) {
			return new ActionDelete();
		} else {
			return null;
		}
	}

	private Action getActionWithArgs(String actionName, String args) {
		if (actionName.equals("printf")) {
			return new ActionPrintf(args);
		}

		return null;
	}
	
	private HashSet<RemoteSlave> parseSlaves(String s) {
		List<String> destSlaveNames = Arrays.asList(s.split(",")); 
		HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();
		for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
			if (destSlaveNames.contains(rslave.getName()))
				destSlaves.add(rslave);
		}
		return destSlaves;
	}
}
