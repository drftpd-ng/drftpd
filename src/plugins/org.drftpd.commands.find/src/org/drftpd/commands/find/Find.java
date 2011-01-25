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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

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
import org.drftpd.commands.nuke.NukeBeans;
import org.drftpd.commands.nuke.NukeException;
import org.drftpd.commands.nuke.NukeUtils;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.plugins.jobmanager.JobManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @author pyrrhic
 * @author mog
 * @author fr0w
 * @author scitz0
 * @version $Id$
 */
public class Find extends CommandInterface {
	public static final Logger logger = Logger.getLogger(Find.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}
	
	private static interface Action {
		public String exec(CommandRequest request, InodeHandle inode);
		public boolean execInDirs();
		public boolean execInFiles();
		public boolean failed();
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

		public boolean failed() {
			return false;
		}
	}


	private static class ActionDelete implements Action {
		private boolean _failed;

		private String doDELE(CommandRequest request, InodeHandle inode) {
			String reply = "";
			try {
				// check permission
				User user = request.getSession().getUserNull(request.getUser());
				FileHandle file = (FileHandle) inode;

				try {
					file.delete(user);
				} catch (PermissionDeniedException e) {
					_failed = true;
					return "Access denied for " + file.getPath();
				}

				reply = "Deleted " + file.getPath();

				User uploader = GlobalContext.getGlobalContext().getUserManager().getUserByName(file.getUsername());
				uploader.updateCredits((long) -(file.getSize() * uploader.getKeyedMap().getObjectFloat(UserManagement.RATIO)));				
			} catch (UserFileException e) {
				reply += " - Error removing credits: " + e.getMessage();
				_failed = true;
			} catch (NoSuchUserException e) {
				reply += " - Error removing credits: " + e.getMessage();
				_failed = true;
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

		public boolean failed() {
			return _failed;
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

		public boolean failed() {
			return false;
		}
	}

	private static class ActionPrintf implements Action {
		private String _format;

		public ActionPrintf(String f) {
			_format = f;
			if (_format == null) {
				throw new NullPointerException();
			}
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			return formatOutput(inode);
		}

		private String formatOutput(InodeHandle inode) {
			
			HashMap<String, String> formats = new HashMap<String, String>();
			
			try {
				logger.debug("printf name: " + inode.getName());
				formats.put("#f", inode.getName());
				formats.put("#p", inode.getPath());
				formats.put("#s", Bytes.formatBytes(inode.getSize()));
				formats.put("#u", inode.getUsername());
				formats.put("#g", inode.getGroup());
				formats.put("#t", new Date(inode.lastModified()).toString());

				if (inode.isFile())			
					formats.put("#x", ((FileHandle) inode).getSlaves().toString());
				else
					formats.put("#x", "no slaves");
				
				formats.put("#H", inode.getParent().getName());
				formats.put("#h", inode.getParent().getPath());
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

		public boolean failed() {
			return false;
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

		public boolean failed() {
			return false;
		}
	}

	private static class ActionWipe implements Action {
		private boolean _failed;

		public String exec(CommandRequest request, InodeHandle inode) {
			try {
				inode.delete(request.getSession().getUserNull(request.getUser()));
			} catch (FileNotFoundException e) {
				logger.error("The file was there and now it's gone, how?", e);
			} catch (PermissionDeniedException e) {
				_failed = true;
				return "You do not have the proper permissions to wipe " + inode.getPath();
			}
			return "Wiped " + inode.getPath();
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return true;
		}

		public boolean failed() {
			return _failed;
		}
	}

	private static class ActionNuke implements Action {
		private boolean _failed;
		private int _multiplier;
		private String _reason;

		public ActionNuke(int multiplier, String reason) {
			_multiplier = multiplier;
			_reason = reason;
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			DirectoryHandle dir = (DirectoryHandle)inode;

			// Check if dir is nuked already, remove nuke prefix if necessary
			String dirName = VirtualFileSystem.getLast(
					NukeUtils.getPathWithoutNukePrefix(dir.getPath()));
			NukeData nd = NukeBeans.getNukeBeans().findName(dirName);
			if (nd != null) {
				_failed = true;
				return "Access denied - " +
					nd.getPath() + " already nuked for '"+ nd.getReason() + "'";
			}

			User user = request.getSession().getUserNull(request.getUser());
			try {
				nd = NukeUtils.nuke(dir, _multiplier, _reason, user);
			} catch (NukeException e) {
				_failed = true;
				return "Nuke failed for " + inode.getPath() + ": " + e.getMessage();
			}
			return "Successfully nuked " + nd.getPath();
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return false;
		}

		public boolean failed() {
			return _failed;
		}
	}

	private static class ActionUnnuke implements Action {
		private boolean _failed;
		private String _reason;

		public ActionUnnuke(String reason) {
			_reason = reason;
		}

		public String exec(CommandRequest request, InodeHandle inode) {
			// Try to unnuke dir, if its not previously nuked an NukeException will be thrown.
			NukeData nd;
			try {
				nd = NukeUtils.unnuke((DirectoryHandle)inode, _reason);
			} catch (NukeException e) {
				_failed = true;
				return "Unnuke failed for " + inode.getPath() + ": " + e.getMessage();
			}
			return "Successfully unnuked " + nd.getPath();
		}

		public boolean execInDirs() {
			return true;
		}

		public boolean execInFiles() {
			return false;
		}

		public boolean failed() {
			return _failed;
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
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		DirectoryHandle dir = request.getCurrentDirectory();

		ArrayList<Action> actions = new ArrayList<Action>();

		int limit = Integer.parseInt(request.getProperties().getProperty("limit.default","5"));
		int maxLimit = Integer.parseInt(request.getProperties().getProperty("limit.max","20"));

		boolean quiet = false;

		StringTokenizer st = new StringTokenizer(request.getArgument());

		while(st.hasMoreTokens()) {
			String option = st.nextToken();

			if (!st.hasMoreTokens()) {
				throw new ImproperUsageException();
			} else if (option.equalsIgnoreCase("-f") || option.equalsIgnoreCase("-file")) {
				params.setInodeType(AdvancedSearchParams.InodeType.FILE);
			} else if (option.equalsIgnoreCase("-d") || option.equalsIgnoreCase("-dir")) {
				params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
			} else if (option.equalsIgnoreCase("-quiet")) {
				quiet = true;
			} else if (option.equalsIgnoreCase("-user")) {
				params.setOwner(st.nextToken());
			} else if (option.equalsIgnoreCase("-group")) {
				params.setGroup(st.nextToken());
			} else if (option.equalsIgnoreCase("-slaves")) {
				HashSet<String> slaves = new HashSet<String>(Arrays.asList(st.nextToken().split(",")));
				params.setSlaves(slaves);
			} else if (option.equalsIgnoreCase("-age")) {
				SimpleDateFormat fullDate = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
				SimpleDateFormat shortDate = new SimpleDateFormat("yyyy.MM.dd");
				try {
					String[] range = getMinMax(st.nextToken());

					if (range[0] != null) {
						if (range[0].length() == 10)
							params.setMinAge(shortDate.parse(range[0]).getTime());
						else if (range[0].length() == 19)
							params.setMinAge(fullDate.parse(range[0]).getTime());
						else
							throw new ImproperUsageException("Invalid date format for min age.");
					}

					if (range[1] != null) {
						if (range[1].length() == 10)
							params.setMaxAge(shortDate.parse(range[1]).getTime());
						else if (range[1].length() == 19)
							params.setMaxAge(fullDate.parse(range[1]).getTime());
						else
							throw new ImproperUsageException("Invalid date format for max age.");
					}
				} catch (ParseException e) {
					throw new ImproperUsageException("Invalid date format", e);
				}
			} else if (option.equalsIgnoreCase("-size")) {
				try {
					String[] range = getMinMax(st.nextToken());
					if (range[0] != null && range[1] != null) {
						long minSize = Bytes.parseBytes(range[0]);
						long maxSize = Bytes.parseBytes(range[1]);
						if (minSize > maxSize) {
							throw new ImproperUsageException("Size range invalid, min value higher than max");
						}
						params.setMinSize(minSize);
						params.setMaxSize(maxSize);
					} else if (range[0] != null) {
						params.setMinSize(Bytes.parseBytes(range[0]));
					} else if (range[1] != null) {
						params.setMaxSize(Bytes.parseBytes(range[1]));
					}
				} catch (NumberFormatException e) {
					throw new ImproperUsageException(e);
				}
			} else if (option.equalsIgnoreCase("-nbrofslaves")) {
				try {
					String[] range = getMinMax(st.nextToken());
					if (range[0] != null && range[1] != null) {
						int minNbrOfSlaves = Integer.parseInt(range[0]);
						int maxNbrOfSlaves = Integer.parseInt(range[1]);
						if (minNbrOfSlaves > maxNbrOfSlaves) {
							throw new ImproperUsageException("Slave number range invalid, min value higher than max");
						}
						params.setMinSlaves(minNbrOfSlaves);
						params.setMaxSlaves(maxNbrOfSlaves);
					} else if (range[0] != null) {
						params.setMinSlaves(Integer.parseInt(range[0]));
					} else if (range[1] != null) {
						params.setMaxSlaves(Integer.parseInt(range[1]));
					}
				} catch (NumberFormatException e) {
					throw new ImproperUsageException(e);
				}
			} else if (option.equalsIgnoreCase("-0byte")) {
				params.setMinSize(0L);
				params.setMaxSize(0L);
			} else if (option.equalsIgnoreCase("-section")) {
				dir = GlobalContext.getGlobalContext().getSectionManager().
						getSection(st.nextToken()).getBaseDirectory();
			} else if (option.equalsIgnoreCase("-sort")) {
				String field = st.nextToken();
				params.setSortField(field);
				if (!st.hasMoreTokens()) {
					throw new ImproperUsageException("You must specify both field and sort order");
				}
				String order = st.nextToken();
				if (order.equalsIgnoreCase("asc")) {
					params.setSortOrder(false);
				} else {
					params.setSortOrder(true);
				}
			} else if (option.equalsIgnoreCase("-name")) {
				String searchString = st.nextToken();
				if (searchString.charAt(0) == '"') {
					searchString = searchString.substring(1);
					while (true) {
						if (searchString.endsWith("\"")) {
							searchString = searchString.substring(0,searchString.length()-1);
							break;
						} else if (!st.hasMoreTokens()) {
							throw new ImproperUsageException();
						} else {
							searchString += " " + st.nextToken();
						}
					}
				}
				params.setName(searchString);
			} else if (option.equalsIgnoreCase("-exact")) {
				params.setExact(true);
			} else if (option.equalsIgnoreCase("-endswith")) {
				params.setEndsWith(st.nextToken());
			} else if (option.equalsIgnoreCase("-limit")) {
				try {
					int newLimit = Integer.parseInt(st.nextToken());
					if (newLimit < maxLimit) {
						limit = newLimit;
					} else {
						limit = maxLimit;
					}
				} catch (NumberFormatException e) {
					throw new ImproperUsageException("Limit must be valid number.");
				}
			} else if (option.equalsIgnoreCase("-action")) {
				String action = st.nextToken();
				if (action.indexOf("(") != -1) {
					String cmd = action.substring(0, action.indexOf("("));
					while (true) {
						if (action.endsWith(")")) {
							actions.add(getActionWithArgs(cmd,
									getArgs(action)));
							break;
						} else if (!st.hasMoreTokens()) {
							throw new ImproperUsageException();
						} else {
							action += " " + st.nextToken();
						}
					}
				} else if (action.equals("sendtoslaves")) {
					if (!checkCustomPermission(request, "sendToSlaves", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for sendToSlaves");
					}
					if (!st.hasMoreTokens()) {
						throw new ImproperUsageException();
					}
					// -action sendtoslaves
					// <numtransfers[:slave[,slave,..][:priority]]>
					List<String> actionArgs = Arrays.asList(st.nextToken().split(":"));
					int numOfSlaves = Integer.parseInt(actionArgs.get(0));
					int priority = 0;
					if (actionArgs.size() >= 3) {
						priority = Integer.parseInt(actionArgs.get(2));
					}
					actions.add(new ActionSendToSlaves(numOfSlaves, parseSlaves(actionArgs.get(1)), priority));
				} else if (action.equals("nuke")) {
					if (!checkCustomPermission(request, "nuke", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for nuke");
					}
					if (!st.hasMoreTokens()) {
						throw new ImproperUsageException();
					}
					// -action nuke <multiplier[:reason]]>
					String actionArgs[] = st.nextToken().split(":");
					int multiplier = Integer.parseInt(actionArgs[0]);
					String reason = "";
					if (actionArgs.length == 2) {
						reason = actionArgs[1];
					}
					actions.add(new ActionNuke(multiplier, reason));
				} else if (action.equals("unnuke")) {
					if (!checkCustomPermission(request, "nuke", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for unnuke");
					}
					// -action unnuke [reason]
					String reason = "";
					if (st.hasMoreTokens()) {
						reason = st.nextToken();
					}
					actions.add(new ActionUnnuke(reason));
				} else if (action.equals("deletefromslaves")) {
					if (!checkCustomPermission(request, "deleteFromSlaves", "=siteop")) {
						return new CommandResponse(500, "You do not have the proper permissions for deleteFromSlaves");
					}
					// -action deletefromslaves <slave[,slave[,...]]>
					actions.add(new ActionDeleteFromSlaves(parseSlaves(st.nextToken())));
				} else {
					Action findAction = getAction(action.toLowerCase());

					if (findAction == null) {
						throw new ImproperUsageException();
					}

					if (findAction instanceof ActionWipe) {
						if (!checkCustomPermission(request, "wipe", "=siteop")) {
							return new CommandResponse(500, "You do not have the proper permissions for wipe");
						}
					}

					actions.add(findAction);
				}
			}
		}

		if (actions.isEmpty()) {
			throw new ImproperUsageException();
		}

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		try {
			inodes = ie.advancedFind(dir, params);
		} catch (IndexException e) {
			logger.error(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.info(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		}

		ReplacerEnvironment env = new ReplacerEnvironment();

		User user = request.getSession().getUserNull(request.getUser());

		Session session = request.getSession();

		CommandResponse response = new CommandResponse(200, "Find complete!");

		if (inodes.isEmpty()) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"find.empty", env, user.getName()));
			return response;
		}

		LinkedList<String> responses = new LinkedList<String>();
		int results = 0;
		
		InodeHandle inode;
		for (Map.Entry<String,String> item : inodes.entrySet()) {
			if (results == limit)
				break;
			try {
				inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey().
						substring(0, item.getKey().length()-1)) : new FileHandle(item.getKey());
				if (inode.isHidden(user) ||
						inode.getPath().matches(request.getProperties().getProperty("path_filter",""))) {
					// No access or path filtered for this command
					continue;
				}
				env.add("name", inode.getName());
				env.add("path", inode.getPath());
				env.add("owner", inode.getUsername());
				env.add("group", inode.getGroup());
				env.add("size", Bytes.formatBytes(inode.getSize()));
				for (Action findAction : actions) {
					if ((inode.isFile() && findAction.execInFiles()) ||
							(inode.isDirectory() && findAction.execInDirs())) {
						logger.debug("Action "+ findAction.getClass() + " executing on " + inode.getPath());
						String text = findAction.exec(request, inode);
						if (!quiet || findAction.failed())
							responses.add(text);
					}
				}
				results++;
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + item.getKey());
			}
		}

		if (results == 0) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"find.empty", env, user.getName()));
			return response;
		}

		env.add("limit", limit);
		env.add("results", results);
		response.addComment(session.jprintf(_bundle,_keyPrefix+"find.header", env, user.getName()));

		for (String line : responses) {
			response.addComment(line);
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

	private String[] getMinMax(String arg) {
		arg = arg.trim();
		String[] range = new String[2];
		int i = arg.indexOf(":");
		if (i == -1) {
			range[0] = arg;
			range[1] = arg;
		} else {
			String min = arg.substring(0,i);
			String max = arg.substring(i+1);
			range[0] = min.isEmpty() ? null : min;
			range[1] = max.isEmpty() ? null : max;
		}
		return range;
	}
}
