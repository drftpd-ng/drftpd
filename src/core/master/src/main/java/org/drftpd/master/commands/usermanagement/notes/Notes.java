/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.commands.usermanagement.notes;

import org.drftpd.master.commands.*;
import org.drftpd.master.commands.usermanagement.notes.metadata.NotesData;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.User;

import java.util.StringTokenizer;

/**
 * @author CyBeR
 */
public class Notes extends CommandInterface {

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
    }

    public CommandResponse doADDNOTE(CommandRequest request) throws ImproperUsageException {
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

    public CommandResponse doDELNOTE(CommandRequest request) throws ImproperUsageException {
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
