package org.drftpd.zipscript.slave.mp3;

import org.drftpd.zipscript.common.mp3.MP3Info;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

public class ZipscriptMP3HandlerTest {

    @Test
    public void testInvalidMP3ReturnsNull() throws IOException {
        // Create a temporary file that is definitely not an MP3
        File tempFile = File.createTempFile("test-invalid", ".mp3");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("This is not an MP3 file content.");
        }
        tempFile.deleteOnExit();

        // Create instance of handler (we can pass null for central since we are testing
        // isolated method)
        ZipscriptMP3Handler handler = new ZipscriptMP3Handler(null);

        // Call the package-private method with the file
        MP3Info result = handler.getMP3InfoFromFile(tempFile);

        // Verify it returns null instead of throwing IOException
        assertNull(result, "Should return null for invalid MP3 file");
    }
}
