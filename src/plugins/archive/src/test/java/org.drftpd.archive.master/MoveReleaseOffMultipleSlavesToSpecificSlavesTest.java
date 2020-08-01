package org.drftpd.archive.master;

import org.drftpd.archive.master.archivetypes.MoveReleaseOffMultipleSlavesToSpecificSlaves;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.protocol.MasterProtocolCentral;
import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.SlaveManager;
import org.drftpd.master.vfs.DirectoryHandle;

import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MoveReleaseOffMultipleSlavesToSpecificSlavesTest {

    private static GC _globalContext = null;

    @BeforeAll
    static void setUp() {
        DummySlaveManager sm = new DummySlaveManager();
        HashMap<String, RemoteSlave> slaves = new HashMap<>();
        slaves.put("SRC1", new DummyRemoteSlave("SRC1"));
        slaves.put("SRC2", new DummyRemoteSlave("SRC2"));
        slaves.put("DEST1", new DummyRemoteSlave("DEST1"));
        slaves.put("DEST2", new DummyRemoteSlave("DEST2"));
        slaves.put("DEST3", new DummyRemoteSlave("DEST3"));
        sm.setSlaves(slaves);

        _globalContext = new GC();
        _globalContext.setSlaveManager(sm);
    }

    @Test
    public void testConfigOne() {
        assertEquals(_globalContext.getSlaveManager().getSlaves().size(), 5);
        TestArchive a = new TestArchive(_globalContext);
        Section s = new Section(new DirectoryHandle("/one"));
        int confNum = 1;
        Properties p = new Properties();
        p.put(confNum + ".archiveafter", 43200);
        p.put(confNum + ".numofslaves", 2);
        p.put(confNum + ".archiveregex", "^.*(1080(p|i)).*$");
        p.put(confNum + ".priority", 5);
        p.put(confNum + ".repeat", 10);
        p.put(confNum + ".offofslave.1", "SRC1");
        p.put(confNum + ".offofslave.2", "SRC2");
        p.put(confNum + ".slavename.1", "DEST1");
        p.put(confNum + ".slavename.2", "DEST2");
        p.put(confNum + ".slavename.3", "DEST3");
        MoveReleaseOffMultipleSlavesToSpecificSlaves instance = new TestMoveReleaseOffMultipleSlavesToSpecificSlaves(_globalContext, a, s, p, confNum);

        assertEquals(instance.getConfNum(), confNum);
        assertEquals(instance.getSection().getName(), s.getName());
        assertNull(instance.getDirectory());
        assertEquals(instance.getArchiveRegex(), "^.*(1080(p|i)).*$");
        assertEquals(instance.getParent(), a);
        assertEquals(instance.getRepeat(), 10);
        assertFalse(instance.isBusy());
        assertFalse(instance.isManual());
        assertFalse(instance.moveReleaseOnly());
        assertFalse(instance.isMovingRelease());
        assertNull(instance.getDestinationDirectory());
        assertFalse(instance.checkFailedDir("bla"));
    }

    @Test
    public void testConfigTwo() {
        TestArchive a = new TestArchive(_globalContext);
        Section s = new Section(new DirectoryHandle("/two"));
        int confNum = 5;
        Properties p = new Properties();
        p.put(confNum + ".archiveafter", 43200);
        p.put(confNum + ".numofslaves", 1);
        p.put(confNum + ".offofslave.1", "SRC1");
        p.put(confNum + ".slavename.1", "DEST1");
        MoveReleaseOffMultipleSlavesToSpecificSlaves instance = new TestMoveReleaseOffMultipleSlavesToSpecificSlaves(_globalContext, a, s, p, confNum);

        assertEquals(instance.getConfNum(), confNum);
        assertEquals(instance.getSection().getName(), s.getName());
        assertNull(instance.getDirectory());
        assertEquals(instance.getArchiveRegex(), ".*");
        assertEquals(instance.getParent(), a);
        assertEquals(instance.getRepeat(), 1);
        assertFalse(instance.isBusy());
        assertFalse(instance.isManual());
        assertFalse(instance.moveReleaseOnly());
        assertFalse(instance.isMovingRelease());
        assertNull(instance.getDestinationDirectory());
        assertFalse(instance.checkFailedDir("bla"));
    }

    static class TestMoveReleaseOffMultipleSlavesToSpecificSlaves extends MoveReleaseOffMultipleSlavesToSpecificSlaves {
        GC _gc;

        public TestMoveReleaseOffMultipleSlavesToSpecificSlaves(GC gc, TestArchive a, SectionInterface s, Properties p, int confNum) {
            super(a, s, p, confNum);
            _gc = gc;
        }

        @Override
        public GlobalContext getGlobalContext() {
            return _gc;
        }
    }

    static class TestArchive extends Archive {
        GC _gc;

        public TestArchive(GC gc) {
            super();
            _gc = gc;
        }

        @Override
        public void startPlugin() {}

        @Override
        public GlobalContext getGlobalContext() {
            return _gc;
        }
    }

    static class DummySlaveManager extends SlaveManager {
        public DummySlaveManager() {
            _central = new MasterProtocolCentral();
        }

        public void setSlaves(HashMap<String, RemoteSlave> rslaves) {
            _rslaves = rslaves;
        }

        @Override
        public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
            return getSlaves();
        }

        @Override
        public RemoteSlave getRemoteSlave(String s) throws ObjectNotFoundException {
            return new DummyRemoteSlave(s);
        }
    }

    static class GC extends GlobalContext {
        public GC() {
            _gctx = GlobalContext.getGlobalContext();
            init();
        }

        @Override
        public SlaveManager getSlaveManager() {
            return super.getSlaveManager();
        }

        public void setSlaveManager(SlaveManager sm) {
            _slaveManager = sm;
        }

        @Override
        public List<PluginInterface> getPlugins() {
            return new ArrayList<>();
        }


        @Override
        public DirectoryHandle getRoot() {
            return new DirectoryHandle("/");
        }
    }

    static class Section implements SectionInterface {
        private final DirectoryHandle _dir;

        public Section(DirectoryHandle lrf) {
            _dir = lrf;
        }

        public DirectoryHandle getCurrentDirectory() {
            return _dir;
        }

        public Set<DirectoryHandle> getDirectories() {
            try {
                return _dir.getDirectoriesUnchecked();
            } catch (FileNotFoundException e) {
                return Collections.emptySet();
            }
        }

        public String getName() {
            return _dir.getName();
        }

        public String getColor() {
            return "15";
        }

        public String getPath() {
            return _dir.getPath();
        }

        public DirectoryHandle getBaseDirectory() {
            return _dir;
        }

        public String getBasePath() {
            return getPath();
        }

        @Override
        public boolean equals(Object arg0) {
            if (!(arg0 instanceof Section)) {
                return false;
            }
            Section compareSection = (Section) arg0;
            return getBaseDirectory().equals(compareSection.getBaseDirectory());
        }
    }
}
