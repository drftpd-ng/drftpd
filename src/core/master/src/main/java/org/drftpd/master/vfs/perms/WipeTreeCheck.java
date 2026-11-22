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
package org.drftpd.master.vfs.perms;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tree walk used to make a recursive wipe honour the 'wipe' path permissions of
 * everything below the wiped directory (issue #253). Kept independent of the VFS
 * so it can be unit tested with a plain tree.
 */
public final class WipeTreeCheck {

    private WipeTreeCheck() {
    }

    /**
     * Walks the subtree below root (root itself is not tested) depth first and returns
     * the first node for which denied is true, or null when nothing is denied.
     *
     * @param children returns the direct children of a node (empty for leaves).
     * @param denied   true if the node must block the wipe.
     */
    public static <T> T findFirstDenied(T root, Function<T, ? extends Collection<? extends T>> children,
                                        Predicate<T> denied) {
        Deque<T> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            T node = pending.pop();
            // push in reverse so iteration order is preserved when popping
            Deque<T> kids = new ArrayDeque<>();
            for (T child : children.apply(node)) {
                if (denied.test(child)) {
                    return child;
                }
                kids.push(child);
            }
            kids.forEach(pending::push);
        }
        return null;
    }
}
