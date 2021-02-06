/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DH1080Test {
    private static final Logger logger = LogManager.getLogger(DH1080Test.class);

    public static String[] correctKeys = new String[] {
        "ZitCYCAe3OrlWlYkzVqeSdr6jAk2yst2B7bPsYeldwATjQjq6U62RTQJA00TMu9O/E0bjob6qR/f6xNFyWr3Oybp51IcviiIOusScdsuVMIO6M5EmoAZoEU66hf9sJCYlm3F47E9Zf0eh3sI3ZFeYZZjItLNPPenAEs9zwm2fZzi2Xkc4dMnA",
        "UPLJ9yRki+zHbE8M1/DBSXuXUCpNf90gXtZAMabJTiYeI4L6BZjPXtZAzuPFmq9GfePD4bCuv9h0pLOysVK0P2IwFjMW0gV7sx7KpqjXsk7Uf42SsI3C4SoWotdbnGqKcWTQjhCf6qHb3yReIsB+W2xM7J93+7wWd94n5csmmR9uhvmC5wsmA",
        "l3RtgoMik15PRUwEFl3zTCDhH+aREtwAyjtijFVcfJBCHJw0nN7p5nGDfI0awOiQC/kyitreDeExPFk2cZu7KlakdwrYsUpiC1q77WVlw3WCiBJgzW2ZKVSu/YhDT+4KTvpkwkQuU6+MYw/jQnnLJDz0+npBGoImTAFkBTVEQWE/hvK0TT64A"
    };

    public static String[] incorrectKeys = new String[] {
        "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++",
        "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++",
        "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP",
    };

    @RepeatedTest(1000)
    public void testDH1080BruteForce() {
        DH1080 dh1 = new DH1080();
        DH1080 dh2 = new DH1080();
        assertFalse(dh1.isNotAValidPublicKey(dh2.getPublicKey()));
        assertFalse(dh2.isNotAValidPublicKey(dh1.getPublicKey()));
        assertNotNull(dh1.getSharedSecret(dh2.getPublicKey()));
    }

    @Test
    public void testDH1080IncorrectKeys() {
        DH1080 testDH = new DH1080();
        for (String key : incorrectKeys) {
            logger.debug("Testing Incorrect key: [{}]", key);
            assertNull(testDH.getSharedSecret(key));
        }
    }

    @Test
    public void testDH1080CorrectKeys() {
        DH1080 testDH = new DH1080();
        for (String key : correctKeys) {
            logger.debug("Testing Correct key: [{}]", key);
            assertNotNull(testDH.getSharedSecret(key));
        }
    }
}
