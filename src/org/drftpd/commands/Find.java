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
package org.drftpd.commands;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
//import org.apache.log4j.Logger;
/**
 * @author B SITE FIND <options> -action <action>
 * Options: -user <user> -group <group> -nogroup -nouser 
 * Options: -mtime [-]n -type [f|d] -slave <slave> -size [-]size
 * Options: -name <name>(* for wildcard) -incomplete 
 * Actions: print, wipe, delete
 * Multipe options and actions
 *         are allowed. If multiple options are given a file must match all
 *         options for action to be taken. 
 */
public class Find implements CommandHandlerFactory, CommandHandler {
	public void unload() {
	}
	public void load(CommandManagerFactory initializer) {
	}
	private static void findFile(BaseFtpConnection conn, FtpReply response,
			LinkedRemoteFileInterface dir, Collection options,
			Collection actions, boolean files, boolean dirs) {
		//TODO optimize me, checking using regexp for all dirs is possibly slow
		if (!conn.getConfig().checkPrivPath(conn.getUserNull(), dir)) {
			//Logger.getLogger(Find.class).debug("privpath: " + dir.getPath());
			return;
		}
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter
					.next();
			if (file.isDirectory()) {
				findFile(conn, response, file, options, actions, files, dirs);
			}
			if (dirs && file.isDirectory() || files && file.isFile()) {
				boolean checkIt = true;
				for (Iterator iterator = options.iterator(); iterator.hasNext();) {
					if (response.size() >= 100)
						return;
					FindOption findOption = (FindOption) iterator.next();
					if (!findOption.isTrueFor(file)) {
						checkIt = false;
						break;
					}
				}
				if (!checkIt)
					continue;
				for (Iterator i = actions.iterator(); i.hasNext();) {
					FindAction findAction = (FindAction) i.next();
					response.addComment(findAction.exec(conn, file));
					if (response.size() >= 100) {
						response.addComment("<snip>");
						return;
					}
				}
			}
		}
	}
	private static FindAction getAction(String actionName) {
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
	public FtpReply execute(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		String args[] = request.getArgument().split(" ");
		if (args.length == 0) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		Collection c = Arrays.asList(args);
		ArrayList options = new ArrayList();
		ArrayList actions = new ArrayList();
		boolean files = true;
		boolean dirs = true;
		boolean forceFilesOnly = false;
		boolean forceDirsOnly = false;
		for (Iterator iter = c.iterator(); iter.hasNext();) {
			String arg = iter.next().toString();
			if (arg.toLowerCase().equals("-user")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				options.add(new OptionUser(iter.next().toString()));
			} else if (arg.toLowerCase().equals("-group")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				options.add(new OptionGroup(iter.next().toString()));
			} else if (arg.toLowerCase().equals("-name")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				options.add(new OptionName(iter.next().toString()));
			} else if (arg.toLowerCase().equals("-slave")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				RemoteSlave rs = null;
				String slaveName = iter.next().toString();
				try {
					rs = conn.getSlaveManager().getSlave(slaveName);
				} catch (ObjectNotFoundException e) {
					return new FtpReply(500, "Slave " + slaveName
							+ " was not found.");
				}
				forceFilesOnly = true;
				options.add(new OptionSlave(rs));
			} else if (arg.toLowerCase().equals("-mtime")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				int offset = 0;
				try {
					offset = Integer.parseInt(iter.next().toString());
				} catch (NumberFormatException e) {
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				}
				options.add(new OptionMTime(offset));
			} else if (arg.toLowerCase().equals("-size")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				long size = 0;
				boolean bigger = true;
				String bytes = iter.next().toString();
				if (bytes.startsWith("-")) {
					bigger = false;
					bytes = bytes.substring(1);
				}
				try {
					size = Bytes.parseBytes(bytes);
				} catch (NumberFormatException e) {
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				}
				options.add(new OptionSize(size, bigger));
			} else if (arg.toLowerCase().equals("-type")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				String type = iter.next().toString().toLowerCase();
				if (type.equals("f"))
					dirs = false;
				else if (type.equals("d"))
					files = false;
				else
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			} else if (arg.toLowerCase().equals("-nouser")) {
				options.add(new OptionUser("nobody"));
			} else if (arg.toLowerCase().equals("-incomplete")) {
				forceDirsOnly = true;
				options.add(new OptionIncomplete());
			} else if (arg.toLowerCase().equals("-nogroup")) {
				options.add(new OptionGroup("drftpd"));
			} else if (arg.toLowerCase().equals("-action")) {
				if (!iter.hasNext())
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				FindAction findAction = getAction(iter.next().toString()
						.toLowerCase());
				if (findAction == null)
					return FtpReply.RESPONSE_501_SYNTAX_ERROR;
				if (findAction instanceof ActionWipe) {
					if (!conn.getUserNull().isAdmin())
						return FtpReply.RESPONSE_530_ACCESS_DENIED;
				}
				actions.add(findAction);
			} else {
				return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			}
		}
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		//if (actions.size() == 0 || options.size() == 0)
		//return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		if (actions.size() == 0)
			actions.add(new ActionPrint());
		if (!dirs && !files) {
			dirs = true;
			files = true;
		}
		//FtpReply response = (FtpReply)
		// FtpReply.RESPONSE_200_COMMAND_OK.clone();
		if (forceFilesOnly && forceDirsOnly) {
			return new FtpReply(500,
					"Option conflict.  Possibly -slave and -incomplete.");
		} else if (forceFilesOnly) {
			dirs = false;
			response
					.addComment("Forcing a file only search because of -slave option.");
		} else if (forceDirsOnly) {
			files = false;
			response
					.addComment("Forcing a dir only search because of -incomplete option.");
		}
		options.add(new OptionType(files, dirs));
		findFile(conn, response, conn.getCurrentDirectory(), options, actions,
				files, dirs);
		return response;
	}
	public CommandHandler initialize(BaseFtpConnection conn,
			CommandManager initializer) {
		return this;
	}
	public String[] getFeatReplies() {
		return null;
	}
	private interface FindAction {
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file);
	}
	private interface FindOption {
		public boolean isTrueFor(LinkedRemoteFileInterface file);
	}
	private static class ActionDelete implements FindAction {
		private String doDELE(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			//FtpRequest request = conn.getRequest();
			// argument check
			//if (!request.hasArgument()) {
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			//return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			//}
			// get filenames
			String fileName = file.getName();
			LinkedRemoteFile requestedFile = (LinkedRemoteFile) file;
			//try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			//requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
			//} catch (FileNotFoundException ex) {
			//return new FtpReply(550, "File not found: " + ex.getMessage());
			//}
			// check permission
			if (requestedFile.getUsername().equals(
					conn.getUserNull().getUsername())) {
				if (!conn.getConfig().checkDeleteOwn(conn.getUserNull(),
						requestedFile)) {
					//return FtpReply.RESPONSE_530_ACCESS_DENIED;
					return "Access denied for " + file.getPath();
				}
			} else if (!conn.getConfig().checkDelete(conn.getUserNull(),
					requestedFile)) {
				//return FtpReply.RESPONSE_530_ACCESS_DENIED;
				return "Access denied for " + file.getPath();
			}
			//FtpReply reply = (FtpReply)
			// FtpReply.RESPONSE_250_ACTION_OKAY.clone();
			String reply = "Deleted " + requestedFile.getPath();
			User uploader;
			try {
				uploader = conn.getConnectionManager().getUserManager().getUserByName(
						requestedFile.getUsername());
				uploader
						.updateCredits((long) -(requestedFile.length() * uploader
								.getRatio()));
			} catch (UserFileException e) {
				reply += "Error removing credits: " + e.getMessage();
			} catch (NoSuchUserException e) {
				reply += "Error removing credits: " + e.getMessage();
			}
			//conn.getConnectionManager()
			//.dispatchFtpEvent(
			//new DirectoryFtpEvent(conn.getUserNull(), "DELE",
			//requestedFile));
			requestedFile.delete();
			return reply;
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
		 *      net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			return doDELE(conn, file);
		}
	}
	private static class ActionPrint implements FindAction {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.remotefile.LinkedRemoteFile)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			return file.getPath();
		}
	}
	private static class ActionWipe implements FindAction {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
		 *      net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			User user = conn.getUserNull();
			//conn.getConnectionManager().dispatchFtpEvent(
			//new DirectoryFtpEvent(user, "WIPE", file));
			file.delete();
			return "Wiped " + file.getPath();
		}
	}
	private static class OptionGroup implements FindOption {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.findOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFile)
		 */
		private String groupname;
		public OptionGroup(String g) {
			groupname = g;
		}
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (file.getGroupname().equals(groupname))
				return true;
			else
				return false;
		}
	}
	private static class OptionIncomplete implements FindOption {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			try {
				if (file.lookupSFVFile().getStatus().getMissing() > 0)
					return true;
				else
					return false;
			} catch (Exception e) {
				return false;
			}
			/*
			 * } catch(NoAvailableSlaveException e) { return false; }
			 * catch(IOException e) { return false; }
			 */
		}
	}
	private static class OptionMTime implements FindOption {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.findOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFile)
		 */
		private Date date;
		boolean after;
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
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			Date fileDate = new Date(file.lastModified());
			if (after)
				return fileDate.after(date);
			else
				return fileDate.before(date);
		}
	}
	private static class OptionName implements FindOption {
		Pattern pattern;
		public OptionName(String str) {
			pattern = Pattern.compile(str.replaceAll("[*]", ".*"));
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			Matcher m = pattern.matcher(file.getName());
			return m.matches();
		}
	}
	private static class OptionSize implements FindOption {
		boolean bigger;
		long size;
		public OptionSize(long s, boolean b) {
			bigger = b;
			size = s;
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (bigger)
				return file.length() >= size;
			else
				return file.length() <= size;
		}
	}
	private static class OptionSlave implements FindOption {
		RemoteSlave slave;
		public OptionSlave(RemoteSlave s) {
			slave = s;
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (file.hasSlave(slave))
				return true;
			else
				return false;
		}
	}
	private static class OptionType implements FindOption {
		boolean files;
		boolean dirs;
		public OptionType(boolean f, boolean d) {
			files = f;
			dirs = d;
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (files && dirs)
				return true;
			else if (files && !dirs)
				return file.isFile();
			else if (!files && dirs)
				return file.isDirectory();
			else
				return true;
		}
	}
	private static class OptionUser implements FindOption {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.findOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFile)
		 */
		private String username;
		public OptionUser(String u) {
			username = u;
		}
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (file.getUsername().equals(username))
				return true;
			else
				return false;
		}
	}
}