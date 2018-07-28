/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.dynamicdata;

import java.util.Hashtable;
import java.util.Map;

/**
 * Implements Map for javabeans support.
 * 
 * @author mog
 * @version $Id$
 */
@SuppressWarnings("serial")
public class KeyedMap<K extends Key<?>, V> extends Hashtable<K, V> {
	public KeyedMap() {
		super();
	} 

	public KeyedMap(Map<? extends K,? extends V> map) {
		super(map);
	}

	@SuppressWarnings("unchecked")
	public <T> T getObject(Key<T> key) throws KeyNotFoundException {
		T ret = (T) get(key);
		
		if (ret == null) {
			throw new KeyNotFoundException();
		}
		return ret;
	}

	public <T> T getObject(Key<T> key, T def) {
		try {
			return getObject(key);
		} catch (KeyNotFoundException e) {
			return def;
		}
	}

	public void setAllObjects(KeyedMap<K, V> m) {
		putAll(m.getAllObjects());
	}

	public Map<K, V> getAllObjects() {
		return new KeyedMap<>(this);
	}

	@SuppressWarnings("unchecked")
	public <T> void setObject(Key<T> key, T obj) {
        if (obj == null) {
            throw new NullPointerException(key + " - is null");
        }

		put((K) key, (V) obj);
	}
	
	public Integer getObjectInteger(Key<Integer> key) {
		return getObject(key, 0);
	}
	
	public Long getObjectLong(Key<Long> key) {
		return getObject(key, 0L);
	}
	
	public Float getObjectFloat(Key<Float> key) {
		return getObject(key, 0F);
	}
	
	public Boolean getObjectBoolean(Key<Boolean> key) {
		return getObject(key, false);
	}
	
	public String getObjectString(Key<String> key) {
		return getObject(key, "");
	}
	
	public void incrementInt(Key<Integer> key) {
		incrementInt(key, 1);
	}
	
	public void incrementInt(Key<Integer> key, int amount) {
		synchronized (this) {
			Integer i = getObject(key, 0);

			setObject(key, i + amount);
		}
	}

	public void incrementLong(Key<Long> key) {
		incrementLong(key, 1L);
	}

	public void incrementLong(Key<Long> key, long amount) {
		synchronized (this) {
			Long l = getObject(key, 0L);

			setObject(key, l + amount);
		}
	}
}
