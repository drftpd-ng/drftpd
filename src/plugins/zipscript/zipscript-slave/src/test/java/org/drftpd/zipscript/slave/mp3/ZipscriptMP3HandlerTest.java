package org.drftpd.zipscript.slave.mp3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ZipscriptMP3HandlerTest {

    @TempDir
    Path tmp;

    @Test
    public void parserReportsInvalidMp3WithDedicatedException() throws IOException {
        Path file = tmp.resolve("not-an.mp3");
        Files.writeString(file, "This is not an MP3 file content.");

        assertThrows(InvalidMP3Exception.class, () -> new MP3Parser(file.toFile()).getMP3Info());
    }

    @Test
    public void invalidMp3ReturnsNull() throws IOException {
        Path file = tmp.resolve("not-an.mp3");
        Files.writeString(file, "This is not an MP3 file content.");

        // The handler is not wired to a slave here, only the file-based method is tested
        ZipscriptMP3Handler handler = new ZipscriptMP3Handler(null);

        assertNull(handler.getMP3InfoFromFile(file.toFile()));
    }

    @Test
    public void otherIOErrorsArePropagated() {
        ZipscriptMP3Handler handler = new ZipscriptMP3Handler(null);

        assertThrows(FileNotFoundException.class,
                () -> handler.getMP3InfoFromFile(tmp.resolve("missing.mp3").toFile()));
    }
}
