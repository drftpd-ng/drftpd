/*
 * Created on Sep 6, 2004
 */
package org.drftpd.plugins;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.master.ConnectionManager;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author Teflon
 */
public class TDPAffilManagement implements CommandHandler, CommandHandlerFactory {
	private static final Logger logger = Logger
			.getLogger(TDPAffilManagement.class);

	String _affilsRoot;
	HashSet<String> _affils = new HashSet<String>();

	HashMap<String, String> _allowedGroups;

	ConnectionManager _cm;

	//boolean _loadSuccess = false;

	//HashMap<String, String> _template;

	public TDPAffilManagement() throws FileNotFoundException {
		super();
		readPerms();
	}

	/**
	 * @throws FileNotFoundException
	 * 
	 */
	private void readPerms() throws FileNotFoundException {
		XMLDecoder in = new XMLDecoder(new FileInputStream("affils.xml"));
		_affils =  (HashSet<String>) in.readObject();
	}

	public Reply doSITE_ADDAFFIL(BaseFtpConnection conn) throws ReplyException {
		if (!conn.getRequest().hasArgument()) {
			return new Reply(202, "Usage: site addaffil <group>");
		}

		StringTokenizer st = new StringTokenizer(conn.getRequest()
				.getArgument(), " ");
		if (!st.hasMoreTokens()) {
			return Reply.RESPONSE_501_SYNTAX_ERROR;
		}

		String group = st.nextToken();
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("group", group);
		env.add("affilsroot", _affilsRoot);

		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
//		ArrayList<String> permsData = readPerms();

//		for (Iterator iter = permsData.iterator(); iter.hasNext();) {
//			if (((String) iter.next()).startsWith(AM_START + group)) {
//				return new Reply(550, "Requested action not taken. " + "Affil "
//						+ group + " already exists in perms.conf.");
//			}
//		}
//
//		permsData.add("");
//		permsData.add(AM_START + group);
		_affils.add(group);
//		for (Iterator iter = _template.keySet().iterator(); iter.hasNext();) {
//			String perm = (String) iter.next();
//			String template = (String) _template.get(perm);
//			if (template.equals(""))
//				continue;
//
//			try {
//				template = SimplePrintf.jprintf(template, env);
//			} catch (FormatterException e) {
//				response.addComment(e.getMessage());
//				return response;
//			}
//			permsData.add(paddingString(perm, 11, ' ', false) + " " + template);
//		}
//		permsData.add(AM_END + group);
//		writePerms(permsData);
		try {
			writePerms();
		} catch(IOException e) {
			throw new ReplyException(e);
		}
		response.addComment("Added " + group + " to perms.conf");

		try {
			LinkedRemoteFile aRoot = conn.getGlobalContext().getRoot()
					.lookupFile(_affilsRoot);
			aRoot.createDirectory(conn.getUserNull().getName(), conn
					.getUserNull().getGroup(), group);
			response.addComment("Created group dir " + _affilsRoot + "/"
					+ group);
		} catch (FileNotFoundException e1) {
			String err = "Affils root dir (" + _affilsRoot
					+ ") was not found on site.";
			logger.warn(err, e1);
			response.addComment(err);
		} catch (FileExistsException e) {
			logger.warn("Couldn't create affil pre dir for " + group
					+ ". It already exists.");
			response.addComment("Group directory " + _affilsRoot + "/" + group
					+ " already exists.");
		}
//
//		response.addComment("Executing SITE RELOAD");
		// String ret = doSITE_RELOAD(conn);
		// if (!ret.equals(""))
		// response.addComment(ret);

		return response;
	}

	private void writePerms() throws IOException {
		XMLEncoder out = new XMLEncoder(new SafeFileOutputStream("affils.xml"));
		out.writeObject(_affils);
		out.close();
	}

	public Reply doSITE_AFFILS(BaseFtpConnection conn) {
		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
//		ArrayList permsData = readPerms();
//		for (Iterator iter = permsData.iterator(); iter.hasNext();) {
		for(String affil : _affils) {
//			String line = (String) iter.next();
//			if (line.startsWith("#TDPAffilManagement")) {
//				String amLine[] = line.split(" ");
//				if (amLine.length < 3) {
//					logger.info("Ignoring Invalid TDPAM line: " + line);
//					continue;
//				}
//
//				if (amLine[1].equals("START")) {
//					String group = amLine[2];
//					// logger.info("Processing configuration for group " +
//					// group);
//					do {
//						line = (String) iter.next();
//						if (line.startsWith(AM_END + group)) {
			response.addComment("Found " + affil);
//						}
//					} while (!line.startsWith("#TDPAffilManagement")
//							&& iter.hasNext());
//
//				}
//			}
//
		}
		return response;
	}

	public Reply doSITE_DELAFFIL(BaseFtpConnection conn) {
		if (!conn.getRequest().hasArgument()) {
			return new Reply(202, "Usage: site delaffil <group> [-delusers]");
		}

		StringTokenizer st = new StringTokenizer(conn.getRequest()
				.getArgument(), " ");
		if (!st.hasMoreTokens()) {
			return Reply.RESPONSE_501_SYNTAX_ERROR;
		}
		String group = st.nextToken();
		boolean delusers = false;
		if (st.hasMoreTokens() && st.nextToken().equals("-delusers")) {
			delusers = true;
		}

		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
		//HashSet<String> permsData = readPerms();
		//permsData.add(group);
		if(_affils.remove(group)) {
			response.addComment("Deleted " + group);
		} else {
			response.addComment(group+" not found");
		}
		//writePerms(permsData);

		try {
			LinkedRemoteFile aRoot = conn.getGlobalContext().getRoot()
					.lookupFile(_affilsRoot + "/" + group);
			aRoot.delete();
			response.addComment("Deleted group dir " + _affilsRoot + "/"
					+ group);
		} catch (FileNotFoundException e) {
			response.addComment("Could not delete group dir " + _affilsRoot
					+ "/" + group);
		}

		if (delusers) {
			try {
				Collection gusers = conn.getGlobalContext().getUserManager()
						.getAllUsersByGroup(group);
				for (Iterator iter = gusers.iterator(); iter.hasNext();) {
					User user = (User) iter.next();
					user.setDeleted(true);
					user.commit();
					response.addComment("Deleted user " + user.getName());
				}
			} catch (UserFileException e1) {
				response.addComment("Error retrieving users in group " + group);
			}
		} else {
			response.addComment("No users were deleted");
		}

		response.addComment("Reloading perms.conf");
		// String ret = doSITE_RELOAD(conn);
		// if (!ret.equals(""))
		// response.addComment(ret);

		return response;
	}

	public Reply execute(BaseFtpConnection conn)
			throws ReplyException {

		if (!isAllowed(conn))
			return Reply.RESPONSE_530_ACCESS_DENIED;

		String cmd = conn.getRequest().getCommand();
		if ("SITE AFFILS".equals(cmd))
			return doSITE_AFFILS(conn);
		if ("SITE ADDAFFIL".equals(cmd))
			return doSITE_ADDAFFIL(conn);
		if ("SITE DELAFFIL".equals(cmd))
			return doSITE_DELAFFIL(conn);
		throw UnhandledCommandException.create(TDPAffilManagement.class, conn
				.getRequest());
	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(BaseFtpConnection conn,
			CommandManager initializer) {
		return this;
	}

	public boolean isAllowed(BaseFtpConnection conn) {
		String cmd = conn.getRequest().getCommand().substring("SITE ".length())
				.toLowerCase();
		String groups[] = ((String) _allowedGroups.get(cmd + ".allow"))
				.split(" ");
		for (int i = 0; i < groups.length; i++) {
			if (conn.getUserNull().isMemberOf(groups[i]))
				return true;
		}
		return false;
	}

	public void load(CommandManagerFactory initializer) {
		loadSettings();
	}

	public boolean loadSettings() {
		//_template = new HashMap<String, String>();
		_allowedGroups = new HashMap<String, String>();
		ResourceBundle bundle = ResourceBundle
				.getBundle(TDPAffilManagement.class.getName());
		try {
			_allowedGroups
					.put("affils.allow", bundle.getString("affils.allow"));
			_allowedGroups.put("addaffil.allow", bundle
					.getString("addaffil.allow"));
			_allowedGroups.put("delaffil.allow", bundle
					.getString("delaffil.allow"));

			_affilsRoot = bundle.getString("affilsroot");

//			_template.put("pre", bundle.getString("template.pre"));
//			_template.put("privpath", bundle.getString("template.privpath"));
//			_template.put("makedir", bundle.getString("template.makedir"));
//			_template.put("delete", bundle.getString("template.delete"));
//			_template.put("rename", bundle.getString("template.rename"));
//			_template
//					.put("creditloss", bundle.getString("template.creditloss"));
		} catch (MissingResourceException e) {
			logger.warn(
					"Error reading config in TDPAffilManagement.properties", e);
			logger.warn("TDPAffilManagement will be disabled");
			return false;
		}

		logger.info("TDPAffilManagement plugin loaded successfully");
		return true;
	}

	public synchronized String paddingString(String s, int n, char c,
			boolean paddingLeft) {
		StringBuffer str = new StringBuffer(s);
		int strLength = str.length();
		if (n > 0 && n > strLength) {
			for (int i = 0; i <= n; i++) {
				if (paddingLeft) {
					if (i < n - strLength)
						str.insert(0, c);
				} else {
					if (i > strLength)
						str.append(c);
				}
			}
		}
		return str.toString();
	}

//	/**
//	 * 
//	 */
//	public ArrayList<String> readPerms() {
//		ArrayList<String> permsData = new ArrayList<String>();
//		try {
//			BufferedReader br = new BufferedReader(new InputStreamReader(
//					new FileInputStream("conf/perms.conf")));
//			String line = "";
//			while ((line = br.readLine()) != null) {
//				permsData.add(line);
//			}
//			br.close();
//		} catch (FileNotFoundException e) {
//			logger.warn("Error reading perms.conf file", e);
//			return null;
//		} catch (IOException e) {
//			logger.warn("Error reading perms.conf file", e);
//			return null;
//		}
//		return permsData;
//	}

	public void unload() {
	}

//	private void writePerms(ArrayList permsData) {
//		logger.info("Saving perms.conf file");
//		try {
//			PrintWriter out = new PrintWriter(new SafeFileWriter(
//					"conf/perms.conf"));
//			for (Iterator iter = permsData.iterator(); iter.hasNext();) {
//				String line = (String) iter.next();
//				out.println(line);
//			}
//			out.close();
//		} catch (IOException e) {
//			logger.warn("Error saving perms.conf", e);
//		}
//	}

}
