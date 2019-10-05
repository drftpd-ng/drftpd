/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.commands.usermanagement.securepass.hooks;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.usermanagement.securepass.SecurePassManager;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author : CyBeR
 * @version : v1.0 
 */

public class SecurePassHooks implements PreHookInterface {
	private static final Logger logger = LogManager.getLogger(SecurePassHooks.class);
	
	/*
	 * Checks the IP from arguments (Used for ADDUSER/GADDUSER/ADDIP)
	 */
	public CommandRequest checkPASS(CommandRequest request, int usernum, int passnum, boolean newuser) {
		if (!request.hasArgument()) {
			return request;
		}

		String[] args = request.getArgument().split(" ");
		if (args.length < passnum) {
			return request;
		}
		
		try {
			String password = args[passnum-1];
			User user = null;
			if (!newuser) {
				if (usernum == 0) {
					user = GlobalContext.getGlobalContext().getUserManager().getUserByName(request.getUser());
				} else {
					user = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[usernum-1]);
				}
			}

			if (!SecurePassManager.getSecurePass().checkPASS(password,user)) {
				request.setAllowed(false);
				CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
				response.addComment(SecurePassManager.getSecurePass().outputConfs(user));
				request.setDeniedResponse(response);
				return request;
			}
		} catch (NoSuchUserException ex) {
			request.setAllowed(false);
			request.setDeniedResponse(new CommandResponse(452, "No such user: " + args[0]));
			logger.debug("No Such User Exception - SecurePassHooks");
			return request;
		} catch (UserFileException ex) {
			request.setAllowed(false);
			request.setDeniedResponse(new CommandResponse(452,"User File Exception: " + args[0]));
			return request;
		}
		return request;
	}
	
	/*
	 * Prehook method for CHPASS
	 */
	public CommandRequestInterface doSecurePassCHPASSPreCheck(CommandRequest request) {
		return checkPASS(request,1,2,false);
	}

	/*
	 * Prehook method for PASSWD
	 */
	public CommandRequestInterface doSecurePassPASSWDPreCheck(CommandRequest request) {
		return checkPASS(request,0,1,false);
	}	
	
	/*
	 * Prehook method for ADDUSER
	 */
	public CommandRequestInterface doSecurePassADDUSERPreCheck(CommandRequest request) {
		return checkPASS(request,1,2,true);
	}
	
	/*
	 * Prehook method for GADDUSER
	 */
	public CommandRequestInterface doSecurePassGADDUSERPreCheck(CommandRequest request) {
		return checkPASS(request,2,3,true);
	}

	@Override
	public void initialize(StandardCommandManager cManager) {
		
	}	
}