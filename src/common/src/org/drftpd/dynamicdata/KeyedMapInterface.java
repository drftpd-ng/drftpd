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

import java.util.Map;

/**
 * Define methods all concrete KeyedMap implementations must provide
 * 
 * @author djb61
 * @version $Id$
 */
public interface KeyedMapInterface<K extends Key<?>, V> {

	public <T> T getObject(Key<T> key) throws KeyNotFoundException;

	public <T> T getObject(Key<T> key, T def);

	public void setAllObjects(KeyedMapInterface<K, V> m);

	public Map<K, V> getAllObjects();

	public <T> void setObject(Key<T> key, T obj);

	public Integer getObjectInteger(Key<Integer> key);

	public Long getObjectLong(Key<Long> key);

	public Float getObjectFloat(Key<Float> key);

	public Boolean getObjectBoolean(Key<Boolean> key);

	public String getObjectString(Key<String> key);

	public void incrementInt(Key<Integer> key);

	public void incrementInt(Key<Integer> key, int amount);

	public void incrementLong(Key<Long> key);

	public void incrementLong(Key<Long> key, long amount);
}
