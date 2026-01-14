package org.drftpd.mediainfo.common;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mp4parser.IsoFile;

public class MediaInfoTest {

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
        // Reset to default
        MediaInfo.setIsoFileFactory(IsoFile::new);
    }

    @Test
    public void testGetMediaInfoFromFile_CatchesRuntimeExceptionFromIsoFile() throws IOException {
        // 1. Setup mock factory to throw RuntimeException
        MediaInfo.setIsoFileFactory(path -> {
            throw new RuntimeException("A cast to int has gone wrong");
        });

        // 2. Create a temporary MP4 file
        File tempFile = File.createTempFile("test_crash", ".mp4");
        tempFile.deleteOnExit();

        // 3. Run the method
        try {
            MediaInfo result = MediaInfo.getMediaInfoFromFile(tempFile);

            // If we reach here, it means no crash occurred from the IsoFile parsing.

        } catch (IOException e) {
            // Ignored: mediainfo binary likely missing
            System.out.println("MediaInfo binary missing or failed, skipping deep verification.");
        } catch (RuntimeException e) {
            if (e.getMessage().equals("A cast to int has gone wrong")) {
                fail("Should have caught the RuntimeException!");
            }
            throw e;
        } finally {
            tempFile.delete();
        }
    }
}
