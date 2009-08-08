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

import com.google.common.collect.MapMaker;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Implements Weak referenced Map for javabeans support.
 * 
 * @author djb61
 * @version $Id$
 */
public class WeakKeyedMap<K extends Key<?>, V> implements ConcurrentMap<K,V>, KeyedMapInterface<K,V> {

	private ConcurrentMap<K,V> _map = new MapMaker().weakKeys().weakValues().makeMap();

	public WeakKeyedMap() {
		
	}

	public WeakKeyedMap(Map<? extends K,? extends V> map) {
		_map.putAll(map);
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

	public void setAllObjects(KeyedMapInterface<K, V> m) {
		putAll(m.getAllObjects());
	}

	public Map<K, V> getAllObjects() {
		return Collections.unmodifiableMap(this);
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

	@Override
	public V putIfAbsent(K key, V value) {
		return _map.putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return _map.remove(key, value);
	}

	@Override
	public V replace(K key, V value) {
		return _map.replace(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return _map.replace(key, oldValue, newValue);
	}

	@Override
	public void clear() {
		_map.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		return _map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return _map.containsValue(value);
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return _map.entrySet();
	}

	@Override
	public V get(Object key) {
		return _map.get(key);
	}

	@Override
	public boolean isEmpty() {
		return _map.isEmpty();
	}

	@Override
	public Set<K> keySet() {
		return _map.keySet();
	}

	@Override
	public V put(K key, V value) {
		return _map.put(key, value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		_map.putAll(m);
	}

	@Override
	public V remove(Object key) {
		return _map.remove(key);
	}

	@Override
	public int size() {
		return _map.size();
	}

	@Override
	public Collection<V> values() {
		return _map.values();
	}
}
