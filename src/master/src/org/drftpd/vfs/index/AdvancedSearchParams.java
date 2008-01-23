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
package org.drftpd.vfs.index;

import java.util.Collections;
import java.util.Set;

/**
 * @author fr0w
 * @version $Id$
 */
public class AdvancedSearchParams {
	
	public enum InodeType {
		DIRECTORY, FILE, ANY
	}

	private InodeType _inodeType = InodeType.ANY;
	private Set<String> _slaves = Collections.emptySet();
	private String _user = "*";
	private String _group = "*";

	public AdvancedSearchParams() {
		
	}
	
	public void setInodeType(InodeType type) {
		_inodeType = type;
	}
	
	public void setSlaves(Set<String> slaves) {
		_slaves = slaves;
	}
	
	public void setOwner(String user) {
		_user = user;
	}
	
	public void setGroup(String group) {
		_group = group;
	}
	
	public InodeType getInodeType() {
		return _inodeType;
	}
	
	public Set<String> getSlaves() {
		return _slaves;
	}
	
	public String getOwner() {
		return _user;
	}
	
	public String getGroup() {
		return _group;
	}
}
