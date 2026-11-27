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
package org.drftpd.zipscript.master.sfv;

import org.drftpd.master.commands.ImproperUsageException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RescanOptionsTest {

    @Test
    void defaultsAreUnchanged() throws ImproperUsageException {
        for (String arg : new String[]{null, "", "   "}) {
            RescanOptions o = RescanOptions.parse(arg);
            assertFalse(o.recursive);
            assertTrue(o.force);
            assertTrue(o.deleteBad);
            assertTrue(o.deleteZeroByte);
            assertFalse(o.quiet);
            assertFalse(o.quick);
            assertNull(o.fileOrPath);
        }
    }

    @Test
    void existingOptionsStillParse() throws ImproperUsageException {
        RescanOptions o = RescanOptions.parse("-R NOFORCE nodelete nodelete0byte Quiet /some/path");
        assertTrue(o.recursive);
        assertFalse(o.force);
        assertFalse(o.deleteBad);
        assertFalse(o.deleteZeroByte);
        assertTrue(o.quiet);
        assertFalse(o.quick);
        assertEquals("/some/path", o.fileOrPath);
    }

    @Test
    void quickDisablesForce() throws ImproperUsageException {
        RescanOptions o = RescanOptions.parse("quick -r");
        assertTrue(o.quick);
        assertFalse(o.force);
        assertTrue(o.recursive);
        assertTrue(o.deleteBad);
    }

    @Test
    void markBadIsOptIn() throws ImproperUsageException {
        assertFalse(RescanOptions.parse("-r").markBad);
        RescanOptions o = RescanOptions.parse("MarkBad /x");
        assertTrue(o.markBad);
        assertTrue(o.force);
        assertEquals("/x", o.fileOrPath);
        assertEquals(".bad", RescanOptions.BAD_SUFFIX);
    }

    @Test
    void makeSfvIsOptIn() throws ImproperUsageException {
        assertFalse(RescanOptions.parse("-r quick").makeSfv);
        RescanOptions o = RescanOptions.parse("makesfv -r");
        assertTrue(o.makeSfv);
        assertTrue(o.recursive);
        assertTrue(o.force);
        assertNull(o.fileOrPath);
    }

    @Test
    void twoPathsAreRejected() {
        assertThrows(ImproperUsageException.class, () -> RescanOptions.parse("a b"));
    }
}
