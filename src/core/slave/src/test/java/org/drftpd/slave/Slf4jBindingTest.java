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
package org.drftpd.slave;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Libraries logging through SLF4J (reflections, httpclient5, ...) must end up in log4j,
 * not in the NOP fallback ("no providers were found").
 */
class Slf4jBindingTest {

    @Test
    void slf4jIsBoundToLog4j() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        assertFalse(factory instanceof NOPLoggerFactory, "SLF4J fell back to NOP logger factory");
        assertTrue(factory.getClass().getName().startsWith("org.apache.logging.slf4j."),
                "SLF4J is not bound to log4j: " + factory.getClass().getName());
    }
}
