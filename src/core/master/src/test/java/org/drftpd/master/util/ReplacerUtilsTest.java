package org.drftpd.master.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.drftpd.master.util.ReplacerUtils.jprintf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplacerUtilsTest {

    @Test
    public void testVarReplacement() {
        Map<String, Object> env = new HashMap<>();
        env.put("position", 3);
        env.put("user", "test");
        env.put("group", "sysop");
        env.put("files", 11);
        env.put("bytes", "10To");
        env.put("speed", "27.4MB/s");
        env.put("percent", "45");
        // Complex template
        String template = "#${position,3.3} ${user,-13} @ ${group,-11} - ${files,7}F - ${bytes,7} - ${speed,10} - ${percent,6}";
        assertEquals("# 3 test      @ sysop  -    11F - 10To - 27.4MB/s -   45", jprintf(template, env));
        // Simple template
        template = "#${position} ${user} @ ${group} - ${files}F - ${bytes} - ${speed} - ${percent}";
        assertEquals("#3 test @ sysop - 11F - 10To - 27.4MB/s - 45", jprintf(template, env));
    }
}
