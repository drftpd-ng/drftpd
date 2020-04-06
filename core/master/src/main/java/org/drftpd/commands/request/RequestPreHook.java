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
package org.drftpd.commands.request;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.request.metadata.RequestUserData;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commandmanager.*;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;
import java.util.Properties;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestPreHook {
	private int _weekMax;
    private Permission _weekExempt;

	public void RequestPreHook() {
		readConfig();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	@CommandHook(commands = "doSITE_REQUEST", priority = 10, type = HookType.PRE)
	public CommandRequestInterface doWklyAllotmentPreCheck(CommandRequest request) {
		User user = request.getSession().getUserNull(request.getUser());
		if (user != null) {
			int weekReqs = user.getKeyedMap().getObjectInteger(RequestUserData.WEEKREQS);
			if (_weekMax != 0 && weekReqs >= _weekMax && !_weekExempt.check(user)) {
				// User is not exempted and max number of request this week is made already
				request.setAllowed(false);
				request.setDeniedResponse(new CommandResponse(530, "Access denied - " + "You have reached max(" + _weekMax + ") number of requests per week"));
			}
			return request;
		} 
		request.setAllowed(false);
		request.setDeniedResponse(new CommandResponse(530, "Access denied - No Such User"));
		return request;		
	}

	/**
	 * Reads 'conf/plugins/request.conf'
	 */
	private void readConfig() {
		Properties props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("request");
		_weekMax = Integer.parseInt(props.getProperty("request.weekmax", "0"));
		_weekExempt = new Permission(props.getProperty("request.weekexempt", ""));
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		readConfig();
	}
}
