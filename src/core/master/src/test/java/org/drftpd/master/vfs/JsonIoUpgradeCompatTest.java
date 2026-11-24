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
package org.drftpd.master.vfs;

import com.cedarsoftware.util.io.JsonReader;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.util.HostMask;
import org.drftpd.master.commands.usermanagement.GroupManagement;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.stats.ExtendedTimedStats;
import org.drftpd.master.tests.DummyEncryptedBeanUserManager;
import org.drftpd.master.tests.LegacyJsonIo;
import org.drftpd.master.usermanager.Group;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUser;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager;
import org.drftpd.master.usermanager.javabeans.BeanGroup;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sites that upgrade have userfiles, groupfiles and VFS inodes on disk that were written by json-io 4.14.
 * These are written here with the real 4.14 jar (dist/json-io-4.14.0.jar, loaded child-first in its own
 * class loader) from real drftpd objects, and read back with the json-io on the classpath through the same
 * calls as VirtualFileSystem.loadInode() and BeanUserManager.loadUser()/loadGroup().
 * <p>
 * The test lives in org.drftpd.master.vfs because loadInode() calls the package-private setName().
 */
public class JsonIoUpgradeCompatTest {

    private static final Key<String> DIR_STRING_KEY = new Key<>(VirtualFileSystemDirectory.class, "compat-test-note");
    private static final Key<Integer> DIR_INT_KEY = new Key<>(VirtualFileSystemDirectory.class, "compat-test-count");

    private static final long LAST_MODIFIED = 1_600_000_000_123L;
    private static final long CREATED = 1_500_000_000_456L;

    private static LegacyJsonIo oldJsonIo;

    @TempDir
    Path tmp;

    @BeforeAll
    static void loadOldJsonIo() throws Exception {
        // Fails with a clear message if dist/json-io-4.14.0.jar cannot be found
        Path jar = LegacyJsonIo.locateJar();
        oldJsonIo = new LegacyJsonIo(jar, JsonIoUpgradeCompatTest.class.getClassLoader());
        // Make sure the two versions really are different classes
        assertNotSame(com.cedarsoftware.util.io.JsonWriter.class, oldJsonIo.getWriterClass(),
                "json-io 4.14 JsonWriter must not be the one from the classpath");
    }

    @AfterAll
    static void closeOldJsonIo() throws Exception {
        if (oldJsonIo != null) {
            oldJsonIo.close();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void writeOld(Object obj, File target) throws Exception {
        Files.createDirectories(target.getParentFile().toPath());
        try (OutputStream out = new FileOutputStream(target)) {
            oldJsonIo.write(obj, out);
        }
    }

    /**
     * Same steps as {@link VirtualFileSystem#loadInode(String)}, with the VFS root at {@code vfsRoot}
     * instead of userdata/vfs.
     */
    private static VirtualFileSystemInode loadInode(File vfsRoot, String path) throws Exception {
        File jsonFile = new File(vfsRoot, path);
        File realDirectory = null;
        if (jsonFile.isDirectory()) {
            realDirectory = jsonFile;
            jsonFile = new File(jsonFile, VirtualFileSystem.dirName);
        }
        try (InputStream in = new FileInputStream(jsonFile);
             JsonReader reader = new JsonReader(in)) {
            VirtualFileSystemInode inode = (VirtualFileSystemInode) reader.readObject();
            inode.setName(VirtualFileSystem.getLast(path));
            if (inode.isDirectory()) {
                VirtualFileSystemDirectory dir = (VirtualFileSystemDirectory) inode;
                dir.setFiles(realDirectory.list(new VirtualFileSystem.DirInodeFilenameFilter()));
            }
            inode.inodeLoadCompleted();
            return inode;
        }
    }

    /**
     * Same steps as BeanUserManager.loadUser(), which EncryptedBeanUserManager inherits.
     */
    private static BeanUser loadUser(File userFile, EncryptedBeanUserManager um) throws Exception {
        try (InputStream in = new FileInputStream(userFile);
             JsonReader reader = new JsonReader(in)) {
            BeanUser user = (BeanUser) reader.readObject();
            user.setUserManager(um);
            return user;
        }
    }

    /**
     * Same steps as BeanUserManager.loadGroup().
     */
    private static BeanGroup loadGroup(File groupFile, EncryptedBeanUserManager um) throws Exception {
        try (InputStream in = new FileInputStream(groupFile);
             JsonReader reader = new JsonReader(in)) {
            BeanGroup group = (BeanGroup) reader.readObject();
            group.setUserManager(um);
            return group;
        }
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Manager that resolves group names without any group files on disk, so membership checks work.
     */
    private static class GroupResolvingManager extends DummyEncryptedBeanUserManager {
        GroupResolvingManager() {
            super(EncryptedBeanUserManager.PASSCRYPT_SHA256);
        }

        @Override
        public Group getGroupByNameUnchecked(String groupname) {
            return new BeanGroup(this, groupname);
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void oldWriterProducesLegacyFormat() throws Exception {
        String json = oldJsonIo.toJson(new VirtualFileSystemLink("drftpd", "drftpd", "/somewhere"));
        assertTrue(json.contains("\"@type\""), "json-io 4.14 output should contain \"@type\": " + json);
    }

    @Test
    public void readsOldInodes() throws Exception {
        File vfsRoot = tmp.resolve("vfs").toFile();

        // Directory /Release with three children: a file, a link and a sub directory
        VirtualFileSystemDirectory dir = new VirtualFileSystemDirectory("alice", "staff");
        dir.setLastModified(LAST_MODIFIED);
        dir.setCreationTime(CREATED);
        dir.setSize(15_000_000L);
        dir.getKeyedMap().setObject(DIR_STRING_KEY, "complete");
        dir.getKeyedMap().setObject(DIR_INT_KEY, 7);
        dir.getUntypedPluginMap().put("plugin-flag", "yes");
        Map<String, AtomicInteger> refCounts = new TreeMap<>();
        refCounts.put("slave1", new AtomicInteger(2));
        refCounts.put("slave2", new AtomicInteger(1));
        dir.setSlaveRefCounts(refCounts);
        writeOld(dir, new File(vfsRoot, "Release/" + VirtualFileSystem.dirName));

        VirtualFileSystemFile file = new VirtualFileSystemFile("bob", "nuke", 15_000_000L,
                new HashSet<>(Set.of("slave1", "slave2")));
        file.setLastModified(LAST_MODIFIED - 1000);
        file.setCreationTime(CREATED - 1000);
        // Straight into the keyed map, the setters call commit()
        file.getKeyedMap().setObject(VirtualFileSystemFile.CRC, 0xDEADBEEFL);
        file.getKeyedMap().setObject(VirtualFileSystemFile.XFERTIME, 12_345L);
        file.getKeyedMap().setObject(VirtualFileSystemFile.DOWNLOADEDTIMES, 3);
        writeOld(file, new File(vfsRoot, "Release/file.rar"));

        VirtualFileSystemLink link = new VirtualFileSystemLink("carol", "staff", "/Release/Sample");
        link.setLastModified(LAST_MODIFIED - 2000);
        writeOld(link, new File(vfsRoot, "Release/sample-link"));

        VirtualFileSystemDirectory sub = new VirtualFileSystemDirectory("alice", "staff");
        writeOld(sub, new File(vfsRoot, "Release/Sample/" + VirtualFileSystem.dirName));

        // --- directory
        VirtualFileSystemInode dirInode = loadInode(vfsRoot, "/Release");
        VirtualFileSystemDirectory readDir = assertInstanceOf(VirtualFileSystemDirectory.class, dirInode);
        assertEquals("Release", readDir.getName());
        assertEquals("alice", readDir.getUsername());
        assertEquals("staff", readDir.getGroup());
        assertEquals(LAST_MODIFIED, readDir.getLastModified());
        assertEquals(CREATED, readDir.getCreationTime());
        assertEquals(15_000_000L, readDir.getSize());
        assertEquals("complete", readDir.getKeyedMap().getObject(DIR_STRING_KEY));
        assertEquals(7, readDir.getKeyedMap().getObjectInteger(DIR_INT_KEY));
        assertEquals("yes", readDir.<String>getUntypedPluginMetaData("plugin-flag"));
        Map<String, AtomicInteger> readCounts = readDir.getSlaveRefCounts();
        assertEquals(Set.of("slave1", "slave2"), readCounts.keySet());
        assertInstanceOf(AtomicInteger.class, readCounts.get("slave1"));
        assertEquals(2, readCounts.get("slave1").get());
        assertEquals(1, readCounts.get("slave2").get());
        assertEquals(Set.of("file.rar", "sample-link", "Sample"), readDir.getInodeNames());
        assertTrue(readDir.isInodeLoaded());

        // --- file
        VirtualFileSystemFile readFile = assertInstanceOf(VirtualFileSystemFile.class,
                loadInode(vfsRoot, "/Release/file.rar"));
        assertEquals("file.rar", readFile.getName());
        assertEquals("bob", readFile.getUsername());
        assertEquals("nuke", readFile.getGroup());
        assertEquals(15_000_000L, readFile.getSize());
        assertEquals(0xDEADBEEFL, readFile.getChecksum());
        assertEquals(12_345L, readFile.getXfertime());
        assertEquals(3, readFile.getDownloadedFiles());
        assertEquals(Set.of("slave1", "slave2"), readFile.getSlaves());
        assertEquals(LAST_MODIFIED - 1000, readFile.getLastModified());
        assertEquals(CREATED - 1000, readFile.getCreationTime());

        // --- link
        VirtualFileSystemLink readLink = assertInstanceOf(VirtualFileSystemLink.class,
                loadInode(vfsRoot, "/Release/sample-link"));
        assertEquals("/Release/Sample", readLink.getLinkPath());
        assertEquals("carol", readLink.getUsername());
        assertEquals("staff", readLink.getGroup());
        assertEquals(LAST_MODIFIED - 2000, readLink.getLastModified());

        // --- empty sub directory
        VirtualFileSystemDirectory readSub = assertInstanceOf(VirtualFileSystemDirectory.class,
                loadInode(vfsRoot, "/Release/Sample"));
        assertTrue(readSub.getInodeNames().isEmpty());
        assertTrue(readSub.getSlaveRefCounts().isEmpty());
    }

    @Test
    public void readsOldUserFile() throws Exception {
        Date created = new Date(CREATED);
        Date lastSeen = new Date(LAST_MODIFIED);
        String hash = sha256("s3cret");

        // EncryptedBeanUserManager is the default usermanager, so its users are what is on disk
        EncryptedBeanUser user = new EncryptedBeanUser("alice");
        user.setCredits(123_456_789L);
        user.setIdleTime(300);
        user.setLastReset(LAST_MODIFIED);
        user.getKeyedMap().setObject(UserManagement.RATIO, 3.0f);
        user.getKeyedMap().setObject(UserManagement.TAGLINE, "old school");
        user.getKeyedMap().setObject(UserManagement.COMMENT, "from 4.14");
        user.getKeyedMap().setObject(UserManagement.MAXLOGINS, 2);
        user.getKeyedMap().setObject(UserManagement.WKLYALLOTMENT, 50_000_000_000L);
        user.getKeyedMap().setObject(UserManagement.CREATED, created);
        user.getKeyedMap().setObject(UserManagement.LASTSEEN, lastSeen);
        user.setMaxSimUp(4);
        user.setMaxSimDown(3);
        user.setGroup(new BeanGroup("staff"));
        user.addSecondaryGroup(new BeanGroup("nuke"));
        user.addSecondaryGroup(new BeanGroup("siteop"));
        user.addIPMask("*@127.0.0.1");
        user.addIPMask("ident@10.0.0.*");
        user.setUploadedBytesForPeriod(ExtendedTimedStats.P_ALL, 9_000_000_000L);
        user.setUploadedFilesForPeriod(ExtendedTimedStats.P_ALL, 900);
        user.setUploadedTimeForPeriod(ExtendedTimedStats.P_ALL, 90_000L);
        user.setUploadedBytesForPeriod(ExtendedTimedStats.P_DAY, 1_000_000L);
        user.setUploadedFilesForPeriod(ExtendedTimedStats.P_DAY, 10);
        user.setUploadedTimeForPeriod(ExtendedTimedStats.P_DAY, 1_000L);
        user.setDownloadedBytesForPeriod(ExtendedTimedStats.P_ALL, 8_000_000_000L);
        user.setDownloadedFilesForPeriod(ExtendedTimedStats.P_ALL, 800);
        user.setDownloadedTimeForPeriod(ExtendedTimedStats.P_ALL, 80_000L);
        user.setDownloadedBytesForPeriod(ExtendedTimedStats.P_WEEK, 2_000_000L);
        user.setDownloadedFilesForPeriod(ExtendedTimedStats.P_WEEK, 20);
        user.setDownloadedTimeForPeriod(ExtendedTimedStats.P_WEEK, 2_000L);
        user.setEncryption(EncryptedBeanUserManager.PASSCRYPT_SHA256);
        // No usermanager yet, so this stores the given hash as is
        user.setPassword(hash);

        File userFile = tmp.resolve("users/javabeans/alice").toFile();
        writeOld(user, userFile);

        BeanUser read = loadUser(userFile, new GroupResolvingManager());
        EncryptedBeanUser readUser = assertInstanceOf(EncryptedBeanUser.class, read);

        assertEquals("alice", readUser.getName());
        assertEquals(123_456_789L, readUser.getCredits());
        assertEquals(300, readUser.getIdleTime());
        assertEquals(LAST_MODIFIED, readUser.getLastReset());

        assertEquals(3.0f, readUser.getKeyedMap().getObjectFloat(UserManagement.RATIO));
        assertEquals("old school", readUser.getKeyedMap().getObjectString(UserManagement.TAGLINE));
        assertEquals("from 4.14", readUser.getKeyedMap().getObjectString(UserManagement.COMMENT));
        assertEquals(2, readUser.getKeyedMap().getObjectInteger(UserManagement.MAXLOGINS));
        assertEquals(50_000_000_000L, readUser.getKeyedMap().getObjectLong(UserManagement.WKLYALLOTMENT));
        assertEquals(created, readUser.getKeyedMap().getObject(UserManagement.CREATED));
        assertEquals(lastSeen, readUser.getKeyedMap().getObject(UserManagement.LASTSEEN));
        assertEquals(4, readUser.getMaxSimUp());
        assertEquals(3, readUser.getMaxSimDown());

        assertNotNull(readUser.getGroup());
        assertEquals("staff", readUser.getGroup().getName());
        assertEquals(List.of("nuke", "siteop"),
                readUser.getGroups().stream().map(Group::getName).collect(Collectors.toList()));
        assertTrue(readUser.isMemberOf("staff"));
        assertTrue(readUser.isMemberOf("nuke"));
        assertTrue(readUser.isAdmin());
        assertFalse(readUser.isMemberOf("other"));

        assertEquals(List.of("*@127.0.0.1", "ident@10.0.0.*"),
                readUser.getHostMaskCollection().stream().map(HostMask::getMask).collect(Collectors.toList()));
        // Host names given, so no reverse DNS lookups
        InetAddress local = InetAddress.getByAddress("localhost", new byte[]{127, 0, 0, 1});
        InetAddress lan = InetAddress.getByAddress("lan.invalid", new byte[]{10, 0, 0, 5});
        assertTrue(readUser.getHostMaskCollection().check("anyone", local));
        assertTrue(readUser.getHostMaskCollection().check("ident", lan));
        assertFalse(readUser.getHostMaskCollection().check("other", lan));

        assertEquals(9_000_000_000L, readUser.getUploadedBytes());
        assertEquals(900, readUser.getUploadedFiles());
        assertEquals(90_000L, readUser.getUploadedTime());
        assertEquals(1_000_000L, readUser.getUploadedBytesForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(10, readUser.getUploadedFilesForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(1_000L, readUser.getUploadedTimeForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(8_000_000_000L, readUser.getDownloadedBytes());
        assertEquals(800, readUser.getDownloadedFiles());
        assertEquals(80_000L, readUser.getDownloadedTime());
        assertEquals(2_000_000L, readUser.getDownloadedBytesForPeriod(ExtendedTimedStats.P_WEEK));
        assertEquals(20, readUser.getDownloadedFilesForPeriod(ExtendedTimedStats.P_WEEK));
        assertEquals(2_000L, readUser.getDownloadedTimeForPeriod(ExtendedTimedStats.P_WEEK));
        assertEquals(0L, readUser.getUploadedBytesForPeriod(ExtendedTimedStats.P_MONTH));

        assertEquals(EncryptedBeanUserManager.PASSCRYPT_SHA256, readUser.getEncryption());
        assertEquals(hash, readUser.getPassword());
        // Same passcrypt as the manager, so no re-hash and no commit
        assertTrue(readUser.checkPassword("s3cret"));
        assertFalse(readUser.checkPassword("wrong"));
    }

    @Test
    public void readsOldGroupFile() throws Exception {
        Date created = new Date(CREATED);

        BeanGroup group = new BeanGroup("staff");
        group.setGroupSlots(5);
        group.setLeechSlots(2);
        group.setMinRatio(1.5f);
        group.setMaxRatio(4.0f);
        group.setCreated(created);
        group.addAdmin(new BeanUser("alice"));
        group.addAdmin(new BeanUser("bob"));

        File groupFile = tmp.resolve("groups/javabeans/staff").toFile();
        writeOld(group, groupFile);

        BeanGroup readGroup = loadGroup(groupFile, new GroupResolvingManager());
        assertEquals("staff", readGroup.getName());
        assertEquals(5, readGroup.getGroupSlots());
        assertEquals(2, readGroup.getLeechSlots());
        assertEquals(1.5f, readGroup.getMinRatio());
        assertEquals(4.0f, readGroup.getMaxRatio());
        assertEquals(created, readGroup.getCreated());
        // Members are stored on the user side (see readsOldUserFile), groups only keep their admins
        assertTrue(readGroup.isAdmin(new BeanUser("alice")));
        assertTrue(readGroup.isAdmin(new BeanUser("bob")));
        assertFalse(readGroup.isAdmin(new BeanUser("carol")));
    }
}
