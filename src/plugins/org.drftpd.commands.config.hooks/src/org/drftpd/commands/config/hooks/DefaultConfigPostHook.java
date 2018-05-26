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
package org.drftpd.commands.config.hooks;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.permissions.MessagePathPermission;

import java.util.Collection;

/**
 * PostHooks that implements some of the functionalities "required" by the directives in perms.conf
 * @author fr0w
 * @version $Id$
 */
public class DefaultConfigPostHook implements PostHookInterface {
			
	public void initialize(StandardCommandManager manager) {		
	}
	
	public void showMessageForPath(CommandRequest request, CommandResponse response) {
		Collection<MessagePathPermission> msgPath = GlobalContext.getConfig().getKeyedMap().getObject(DefaultConfigHandler.MSGPATH, null);

		if (msgPath == null || response.getCurrentDirectory() == null) {
			return;
		}
		
		for (MessagePathPermission perm : msgPath) {
			if (perm.checkPath(response.getCurrentDirectory())) {
				if (perm.check(request.getSession().getUserNull(request.getUser()))) {
					for (String line : perm.getMessage()) {
						response.addComment(line);
					}
				}
			}
		}		
	}

}
