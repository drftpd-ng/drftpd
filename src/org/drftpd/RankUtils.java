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

package org.drftpd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;

import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * Set of usefull commnads to sort users/groups.
 * @author fr0w
 */
public class RankUtils {
	public static Collection<GroupPosition> topFileGroup(Collection files) {
		ArrayList<GroupPosition> ret = new ArrayList<GroupPosition>();
		
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String groupname = file.getGroupname();
			
			GroupPosition stat = null;
			
			for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
				GroupPosition stat2 = (GroupPosition) iter2.next();
				
				if (stat2.getGroupname().equals(groupname)) {
					stat = stat2;
					
					break;
				}
			}
			
			if (stat == null) {
				stat = new GroupPosition(groupname, file.length(), 1,
						file.getXfertime());
				ret.add(stat);
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
				stat.updateXfertime(file.getXfertime());
			}
		}
		
		Collections.sort(ret);
		
		return ret;
	}
	
	public static Collection userSort(Collection files, String type, String sort) {
		ArrayList<UploaderPosition> ret = new ArrayList<UploaderPosition>();
		
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
			UploaderPosition stat = null;
			
			for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
				UploaderPosition stat2 = (UploaderPosition) iter2.next();
				
				if (stat2.getUsername().equals(file.getUsername())) {
					stat = stat2;
					
					break;
				}
			}
			
			if (stat == null) {
				stat = new UploaderPosition(file.getUsername(), file.length(),
						1, file.getXfertime());
				ret.add(stat);
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
				stat.updateXfertime(file.getXfertime());
			}
		}
		
		Collections.sort(ret, new UserComparator(type, sort));
		
		return ret;
	}
}

class UserComparator implements Comparator {
	private String _sort;
	private String _type;
	
	public UserComparator(String type, String sort) {
		_type = type;
		_sort = sort;
	}
	
	static long getType(String type, UploaderPosition user) {
		if (type.equals("bytes")) {
			return user.getBytes();
		} else if (type.equals("xferspeed")) {
			return user.getXferspeed();
		} else if (type.equals("xfertime")) {
			return user.getXfertime();
		}
		
		return 0;
	}
	
	public int compare(Object o1, Object o2) {
		UploaderPosition u1 = (UploaderPosition) o1;
		UploaderPosition u2 = (UploaderPosition) o2;
		
		long thisVal = getType(_type, u1);
		long anotherVal = getType(_type, u2);
		
		if (_sort.equals("low")) {
			return ((thisVal < anotherVal) ? (-1)
					: ((thisVal == anotherVal) ? 0 : 1));
		}
		
		return ((thisVal > anotherVal) ? (-1) : ((thisVal == anotherVal) ? 0 : 1));
	}
}
