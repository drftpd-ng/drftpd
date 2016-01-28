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
package org.drftpd.commands.usermanagement.notes;

import java.util.StringTokenizer;

import org.drftpd.master.Session;
import org.drftpd.commands.usermanagement.notes.metadata.NotesData;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.usermanager.User;

/**
 * @author CyBeR
 */
public class Notes extends CommandInterface {

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
	}

    public CommandResponse doSITE_ADDNOTE(CommandRequest request) throws ImproperUsageException {
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
		
		String note = st.nextToken("\n");
		
		NotesData notes = user.getKeyedMap().getObject(NotesData.NOTES, null);
		if (notes == null) {
			notes = new NotesData();
		}
		notes.addNote(note);
		user.getKeyedMap().setObject(NotesData.NOTES, notes);
		user.commit();
		return new CommandResponse(200, "Note Added");
    }
    
    public CommandResponse doSITE_DELNOTE(CommandRequest request) throws ImproperUsageException {
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

		int notenum;
		try {
			notenum = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e) {
			throw new ImproperUsageException();
		}

		if (notenum <= 0) {
			throw new ImproperUsageException();
		}
		
		NotesData notes = user.getKeyedMap().getObject(NotesData.NOTES, null);
		if (notes != null) {
			try {
				notes.delNote(notenum - 1);
			} catch (IndexOutOfBoundsException e) {
				return new CommandResponse(500, "Invalid note number, user has " + notes.getNotes().size() + " notes");
			}
			user.getKeyedMap().setObject(NotesData.NOTES, notes);
			user.commit();
			return new CommandResponse(200, "Note Removed");
		}
		return new CommandResponse(200, "No Notes For User");
    }

}
