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
package org.drftpd.commands.usermanagement.expireduser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

import org.drftpd.master.Session;
import org.drftpd.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.usermanager.User;

/**
 * @author CyBeR
 */
public class ExpiredUser extends CommandInterface {

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
	}
	
    public CommandResponse doSITE_SETEXPIRE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

		Session session = request.getSession();

		User user = session.getUserNull(st.nextToken());
		if (user == null) {
			throw new ImproperUsageException();
		}
		
		if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }
		
		String date = st.nextToken("\n");
		
		
		try {
			Date myDate = new SimpleDateFormat("yyyy-MM-dd").parse(date);
			user.getKeyedMap().setObject(ExpiredUserData.EXPIRES, myDate);
			
		} catch (ParseException e) {
			throw new ImproperUsageException();
		}
		
		user.commit();
		return new CommandResponse(200, "Set Expiry Date For '" + user.getName() + "'");
    }
    
    public CommandResponse doSITE_REMOVEEXPIRE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

		Session session = request.getSession();

		User user = session.getUserNull(st.nextToken());
		if (user == null) {
			throw new ImproperUsageException();
		}

		user.getKeyedMap().remove(ExpiredUserData.EXPIRES);
		user.commit();
		return new CommandResponse(200, "Removed Expiry Date For '" + user.getName() + "'");
    }

}
