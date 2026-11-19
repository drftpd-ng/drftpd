package org.drftpd.master.vfs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CommitManagerQueueSizeTest {

    private static class Item implements Commitable {
        private final String _name;

        Item(String name) {
            _name = name;
        }

        @Override
        public void commit() {
        }

        @Override
        public void writeToDisk() {
        }

        @Override
        public String descriptiveName() {
            return _name;
        }
    }

    @Test
    public void queueSizeFollowsAddAndRemove() {
        CommitManager cm = CommitManager.getCommitManager();
        int before = cm.getQueueSize();
        Item a = new Item("a");
        Item b = new Item("b");

        cm.add(a);
        cm.add(a); // already queued, ignored
        cm.add(b);
        assertEquals(before + 2, cm.getQueueSize());

        assertTrue(cm.remove(a));
        assertFalse(cm.remove(a), "a second remove must report that nothing was removed");
        assertEquals(before + 1, cm.getQueueSize());

        assertTrue(cm.remove(b));
        assertEquals(before, cm.getQueueSize());
    }

    @Test
    public void concurrentRemovesAreCountedExactlyOnce() throws Exception {
        CommitManager cm = CommitManager.getCommitManager();
        int before = cm.getQueueSize();
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            Item item = new Item("item" + i);
            items.add(item);
            cm.add(item);
        }

        // Several threads race to remove the same items, like the commit thread and remove() can
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger removed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (Item item : items) {
                    if (cm.remove(item)) {
                        removed.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS); // rethrows anything a worker thread threw
        }
        pool.shutdown();

        // Every item is reported removed by exactly one thread, and the size ends where it began
        assertEquals(items.size(), removed.get());
        assertEquals(before, cm.getQueueSize());
    }
}
