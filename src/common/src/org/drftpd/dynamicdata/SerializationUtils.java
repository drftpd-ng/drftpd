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
package org.drftpd.dynamicdata;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map.Entry;
import java.util.WeakHashMap;

/**
 * A collection of helper methods to aid with the serialization of KeyedMap instances. The
 * use of these methods allows WeakKeyedMaps to be used during runtime and a strongly
 * referenced KeyedMap to be written when the data is serialised. The class maintains an
 * internal fully weakly referenced cache to avoid unnecessary reflection lookups when
 * constructing WeakKeyedMaps.
 * 
 * @author djb61
 * @version $Id$
 */
public class SerializationUtils {

	private static WeakHashMap<Key<?>,WeakReference<Key<?>>> _keyCache =
		new WeakHashMap<Key<?>,WeakReference<Key<?>>>();

	/**
	 * Constructs a WeakKeyedMap containing the same entries as the passed KeyedMap. The <tt>Key</tt>
	 * objects used as keys in the map are replaced by the instances statically referenced in the
	 * <tt>Key</tt> owning class, this prevents immediate garbage collection of entries in the
	 * WeakKeyedMap. If a statically referenced instance for a key cannot be found in the owning
	 * class then the entry for that key will be absent in the returned WeakKeyedMap.
	 *  
	 * @param  sourceMap
	 *         The KeyedMap instance to copy data from
	 * 
	 * @return  A WeakKeyedMap instance containing the same mappings as the sourceMap
	 */
	public static WeakKeyedMap<Key<?>,Object> populateWeakKeyedMap(KeyedMap<Key<?>,Object> sourceMap) {
		WeakKeyedMap<Key<?>,Object> weakMap = new WeakKeyedMap<Key<?>,Object>();
		synchronized(sourceMap) {
			for (Entry<Key<?>,Object> sourceEntry : sourceMap.entrySet()) {
				try {
					weakMap.put(findStaticReferencedKey(sourceEntry.getKey()), sourceEntry.getValue());
				} catch (KeyNotFoundException e) {
					// Key doesn't exist, this entry will be removed from the returned map
				}
			}
		}
		return weakMap;
	}

	/**
	 * Constructs a KeyedMap containing the same entries as the passed WeakKeyedMap.
	 * 
	 * @param  sourceMap
	 *         The WeakKeyedMap to copy data from
	 * 
	 * @return  A KeyedMap instance containing the same mappings as the sourceMap
	 */
	public static KeyedMap<Key<?>,Object> populateKeyedMap(WeakKeyedMap<Key<?>,Object> sourceMap) {
		return new KeyedMap<Key<?>,Object>(sourceMap);
	}

	/**
	 * Retrieves a statically referenced Key instance equal to the passed Key instance. If this
	 * Key has been retrieved before then the instance will be returned from the cache, if not
	 * then it will be retrieved using reflection from the owning class of the Key and added to
	 * the cache prior to returning.
	 *  
	 * @param  inputKey
	 *         The <tt>Key</tt> instance to find a statically referenced equivalent to
	 * 
	 * @return  A statically referenced <tt>Key</tt> equivalent to inputKey
	 * 
	 * @throws  KeyNotFoundException
	 *          If the requested key is not cached and cannot be found as a static reference in
	 *          the owning class
	 */
	private static Key<?> findStaticReferencedKey(Key<?> inputKey) throws KeyNotFoundException {
		Key<?> staticKey = null;
		WeakReference<Key<?>> cachedRef = _keyCache.get(inputKey);
		if (cachedRef != null) {
			staticKey = cachedRef.get();
		}
		if (staticKey == null) {
			Class<?> keyOwner = inputKey.getOwner();
			for (Field ownerField : keyOwner.getDeclaredFields()) {
				if (ownerField.getType() == Key.class && Modifier.isPublic(ownerField.getModifiers())
						&& Modifier.isStatic(ownerField.getModifiers()) && Modifier.isFinal(ownerField.getModifiers())) {
					try {
						Key<?> testKey = (Key<?>)ownerField.get(keyOwner);
						if (testKey.equals(inputKey)) {
							staticKey = testKey;
							_keyCache.put(testKey, new WeakReference<Key<?>>(testKey));
							break;
						}
					} catch (IllegalAccessException e) {
						// shouldn't happen as we've already checked it is a public field
					}
				}
			}
		}
		if (staticKey == null) {
			throw new KeyNotFoundException();
		}
		return staticKey;
	}
}
