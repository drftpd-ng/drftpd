package org.drftpd.master.config;

import org.drftpd.common.util.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code ${version}} placeholder of login_prompt is replaced by the running version.
 */
class LoginPromptTest {

    @Test
    void placeholderIsReplaced() {
        assertEquals(Version.getDisplay() + " https://github.com/drftpd-ng/drftpd",
                Version.expandPlaceholder("${version} https://github.com/drftpd-ng/drftpd"));
    }

    @Test
    void placeholderCanBeEmbedded() {
        assertEquals("Welcome to " + Version.getDisplay() + "!",
                Version.expandPlaceholder("Welcome to ${version}!"));
    }

    @Test
    void literalTextIsUnchanged() {
        String literal = "DrFTPD 4.0.11 https://github.com/drftpd-ng/drftpd";
        assertEquals(literal, Version.expandPlaceholder(literal));
        assertEquals("", Version.expandPlaceholder(""));
    }

    @Test
    void permsConfDistUsesPlaceholder() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/master/config/perms.conf.dist")) {
            assertNotNull(in);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String line = content.lines()
                    .filter(l -> l.startsWith("login_prompt "))
                    .findFirst().orElseThrow();
            assertEquals("login_prompt ${version} https://github.com/drftpd-ng/drftpd", line);
        }
    }
}
