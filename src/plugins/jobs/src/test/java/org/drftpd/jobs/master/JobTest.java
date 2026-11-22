package org.drftpd.jobs.master;

import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for issue #163: a job whose file already is on all destination slaves
 * must be satisfied instead of throwing an IllegalStateException.
 */
public class JobTest {

    private static class StubFile extends FileHandle {
        private final Set<String> _slaveNames;

        StubFile(String... slaveNames) {
            super("/test/file");
            _slaveNames = new HashSet<>(Arrays.asList(slaveNames));
        }

        @Override
        public Set<String> getSlaveNames() {
            return new HashSet<>(_slaveNames);
        }

        @Override
        public Set<RemoteSlave> getSlaves() {
            Set<RemoteSlave> slaves = new HashSet<>();
            for (String name : _slaveNames) {
                slaves.add(new DummyRemoteSlave(name));
            }
            return slaves;
        }
    }

    @Test
    public void fileAlreadyOnAllDestinationSlavesIsDone() throws FileNotFoundException {
        List<RemoteSlave> dest = Arrays.asList(new DummyRemoteSlave("slave1"), new DummyRemoteSlave("slave2"));
        Job job = new Job(new StubFile("slave1", "slave2"), 3, 2, dest);

        job.sentToSlaves(dest);

        assertTrue(job.isDone());
    }

    @Test
    public void fileOnSomeDestinationSlavesStillNeedsTransfer() throws FileNotFoundException {
        List<RemoteSlave> dest = Arrays.asList(new DummyRemoteSlave("slave1"), new DummyRemoteSlave("slave2"));
        Job job = new Job(new StubFile("slave1"), 3, 2, dest);

        job.sentToSlaves(List.of(dest.get(0)));

        assertFalse(job.isDone());
        assertEquals(List.of("slave2"), List.copyOf(job.getSlavesToTransferTo()));
    }

    @Test
    public void unsatisfiableJobStillFails() {
        List<RemoteSlave> dest = Arrays.asList(new DummyRemoteSlave("slave1"), new DummyRemoteSlave("slave2"));
        Job job = new Job(new StubFile("slave1", "slave2"), 3, 3, dest);

        assertThrows(IllegalStateException.class, () -> job.sentToSlaves(dest));
    }
}
