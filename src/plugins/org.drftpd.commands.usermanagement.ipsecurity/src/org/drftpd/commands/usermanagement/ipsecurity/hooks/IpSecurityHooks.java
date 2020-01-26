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

package org.drftpd.commands.usermanagement.ipsecurity.hooks;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.util.StringTokenizer;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.usermanagement.ipsecurity.IpSecurityManager;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.drftpd.util.HostMask;

/**
 * @author : CyBeR
 * @version : v1.0 
 */

public class IpSecurityHooks implements PreHookInterface {
	private static final Logger logger = LogManager.getLogger(IpSecurityHooks.class);
	
	/*
	 * Checks the IP from arguments (Used for ADDUSER/GADDUSER/ADDIP)
	 */
	public CommandRequest checkIP(CommandRequest request, int argnum, int ipnum, boolean newuser) {
		if (!request.hasArgument()) {
			return request;
		}

		String[] args = request.getArgument().split(" ");
		if (args.length < argnum) {
			return request;
		}
		
		try {
			int _numip = args.length - argnum + 1;

			User user = null;

			if (!newuser) {	
				user = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[0]);
				_numip = user.getHostMaskCollection().size();
			} 
		
			for (int i = ipnum; i < args.length; i++) {
				HostMask newMask = new HostMask(args[i].replace(",",""));
				String maskHostMask = newMask.getHostMask();

				boolean _allowed = IpSecurityManager.getIpSecurity().checkIP(newMask.getIdentMask(), maskHostMask, _numip,user);
				if ((!_allowed) && (!maskHostMask.equals("127.0.0.1"))) {
					request.setAllowed(false);
					CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
					response.addComment(IpSecurityManager.getIpSecurity().outputConfs(user));
					request.setDeniedResponse(response);
					return request;
				}
			}

		} catch (NoSuchUserException ex) {
			request.setAllowed(false);
			request.setDeniedResponse(new CommandResponse(452, "No such user: " + args[0]));
			logger.debug("No Such User Exception - IpSecurityHooks");
			return request;
		} catch (UserFileException ex) {
			request.setAllowed(false);
			request.setDeniedResponse(new CommandResponse(452,"User File Exception: " + args[0]));
			return request;
		}
		return request;
	}
	
	/*
	 * Prehook method for ADDIP
	 */
	public CommandRequestInterface doIpSecurityADDIPPreCheck(CommandRequest request) {
		return checkIP(request,2,1,false);
	}

	/*
	 * Prehook method for ADDUSER
	 */
	public CommandRequestInterface doIpSecurityADDUSERPreCheck(CommandRequest request) {
		return checkIP(request,3,2,true);
	}
	
	/*
	 * Prehook method for GADDUSER
	 */
	public CommandRequestInterface doIpSecurityGADDUSERPreCheck(CommandRequest request) {
		return checkIP(request,4,3,true);
	}

	/*
	 * Prehook method for SLAVE ADDIP
	 * Gets the ip, and preforms checks.
	 */
	public CommandRequestInterface doIpSecuritySLAVEPreCheck(CommandRequest request) {
		if (!request.hasArgument()) {
			return request;
		}

		String argument = request.getArgument();
		StringTokenizer arguments = new StringTokenizer(argument);

		if (!arguments.hasMoreTokens()) {
			return request;
		}
		
		String slavename = arguments.nextToken();
		
		RemoteSlave rslave = null;
		try {
			rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
		} catch (ObjectNotFoundException e) {
			request.setDeniedResponse(new CommandResponse(200,"Slave Not Found: " + slavename));
			request.setAllowed(false);
			return request;
		}
		
		if (arguments.hasMoreTokens()) {
			String command = arguments.nextToken();
			if (command.equalsIgnoreCase("addmask")) {
				if (arguments.countTokens() != 1) {
					return request;
				}
	
				HostMask newMask = new HostMask(arguments.nextToken().replace(",",""));
	
				String _maskident = newMask.getIdentMask();
				String _maskHostMask = newMask.getHostMask();
	
				boolean _allowed = IpSecurityManager.getIpSecurity().checkIP(_maskident, _maskHostMask, rslave.getMasks().size(),null);
				if ((!_allowed) && (!_maskHostMask.equals("127.0.0.1"))) {
					request.setAllowed(false);
					CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
					response.addComment(IpSecurityManager.getIpSecurity().outputConfs(null));
					request.setDeniedResponse(response);
					return request;
				}
			}
		}
		return request;
	}
	
	public void initialize(StandardCommandManager cManager) {
		
	}	
}
