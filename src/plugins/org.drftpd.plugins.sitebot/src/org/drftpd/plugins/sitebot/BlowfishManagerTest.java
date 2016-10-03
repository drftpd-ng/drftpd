package org.drftpd.plugins.sitebot;

public class BlowfishManagerTest {

    /**
     * Key
     */
    private static final String key = "nrcwaTv2euON+gDEWOAdswaqDEljMT6Whyx6kgZrs0U";

    /**
     * String to crypt
     */
    private static final String initial = "!help";


    public static void main(String[] args) throws Exception {
        System.out.println("Initial : " + initial);
        System.out.println("ECB ENCRYPTION");
        BlowfishManager lBlowECB = new BlowfishManager(key, "ecb");
        String encryptedECB = lBlowECB.encrypt(initial);
        System.out.println("Encrypt : " + encryptedECB);
        System.out.println("Decrypt : " + lBlowECB.decrypt(encryptedECB));
        System.out.println("--------");
        System.out.println("CBC ENCRYPTION");
        BlowfishManager lBlowCBC = new BlowfishManager(key, "cbc");
        String encryptedCBC = lBlowCBC.encrypt(initial);
        System.out.println("Encrypt : " + encryptedCBC);
        System.out.println("Decrypt : " + lBlowCBC.decrypt(encryptedCBC));
        System.out.println("--------");
        BlowfishManager test = new BlowfishManager("ZigkaBZXaUiifHCdUXzzFkJkAVnMdns8bTdqYvy/3sg", "ecb");
        System.out.println("Decrypt : " + test.decrypt("+OK PRn3r1D4.sn1"));
    }
}
