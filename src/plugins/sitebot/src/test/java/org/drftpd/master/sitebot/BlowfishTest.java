package org.drftpd.master.sitebot;


import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlowfishTest  {

    @RepeatedTest(10000)
    public void testCBC() {
        BlowfishManager manager = new BlowfishManager("drftpd", "CBC");
        String text = "You need to specify a Ident@IP to add " + Math.random();
        String encrypt = manager.encrypt(text);
        String decrypt = manager.decrypt(encrypt);
        assertEquals(decrypt, text);
    }

    @RepeatedTest(10000)
    public void testECB() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        String text = "You need to specify a Ident@IP to add " + Math.random();
        String encrypt = ecb.encrypt(text);
        String decrypt = ecb.decrypt(encrypt);
        assertEquals(decrypt, text);
    }
}
