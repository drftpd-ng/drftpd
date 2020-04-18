package org.drftpd.master.sitebot;


import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlowfishTest  {

    @RepeatedTest(10)
    public void testCBC() {
        BlowfishManager manager = new BlowfishManager("drftpd", "CBC");
        String text = "You need to specify a Ident@IP to add";
        String encrypt = manager.encrypt(text);
        String decrypt = manager.decrypt(encrypt);
        assertEquals(decrypt, text);
    }
}
