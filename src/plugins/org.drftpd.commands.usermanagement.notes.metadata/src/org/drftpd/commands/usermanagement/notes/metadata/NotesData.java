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
package org.drftpd.commands.usermanagement.notes.metadata;

import org.drftpd.dynamicdata.Key;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author CyBeR
 */
@SuppressWarnings("serial")
public class NotesData implements Serializable {

	public static final Key<NotesData> NOTES = new Key<>(NotesData.class, "notes");

	private ArrayList<String> _notes;
	
	public ArrayList<String> getNotes() {
		return _notes;
	}

	public void setNotes(ArrayList<String> notes) {
		_notes = notes;
	}
	
	public void addNote(String note) {
		if (_notes == null) {
			_notes = new ArrayList<>();
		}
		_notes.add(note);
	}
	
	public void delNote(int note) throws IndexOutOfBoundsException {
		if (_notes != null) {
			_notes.remove(note);
		}
	}	
	
	public String toString() {
		if (_notes == null) {
			return "";
		}
		
		String output = "";
		int cnt = 1;
		for (String note : _notes) {
			output = output + "Note #" + cnt++ + " - " + note + "\n";
		}
		return output;
	}
} 
