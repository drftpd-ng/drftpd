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
package org.drftpd.commands.newhandler;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @version $Id$
 * @author zubov
 * @author fr0w
 */
public class New extends CommandInterface {
	private static final Logger logger = Logger.getLogger(New.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doNEW(CommandRequest request) throws ImproperUsageException {
		int defaultCount = Integer.parseInt(request.getProperties().getProperty("default", "5"));;
		int maxCount = Integer.parseInt(request.getProperties().getProperty("max", "25"));
		String sectionFilter = request.getProperties().getProperty("filtered_sections", "").trim();;
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		SectionManagerInterface sectionManager = GlobalContext.getGlobalContext().getSectionManager();
		HashMap<String, SectionInterface> sections = new HashMap<String, SectionInterface>();

		// site new
		// site new <number>
		// site new <section> <number>
		SectionInterface specificSection = null;
		int count = defaultCount;
		boolean allSections = false;

		if (request.hasArgument()) {
			StringTokenizer st = new StringTokenizer(request.getArgument());
			if (st.countTokens() > 2)
				throw new ImproperUsageException(); // invalid number of arguments.

			while (st.hasMoreTokens()) {
				String parm = st.nextToken();

				if (isInteger(parm)) { // found a number.
					count = Integer.parseInt(parm);

					if (count > maxCount)
						count = maxCount;

					if (specificSection == null) { // not section specified, adding all.
						sections.putAll(sectionManager.getSectionsMap());
						allSections = true;
					}

					break; // nothing else to do.
				} else {
					specificSection = sectionManager.getSection(parm);
					if (specificSection.getName().equals("")) // not a valid section.
						return new CommandResponse(501, "Invalid/Non-Existant section"); 
					else // valid section.
						sections.put(specificSection.getName(), specificSection);
				}
			}
		} else {
			// no param found. setting default values.
			sections.putAll(sectionManager.getSectionsMap());
			allSections = true;
		}

		if (allSections) {
			for (String s : sectionFilter.split(" ")) {
				sections.remove(s);
			}
		}

		User user = request.getSession().getUserNull(request.getUser());

		// Collect all dirs from all sections
		ArrayList<DirectoryHandle> directories = new ArrayList<DirectoryHandle>();
		for (SectionInterface section : sections.values()) {
			try {
				directories.addAll(section.getCurrentDirectory().getDirectories(user));
			} catch (FileNotFoundException e) {
				logger.error("The directory was just there! How come it's gone?", e);
			}
		}

		ReplacerEnvironment env = new ReplacerEnvironment();
		if (directories.size() == 0) {
			response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"new.empty", env, request.getUser()));
		} else {
			Collections.sort(directories, new DateComparator());

			response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"header", env, request.getUser()));

			// Print the reply! 
			int pos = 1;

			for (Iterator<DirectoryHandle> iter = directories.iterator(); iter.hasNext() && (pos <= count); pos++) {
				try {
					DirectoryHandle dir = iter.next();
					env.add("pos", "" + pos);
					env.add("name", allSections ? dir.getPath() : dir.getName());
					env.add("diruser", dir.getUsername());
					env.add("files", "" + dir.getInodeHandles(user).size());
					env.add("size", Bytes.formatBytes(dir.getSize()));
					env.add("age", Time.formatTime(System.currentTimeMillis() - dir.lastModified()));
					response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"new", env, request.getUser()));
				} catch (FileNotFoundException e) {
					logger.error("The directory was just there! How come it's gone?", e);
				}
			}
			response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"footer", env, request.getUser()));
		}

		return response;
	}

	private static class DateComparator implements Comparator<DirectoryHandle> {
		public int compare(DirectoryHandle d1, DirectoryHandle d2) {
			long lastModified1 = 0;
			long lastModified2 = 0;

			try {
				lastModified1 = d1.lastModified();
				lastModified2 = d2.lastModified();
			} catch (FileNotFoundException e) {
				logger.error("The directory was just there! How come it's gone?", e);
			}

			if (lastModified1 == lastModified2) {
				return 0;
			}

			return (lastModified1 > lastModified2) ? (-1) : 1;
		}
	}

	private static boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
