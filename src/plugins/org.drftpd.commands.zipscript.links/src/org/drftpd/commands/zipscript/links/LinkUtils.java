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
package org.drftpd.commands.zipscript.links;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class LinkUtils {

	private static final Logger logger = Logger.getLogger(LinkUtils.class);

	public static void processLink(CommandRequest request, String mode, ResourceBundle bundle) {
		DirectoryHandle dir = request.getCurrentDirectory();
		String keyPrefix = LinkUtils.class.getName();
		boolean knownDir = false;
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf");
		// Check if incomplete links are enabled
		if (!cfg.getProperty("incomplete.links", "false").equals("true")) {
			return;
		}
		String knownDirs = cfg.getProperty("incomplete.useparent");
		StringTokenizer st = new StringTokenizer(knownDirs);
		knownDir = Pattern.compile(st.nextToken(),Pattern.CASE_INSENSITIVE)
			.matcher(dir.getName()).matches();
		if (st.hasMoreTokens()) {
			logger.warn("Invalid incomplete.useparent in zipscript.conf, ignoring");
			knownDir=false;
		}
		DirectoryHandle targetDir;
		String linkName;
		ReplacerEnvironment env = new ReplacerEnvironment();
		if (knownDir) {
			targetDir = dir.getParent().getParent();
			env.add("directory", dir.getParent().getName());
			env.add("subdir", dir.getName());
			linkName = request.getSession().jprintf(bundle,
						keyPrefix+".incomplete.subdirlink", env, request.getUser());
		}
		else {
			targetDir = dir.getParent();
			env.add("directory", dir.getName());
			linkName = request.getSession().jprintf(bundle,
						keyPrefix+".incomplete.link", env, request.getUser());
		}
		if (mode.equals("create")) {
			try {
				targetDir.createLinkUnchecked(linkName,dir.getPath(),request.getSession().getUserNull(request.getUser())
						.getName(),request.getSession().getUserNull(request.getUser()).getGroup());
			} catch (FileExistsException e) {
				// An inode with the links path already exists
			} catch (FileNotFoundException e) {
				// Link creation failed for some reason
			}
		}
		else if (mode.equals("delete")) {
			try {
				InodeHandle link = targetDir.getInodeHandleUnchecked(linkName);
				if (link.isLink()) {
					link.deleteUnchecked();
				}
			} catch (FileNotFoundException e) {
				// Link is already gone
			}
		}
	}
}
