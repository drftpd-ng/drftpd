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
package org.drftpd.misc;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zubov
 * @version $Id$ Must be CaseInsensitiveHashMap <String,V>
 */
@SuppressWarnings("serial")
public class CaseInsensitiveHashMap<K, V> extends HashMap<K, V> {

	public CaseInsensitiveHashMap() {
		super();
	}

	public CaseInsensitiveHashMap(Map<K,V> map) {
		super(map);
	}

	public boolean containsKey(String arg0) {
		return super.containsKey(arg0.toLowerCase());
	}

	public V get(String arg0) {
		return super.get(arg0.toLowerCase());
	}

	@SuppressWarnings("unchecked")
	public V put(K arg0, V arg1) {
		return super.put(((K) ((String) arg0).toLowerCase()), arg1);
	}
}
