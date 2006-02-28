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

import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import org.apache.log4j.Logger;



/**
 * Implements Map for javabeans support.
 * @author mog
 * @version $Id$
 */
public class KeyedMap<K, V> extends Hashtable {
	private static final Logger logger = Logger.getLogger(KeyedMap.class);

	public KeyedMap() {
		super();
	}

	public Map<Key, Object> getAllObjects() {
		return Collections.unmodifiableMap(this);
	}

	public Object getObject(Key key) throws KeyNotFoundException {
        Object ret = get(key);
        if (ret == null) {
            throw new KeyNotFoundException();
        }
        return ret;
	}

    public Object getObject(Key key, Object def) {
        try {
            return getObject(key);
        } catch (KeyNotFoundException e) {
            return def;
        }
    }

	public boolean getObjectBoolean(Key key) {
		try {
			return ((Boolean)getObject(key)).booleanValue();
		} catch (KeyNotFoundException e) {
			return false;
		}
	}
	
	public Date getObjectDate(Key key) {
		return ((Date) getObject(key, new Date(System.currentTimeMillis())));
	}

    public float getObjectFloat(Key key) {
        return ((Float) getObject(key, new Float(0))).floatValue();
    }

    public int getObjectInt(Key key) {
        try {
            return ((Integer) getObject(key)).intValue();
        } catch (KeyNotFoundException e) {
            return 0;
        }
    }
    
    /**
     * If key is not found, returns 0
     */
    public long getObjectLong(Key key) {
        return ((Long) getObject(key, new Long(0))).longValue();
    }

    public String getObjectString(Key key) {
        return (String) getObject(key, "");
    }

    public void incrementObjectInt(Key key, int amount) {
        if (!key.getType().equals(Integer.class)) {
            throw new ClassCastException();
        }

        synchronized (this) {
            Integer i;

            try {
                i = (Integer) getObject(key);
            } catch (KeyNotFoundException e) {
                i = new Integer(0);
            }
            
            setObject(key, new Integer(i.intValue() + amount));
        }
    }

    public void incrementObjectLong(Key key) {
        incrementObjectInt(key, 1);
    }

	public void incrementObjectLong(Key key, long amount) {
		if (!key.getType().equals(Long.class)) {
            throw new ClassCastException();
        }

        synchronized (this) {
            Long i;

            try {
                i = (Long) getObject(key);
            } catch (KeyNotFoundException e) {
                i = new Long(0);
            }

            setObject(key, new Long(i.longValue() + amount));
        }
	}

	public void setAllObjects(KeyedMap m) {
		putAll(m.getAllObjects());
	}

	public void setObject(Key key, Object obj) {
        if (obj == null) {
            throw new NullPointerException(key + " - " + obj);
        }

        if (!key.getType().isInstance(obj)) {
            throw new ClassCastException(key + " - " + key.getType().getName() +
                    " - " + obj + " - " + obj.getClass().getName());
        }
        put(key, obj);
	}

	public void setObject(Key k, int v) {
		setObject(k, new Integer(v));
	}

	public void setObject(Key k, long v) {
		setObject(k, new Long(v));
	}
}
