/*
 * Created on Nov 9, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.drftpd.remotefile;

import java.util.Hashtable;
import java.util.Map;


public class CaseInsensitiveHashtable extends Hashtable {
    public CaseInsensitiveHashtable() {
        super();
    }

    public CaseInsensitiveHashtable(int initialCapacity) {
        super(initialCapacity);
    }

    public CaseInsensitiveHashtable(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public CaseInsensitiveHashtable(Map t) {
        super(t);
    }

    public synchronized boolean containsKey(Object key) {
        return super.containsKey(((String) key).toLowerCase());
    }

    public synchronized Object get(Object key) {
        return super.get(((String) key).toLowerCase());
    }

    public synchronized Object put(Object key, Object value) {
        return super.put(((String) key).toLowerCase(), value);
    }

    public synchronized Object remove(Object key) {
        return super.remove(((String) key).toLowerCase());
    }
}