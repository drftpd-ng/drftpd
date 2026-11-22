package org.drftpd.mediainfo.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mp4parser.IsoFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the MP4 size check directly, so they do not depend on the mediainfo binary being installed.
 */
public class MediaInfoMp4Test {

    @TempDir
    Path tmp;

    /** Writes a minimal valid MP4: a single 20-byte ftyp box. */
    private Path writeMinimalMp4() throws IOException {
        ByteBuffer box = ByteBuffer.allocate(20);
        box.putInt(20);
        box.put("ftyp".getBytes(StandardCharsets.US_ASCII));
        box.put("isom".getBytes(StandardCharsets.US_ASCII));
        box.putInt(0);
        box.put("isom".getBytes(StandardCharsets.US_ASCII));
        Path file = tmp.resolve("sample.mp4");
        Files.write(file, box.array());
        return file;
    }

    private static MediaInfo withActualSize(long size) {
        MediaInfo info = new MediaInfo();
        info.setActFileSize(size);
        return info;
    }

    @Test
    public void matchingSizeKeepsSampleOk() throws IOException {
        Path file = writeMinimalMp4();
        MediaInfo info = withActualSize(20);

        MediaInfo.checkMp4Size(info, file.toString(), IsoFile::new);

        assertTrue(info.getSampleOk());
    }

    @Test
    public void sizeMismatchMarksSampleBad() throws IOException {
        Path file = writeMinimalMp4();
        MediaInfo info = withActualSize(1000);

        MediaInfo.checkMp4Size(info, file.toString(), IsoFile::new);

        assertFalse(info.getSampleOk());
        assertEquals(20, info.getCalFileSize());
    }

    @Test
    public void parserRuntimeExceptionLeavesResultUnknown() {
        // isoparser throws this for valid MP4s with boxes over 2 GB (CastUtils.l2i)
        MediaInfo info = withActualSize(3_000_000_000L);

        MediaInfo.checkMp4Size(info, "big.mp4", path -> {
            throw new RuntimeException("A cast to int has gone wrong. Please contact the mp4parser discussion group.");
        });

        assertTrue(info.getSampleOk(), "a parse failure must not mark a possibly valid file as bad");
        assertEquals(0, info.getCalFileSize());
    }

    @Test
    public void parserIOExceptionLeavesResultUnknown() {
        MediaInfo info = withActualSize(1234);

        assertDoesNotThrow(() -> MediaInfo.checkMp4Size(info, "unreadable.mp4", path -> {
            throw new IOException("read error");
        }));

        assertTrue(info.getSampleOk());
        assertEquals(0, info.getCalFileSize());
    }
}
