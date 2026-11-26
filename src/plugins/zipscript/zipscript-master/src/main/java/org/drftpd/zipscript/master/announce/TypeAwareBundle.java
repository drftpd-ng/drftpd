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
package org.drftpd.zipscript.master.announce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ResourceBundle;

/**
 * Wraps an announce theme bundle: while a type chain is set for the current thread, a lookup of
 * <code>key</code> first tries <code>key.&lt;type&gt;</code> for each type (most specific first)
 * and falls back to the plain <code>key</code>. With no chain set (the default) it behaves exactly
 * like the wrapped bundle, so themes that define no per-type keys are unaffected.
 */
public class TypeAwareBundle extends ResourceBundle {

    private static final Logger logger = LoggerFactory.getLogger(TypeAwareBundle.class);

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static final ThreadLocal<List<String>> TYPES = new ThreadLocal<>();

    private final ResourceBundle _delegate;

    public TypeAwareBundle(ResourceBundle delegate) {
        _delegate = delegate;
    }

    /** Wraps the bundle unless it is already wrapped. */
    public static ResourceBundle wrap(ResourceBundle bundle) {
        if (bundle == null || bundle instanceof TypeAwareBundle) {
            return bundle;
        }
        return new TypeAwareBundle(bundle);
    }

    /**
     * True if the theme has the key. If not (e.g. a theme written before the key existed), one
     * warning is logged per key and the caller should skip the announce instead of sending the key.
     */
    public static boolean hasThemeKey(ResourceBundle bundle, String key) {
        if (bundle != null && bundle.containsKey(key)) {
            return true;
        }
        if (WARNED.add(key)) {
            logger.warn("Theme key {} is missing from the announcer theme, skipping this announce. "
                    + "Copy it from the matching .theme.default file to enable it.", key);
        }
        return false;
    }

    /** Sets the type chain used by lookups on the calling thread; null or empty clears it. */
    public static void setTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            TYPES.remove();
        } else {
            TYPES.set(types);
        }
    }

    public static void clearTypes() {
        TYPES.remove();
    }

    /** True if the bundle has at least one key ending in one of the given suffixes (e.g. ".mp3.vbr"). */
    public boolean hasKeySuffix(String... suffixes) {
        for (String key : Collections.list(_delegate.getKeys())) {
            for (String suffix : suffixes) {
                if (key.endsWith(suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected Object handleGetObject(String key) {
        List<String> types = TYPES.get();
        if (types != null) {
            for (String type : types) {
                String candidate = key + "." + type;
                if (_delegate.containsKey(candidate)) {
                    return _delegate.getObject(candidate);
                }
            }
        }
        return _delegate.containsKey(key) ? _delegate.getObject(key) : null;
    }

    @Override
    public Enumeration<String> getKeys() {
        return _delegate.getKeys();
    }
}
