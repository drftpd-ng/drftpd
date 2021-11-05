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

import java.io.Serializable;

/**
 * @author mog
 * @version $Id$
 */
@SuppressWarnings("serial")
public class Key<T> implements Serializable {
    private String _key;

    public Key(Class<?> owner, String key) {
        assert owner != null;
        assert key != null;
        _key =  owner.getName() + '@' + key;
    }

    public Key(String key) {
        this._key = key;
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (!(o instanceof Key)) {
            return false;
        }

        Key<T> ok = (Key<T>) o;

        return ok.getKey().equals(getKey());
    }

    public String getKey() {
        return _key;
    }

    public void setKey(String _key) {
        this._key = _key;
    }

    public String toString() {
        return _key;
    }

    public int hashCode() {
        return _key.hashCode();
    }
}
