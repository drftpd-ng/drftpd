package org.drftpd.slave.network;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Checksum;


/**
 * An output stream that also maintains a checksum of the data being
 * written. The checksum can then be used to verify the integrity of
 * the output data.
 *
 * @see         Checksum
 * @author      David Connelly
 * @since 1.1
 */
public class TestChecksumStream extends FilterOutputStream {
    private AtomicLong integer = new AtomicLong();
    private Checksum cksum;

    /**
     * Creates an output stream with the specified Checksum.
     * @param out the output stream
     * @param cksum the checksum
     */
    public TestChecksumStream(OutputStream out, Checksum cksum) {
        super(out);
        this.cksum = cksum;
    }

    /**
     * Writes a byte. Will block until the byte is actually written.
     * @param b the byte to be written
     * @throws IOException if an I/O error has occurred
     */
    public void write(int b) throws IOException {
        out.write(b);
        cksum.update(b);
    }

    /**
     * Writes an array of bytes. Will block until the bytes are
     * actually written.
     * @param b the data to be written
     * @param off the start offset of the data
     * @param len the number of bytes to be written
     * @throws    IOException if an I/O error has occurred
     */
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        long start = new Date().getTime();
        cksum.update(b, off, len);
        integer.addAndGet(new Date().getTime() - start);
        System.out.println("Update CRC takes " + integer.get() + " ms");
    }

    /**
     * Returns the Checksum for this output stream.
     * @return the Checksum
     */
    public Checksum getChecksum() {
        System.out.println("getChecksum " + (cksum));
        return cksum;
    }
}
