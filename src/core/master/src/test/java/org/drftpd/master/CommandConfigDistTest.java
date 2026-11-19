package org.drftpd.master;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks how command configuration files and their .conf.dist defaults are combined.
 */
public class CommandConfigDistTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void requireNoConfigPathOverride() {
        // loadCommandConfig prefixes the directory with DRFTPD_CONFIG_PATH when it is set
        Assumptions.assumeTrue(System.getenv("DRFTPD_CONFIG_PATH") == null);
    }

    private void write(String name, String... commands) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append(cmd).append(" {\n").append("plugin test\n").append("}\n\n");
        }
        Files.writeString(dir.resolve(name), sb.toString());
    }

    private HashMap<String, Properties> load() {
        return GlobalContext.loadCommandConfig(dir.toAbsolutePath().toString());
    }

    @Test
    public void distIsUsedWhenNoConfExists() throws IOException {
        write("core.conf.dist", "ABOR", "LIST");

        HashMap<String, Properties> cmds = load();

        assertTrue(cmds.containsKey("abor"));
        assertTrue(cmds.containsKey("list"));
    }

    @Test
    public void confShadowsItsDist() throws IOException {
        write("core.conf.dist", "ABOR", "LIST");
        write("core.conf", "ABOR");

        HashMap<String, Properties> cmds = load();

        assertTrue(cmds.containsKey("abor"));
        assertFalse(cmds.containsKey("list"), "the .dist must be ignored when the .conf exists");
    }

    @Test
    public void filesForDifferentPluginsAreCombined() throws IOException {
        write("core.conf", "ABOR");
        write("request.conf.dist", "REQUEST");

        HashMap<String, Properties> cmds = load();

        assertTrue(cmds.containsKey("abor"));
        assertTrue(cmds.containsKey("request"));
    }

    @Test
    public void commandDefinedTwiceStopsStartup() throws IOException {
        // Old customised core.conf still defining a command that moved to another file
        write("core.conf", "REQUEST");
        write("request.conf.dist", "REQUEST");

        assertThrows(RuntimeException.class, this::load);
    }
}
