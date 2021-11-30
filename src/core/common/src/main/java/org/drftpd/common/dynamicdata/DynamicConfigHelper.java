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
package org.drftpd.common.dynamicdata;

import org.drftpd.common.dynamicdata.element.*;

import java.util.Date;
import java.util.Map;

public class DynamicConfigHelper {

    private final Map<Key<?>, ConfigElement<?>> _configs;

    private DynamicConfigHelper(Map<Key<?>, ConfigElement<?>> configs) {
        _configs = configs;
    }

    public static DynamicConfigHelper configHelper(Map<Key<?>, ConfigElement<?>> configs) {
        return new DynamicConfigHelper(configs);
    }

    public Map<Key<?>, ConfigElement<?>> getData() {
        return _configs;
    }

    public <T> void setObject(Key<T> key, ConfigElement<T> data) {
        _configs.put(key, data);
    }
    public void setBool(Key<Boolean> key, boolean data) {
        _configs.put(key, new ConfigBoolean(data));
    }
    public void setLong(Key<Long> key, long data) {
        _configs.put(key, new ConfigLong(data));
    }
    public void setInt(Key<Integer> key, int data) {
        _configs.put(key, new ConfigInteger(data));
    }
    public void setFloat(Key<Float> key, float data) {
        _configs.put(key, new ConfigFloat(data));
    }
    public void setDate(Key<Date> key, Date data) {
        _configs.put(key, new ConfigDate(data));
    }
    public void setString(Key<String> key, String data) {
        _configs.put(key, new ConfigString(data));
    }

    public <T> T get(Key<T> key) throws KeyNotFoundException {
        @SuppressWarnings("unchecked")
        ConfigElement<T> data = (ConfigElement<T>)_configs.get(key);
        if (data == null) {
            throw new KeyNotFoundException(key.toString());
        }
        return data.getValue();
    }
    public <T> T get(Key<T> key, T defaultValue) {
        try {
            return get(key);
        } catch (KeyNotFoundException e) {
            return defaultValue;
        }
    }

    public <T> T remove(Key<T> key) {
        try {
            T o = get(key);
            _configs.remove(key);
            return o;
        } catch (KeyNotFoundException e) {
            return null;
        }
    }

    public void incrementLong(Key<Long> key, long inc) {
        Long data = 0L;
        try {
            data = get(key);
        } catch (KeyNotFoundException e) {
            // Nothing to do
        }
        setLong(key, data + inc);
    }
    public void incrementLong(Key<Long> key) {
        incrementLong(key, 1);
    }
    public void incrementInt(Key<Integer> key, int inc) {
        Integer data = 0;
        try {
            data = get(key);
        } catch (KeyNotFoundException e) {
            // Nothing to do
        }
        setInt(key, data + inc);
    }
    public void incrementInt(Key<Integer> key) {
        incrementInt(key, 1);
    }
}
