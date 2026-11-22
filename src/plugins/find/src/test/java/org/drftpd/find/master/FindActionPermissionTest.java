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
package org.drftpd.find.master;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FindActionPermissionTest {

    private static Properties findConf() {
        Properties p = new Properties();
        p.setProperty("perms", "*");
        p.setProperty("sendToSlaves", "=siteop");
        p.setProperty("deleteFromSlaves", "=siteop");
        p.setProperty("wipe", "=siteop");
        p.setProperty("nuke", "=siteop =nuke");
        return p;
    }

    @Test
    public void actionNameFindsTheConfiguredLine() {
        Properties p = findConf();
        assertEquals("wipe", Find.actionPermissionKey(p, "Wipe"));
        assertEquals("nuke", Find.actionPermissionKey(p, "Nuke"));
        assertEquals("sendToSlaves", Find.actionPermissionKey(p, "SendToSlaves"));
        assertEquals("deleteFromSlaves", Find.actionPermissionKey(p, "DeleteFromSlaves"));
        assertEquals("=siteop =nuke", p.getProperty(Find.actionPermissionKey(p, "Nuke")));
    }

    @Test
    public void missingLineUsesTheLowerCasedName() {
        assertEquals("unnuke", Find.actionPermissionKey(findConf(), "Unnuke"));
    }

    @Test
    public void defaultsAreRestrictiveExceptForPrintingAndDelete() {
        assertEquals("*", Find.defaultActionPermission("Print"));
        assertEquals("*", Find.defaultActionPermission("Printf"));
        assertEquals("*", Find.defaultActionPermission("Delete"));
        assertEquals("=siteop", Find.defaultActionPermission("Nuke"));
        assertEquals("=siteop", Find.defaultActionPermission("Unnuke"));
        assertEquals("=siteop", Find.defaultActionPermission("Wipe"));
        assertEquals("=siteop", Find.defaultActionPermission("SendToSlaves"));
        assertEquals("=siteop", Find.defaultActionPermission("DeleteFromSlaves"));
    }
}
