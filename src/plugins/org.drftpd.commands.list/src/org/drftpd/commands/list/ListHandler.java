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
package org.drftpd.commands.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.FtpReply;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.master.TransferState;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.FileHandleInterface;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.InodeHandleInterface;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ListHandler extends CommandInterface {

	private static final Logger logger = Logger.getLogger(ListHandler.class);

	private static final DateFormat AFTER_SIX = new SimpleDateFormat(" yyyy");

	private static final DateFormat BEFORE_SIX = new SimpleDateFormat("HH:mm");

	private static final DateFormat FULL = new SimpleDateFormat("HH:mm:ss yyyy");

	private static final String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May",
		"Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	private static final SimpleDateFormat MLSTTIME = new SimpleDateFormat("yyyyMMddHHmmss.SSS");

	private static final String NEWLINE = "\r\n";

	private static final String DELIM = " ";

	private static final String PADDING = "          ";

	private ArrayList<AddListElementsInterface> _listAddons = new ArrayList<AddListElementsInterface>();

	private StandardCommandManager _cManager;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	@Override
	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_cManager = cManager;
		_featReplies = new String[] {
				"MLST type*,x.crc32*,size*,modify*,unix.owner*,unix.group*,x.slaves*,x.xfertime*"
		};
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";

		// Subscribe to events
		AnnotationProcessor.process(this);

		// Load any additional element providers from plugins
		try {
			List<AddListElementsInterface> loadedListAddons =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.commands.list", "AddElements", "Class");
			for (AddListElementsInterface listAddon : loadedListAddons) {
				listAddon.initialize();
				_listAddons.add(listAddon);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.list extension point 'AddElements', possibly the "+
					"org.drftpd.commands.list extension point definition has changed in the plugin.xml",e);
		}
	}

	public CommandResponse doLIST(CommandRequest request) throws ImproperUsageException {
		return list(request, true, false, false, false);
	}

	public CommandResponse doSTAT(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
			
			ReplacerEnvironment env = new ReplacerEnvironment();
			
			env.add("ssl.enabled", conn.isSecure() ? "Yes" : "No");
			env.add("user", conn.getUsername());
			env.add("user.ip", conn.getObject(BaseFtpConnection.ADDRESS, null).getHostAddress());
			env.add("user.timeout", conn.getUserNull().getIdleTime());
			env.add("conns", ConnectionManager.getConnectionManager().getConnections().size()); // TODO sync this.
			env.add("version", GlobalContext.VERSION);
			
			CommandResponse response = new CommandResponse(211, "End of status");
			response.addComment(conn.jprintf(_bundle, env, _keyPrefix+ "daemon.stat"));
			
			return response;
		}
		return list(request, false, true, false, false);
	}

	public CommandResponse doMLST(CommandRequest request) throws ImproperUsageException {
		return list(request, false, false, true, false);
	}

	public CommandResponse doMLSD(CommandRequest request) throws ImproperUsageException {
		return list(request, false, false, false, true);
	}

	private CommandResponse list(CommandRequest request, boolean isList, boolean isStat, boolean isMlst, boolean isMlsd) 
	throws ImproperUsageException {
		try {
			String directoryName = null;
			String options = "";
			BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
			TransferState ts = conn.getTransferState();

			if (request.hasArgument()) {
				StringBuffer optionsSb = new StringBuffer(4);
				StringTokenizer st = new StringTokenizer(request.getArgument()," ");

				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					if (token.charAt(0) == '-') {
						if (isStat || isList) {
							if (token.length() > 1) {
								optionsSb.append(token.substring(1));
							}
						} else {
							throw new ImproperUsageException();
						}
					} else {
						directoryName = token;
					}
				}
				options = optionsSb.toString();
			}

			boolean fulldate = options.indexOf('T') != -1;

			if (!ts.isPasv() && !ts.isPort() && !isStat && !isMlst) {
				return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
			}

			DirectoryHandle directoryFile;
			CommandResponse response = null;	
			User user = request.getSession().getUserNull(request.getUser());

			if (directoryName != null) {
				try {
					directoryFile = conn.getCurrentDirectory().getDirectory(directoryName, user);
				} catch (FileNotFoundException ex) {
					return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
				} catch (ObjectNotValidException e) {
					return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
				}
			} else {
				directoryFile = conn.getCurrentDirectory();
			}
			Writer os = null;

			if (isStat) {
				response = new CommandResponse(213, "End of STAT");
				conn.printOutput("213-STAT"+NEWLINE);
				os = conn.getControlWriter();
			} else if (isMlst) {
				response = new CommandResponse(250, "End of MLST");
				conn.printOutput("250-MLST" + NEWLINE);
				os = conn.getControlWriter();
			} else {
				if (!ts.getSendFilesEncrypted()
						&& GlobalContext.getConfig().checkPermission(
								"denydiruncrypted", conn.getUserNull())) {
					return new CommandResponse(550, "Secure Listing Required");
				}

				conn.printOutput(new FtpReply(StandardCommandManager.genericResponse("RESPONSE_150_OK")));

				try {
					os = new PrintWriter(new OutputStreamWriter(ts.getDataSocketForLIST().getOutputStream()));
				} catch (IOException ex) {
					logger.warn(ex);
					return new CommandResponse(425, ex.getMessage());
				}
			}
			ListElementsContainer container = null;

			try {
				container = listElements(directoryFile, conn, request.getUser());
			} catch (IOException e) {
				logger.error(e);
				return new CommandResponse(450, e.getMessage());
			}

			// execute list addons.
			for (AddListElementsInterface listAddon : _listAddons) {
				container = listAddon.addElements(directoryFile,container);
			}

			try {
				if (isStat || isList) {
					os.write(toList(container.getElements(), fulldate));
				} else {
					os.write(toMLST(container.getElements()));
				}
				if (isStat || isMlst)
					return response;
				else {
					os.close();
					response = StandardCommandManager.genericResponse("RESPONSE_226_CLOSING_DATA_CONNECTION");
					response.addComment(conn.status());
					return response;
				}
			} catch (IOException ex) {
				logger.warn(ex);
				return new CommandResponse(450, ex.getMessage());
			}
		} finally {
			BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
			conn.getTransferState().reset();
		}
	}

	private ListElementsContainer listElements(DirectoryHandle dir, Session session, String user) throws IOException {
		ListElementsContainer container = new ListElementsContainer(session, user, _cManager);
		ArrayList<InodeHandle> tempFileList = new ArrayList<InodeHandle>(dir.getInodeHandles(session.getUserNull(user)));
		ArrayList<InodeHandleInterface> listFiles = container.getElements();
		ArrayList<String> fileTypes = container.getFileTypes();
		int numOnline = container.getNumOnline();
		int numTotal = container.getNumTotal();

		for (InodeHandle element : tempFileList) {
			boolean offlineFilesEnabled = GlobalContext.getConfig().getMainProperties().getProperty("files.offline.enabled", "true").equals("true");
			
			if (offlineFilesEnabled && element.isFile()) {
				if (!((FileHandleInterface) element).isAvailable()) {
					ReplacerEnvironment env = new ReplacerEnvironment();
					env.add("ofilename", element.getName());
					String oFileName = session.jprintf(_bundle, _keyPrefix+"files.offline.filename", env, user);

					listFiles.add(new LightRemoteInode(oFileName, element.getUsername(), element.getGroup(), element.lastModified(), element.getSize()));
					numTotal++;
				}
				// -OFFLINE and "ONLINE" files will both be present until someone implements
				// a way to reupload OFFLINE files.
				// It could be confusing to the user and/or client if the file doesn't exist, but you can't upload it. 
			}

			if (element.isFile()) {
				//else element is a file, and is online
				int typePosition = element.getName().lastIndexOf(".");
				String fileType;
				if (typePosition != -1) {
					fileType = element.getName().substring(typePosition, element.getName().length());
					if (!fileTypes.contains(fileType)) {
						fileTypes.add(fileType);
					}
				}
			}
			numOnline++;
			numTotal++;
			listFiles.add(element);
		}

		container.setNumOnline(numOnline);
		container.setNumTotal(numTotal);
		return container;
	}

	private String toMLST(Collection<InodeHandleInterface> listElements) {
		StringBuilder output = new StringBuilder();

		for (InodeHandleInterface inode : listElements) {
			try {
				if (inode.isLink()) {
					output.append("type=OS.unix=slink:" + ((LinkHandle) inode).getTargetString() + ";");
				} else if (inode.isFile()) {
					output.append("type=file;");
				} else if (inode.isDirectory()) {
					output.append("type=dir;");
				} else {
					throw new RuntimeException("type");
				}

				FileHandle file = null;
				boolean isFileHandle = false;
				if (inode.isFile() && inode instanceof FileHandle) {
					file = (FileHandle) inode;
					isFileHandle = true;
				}

				try {
					if (isFileHandle && file.getCheckSum() != 0 && file.getSize() != 0L) {
						output.append("x.crc32=" + Checksum.formatChecksum(file.getCheckSum())+ ";");
					}
				} catch (NoAvailableSlaveException e) {
					logger.debug("Unable to fetch checksum for: "+inode.getPath());
				}

				output.append("size=" + inode.getSize() + ";");
				synchronized(MLSTTIME) {
					output.append("modify=" + MLSTTIME.format(new Date(inode.lastModified())) +";");
				}

				output.append("unix.owner=" + inode.getUsername() + ";");
				output.append("unix.group=" + inode.getGroup() + ";");

				if (isFileHandle) {
					Iterator<RemoteSlave> iter = file.getSlaves().iterator();
					output.append("x.slaves=");

					if (iter.hasNext()) {
						output.append(iter.next().getName());

						while (iter.hasNext()) {
							output.append("," + iter.next().getName());
						}
					}

					output.append(";");
				}

				if (isFileHandle && file.getXfertime() != 0) {
					output.append("x.xfertime=" + file.getXfertime() + ";");
				}

				output.append(" " + inode.getName());
				output.append(NEWLINE);
			} catch (FileNotFoundException e) {
				logger.error("The file was there and now it's gone, how?", e);
			}
		}
		return output.toString();
	}

	private String toList(Collection<InodeHandleInterface> listElements, boolean fulldate) throws FileNotFoundException {
		StringBuilder output = new StringBuilder();

		for (InodeHandleInterface inode : listElements) {
			if (inode instanceof FileHandle
					&& !((FileHandle) inode).isAvailable()) {
				output.append("----------");
			} else {
				addPermission(inode, output);
			}

			output.append(DELIM);
			output.append((inode.isDirectory() ? "3" : "1"));
			output.append(DELIM);
			output.append(padToLength(inode.getUsername(), 8));
			output.append(DELIM);
			output.append(padToLength(inode.getGroup(), 8));
			output.append(DELIM);
			output.append(inode.getSize());
			output.append(DELIM);
			output.append(getUnixDate(inode.lastModified(), fulldate));
			output.append(DELIM);
			output.append(inode.getName());
			if (inode.isLink()) {
				output.append(DELIM + "->" + DELIM + ((LinkHandle)inode).getTargetString());
			}
			output.append(NEWLINE);
		}
		return output.toString();
	}

	private void addPermission(InodeHandleInterface inode, StringBuilder output) throws FileNotFoundException {
		if (inode.isLink()) {
			output.append("l");
		} else if (inode.isDirectory()) {
			output.append("d");
		} else {
			output.append("-");
		}
		output.append("rw");
		output.append(inode.isDirectory() ? "x" : "-");

		output.append("rw");
		output.append(inode.isDirectory() ? "x" : "-");

		output.append("rw");
		output.append(inode.isDirectory() ? "x" : "-");
	}

	private String getUnixDate(long date, boolean fulldate) {
		Date date1 = new Date(date);
		long dateTime = date1.getTime();

		if (dateTime < 0) {
			return "------------";
		}

		Calendar cal = new GregorianCalendar();
		cal.setTime(date1);

		String firstPart = MONTHS[cal.get(Calendar.MONTH)] + ' ';

		String dateStr = String.valueOf(cal.get(Calendar.DATE));

		if (dateStr.length() == 1) {
			dateStr = ' ' + dateStr;
		}

		firstPart += (dateStr + ' ');

		long nowTime = System.currentTimeMillis();

		if (fulldate) {
			synchronized(FULL) {
				return firstPart + FULL.format(date1);
			}
		} else if (Math.abs(nowTime - dateTime) > (183L * 24L * 60L * 60L * 1000L)) {
			synchronized(AFTER_SIX) {
				return firstPart + AFTER_SIX.format(date1);
			}
		} else {
			synchronized(BEFORE_SIX) {
				return firstPart + BEFORE_SIX.format(date1);
			}
		}
	}

	private String padToLength(String value, int length) {
		if (value.length() >= length) {
			return value;
		}

		if (PADDING.length() < length) {
			throw new RuntimeException("padding must be longer than length");
		}

		return PADDING.substring(0, length - value.length()) + value;
	}

	@EventSubscriber @Override
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		super.onUnloadPluginEvent(event);
		Set<AddListElementsInterface> unloadedListAddons =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "AddElements", event, _listAddons);
		if (!unloadedListAddons.isEmpty()) {
			ArrayList<AddListElementsInterface> clonedListAddons = new ArrayList<AddListElementsInterface>(_listAddons);
			boolean addonRemoved = false;
			for (Iterator<AddListElementsInterface> iter = clonedListAddons.iterator(); iter.hasNext();) {
				AddListElementsInterface listAddon = iter.next();
				if (unloadedListAddons.contains(listAddon)) {
					listAddon.unload();
					logger.debug("Unloading list element addon provided by plugin "
							+CommonPluginUtils.getPluginIdForObject(listAddon));
					iter.remove();
					addonRemoved = true;
				}
			}
			if (addonRemoved) {
				_listAddons = clonedListAddons;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<AddListElementsInterface> loadedListAddons =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.commands.list", "AddElements", "Class", event);
			if (!loadedListAddons.isEmpty()) {
				ArrayList<AddListElementsInterface> clonedListAddons = new ArrayList<AddListElementsInterface>(_listAddons);
				for (AddListElementsInterface listAddon : loadedListAddons) {
					listAddon.initialize();
					clonedListAddons.add(listAddon);
				}
				_listAddons = clonedListAddons;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.commands.list extension point 'AddElements'"+
					", possibly the org.drftpd.commands.list extension point definition has changed in the plugin.xml",e);
		}
	}
}
