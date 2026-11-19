package org.drftpd.master.slavemanagement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

public class SlaveCertificatePinTest {

    // Self-signed test certificate generated with keytool (CN=testslave)
    private static final String CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBQDCB6KADAgECAgkAmDyneOz0A0UwCgYIKoZIzj0EAwIwFDESMBAGA1UEAxMJ
            dGVzdHNsYXZlMCAXDTI2MTExOTE3MzYwMloYDzIxMjYxMDI2MTczNjAyWjAUMRIw
            EAYDVQQDEwl0ZXN0c2xhdmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQfJZ9v
            R5/5vv9k0lB6ei/+fkBoh43MkMY7z803U+/iVjLnkizuGOL3yiN0+3XepKwCuE4g
            jiFq6G3VF19mshmsoyEwHzAdBgNVHQ4EFgQUB6XzemayIg7B8LhUoJcnlB0d5h4w
            CgYIKoZIzj0EAwIDRwAwRAIgeW+BVIXlBWlY1M7eg5BLG54RI4onRGWiD49stC8n
            CBICIHNagq3QMb5dXda2poBAyLWBrdJBOwGEUZi4OkaUXkVb
            -----END CERTIFICATE-----
            """;

    // As printed by: keytool -printcert -file cert.pem
    private static final String KEYTOOL_FINGERPRINT =
            "5C:20:D9:9C:54:DA:66:BB:FB:C0:43:9F:EE:94:6B:4F:20:87:8F:F4:27:EC:50:CA:1B:86:D9:61:59:79:D8:DB";

    private static X509Certificate cert;

    @BeforeAll
    static void loadCertificate() throws Exception {
        cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(CERT_PEM.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void fingerprintMatchesKeytool() throws Exception {
        assertEquals("SHA256:" + KEYTOOL_FINGERPRINT.replace(":", ""), SlaveCertificatePin.fingerprint(cert));
    }

    @Test
    public void matchesAcceptsCommonFormats() throws Exception {
        String actual = SlaveCertificatePin.fingerprint(cert);
        assertTrue(SlaveCertificatePin.matches(actual, actual));
        assertTrue(SlaveCertificatePin.matches(KEYTOOL_FINGERPRINT, actual));
        assertTrue(SlaveCertificatePin.matches(actual.toLowerCase(), actual));
        assertTrue(SlaveCertificatePin.matches("SHA-256:" + KEYTOOL_FINGERPRINT, actual));
        assertTrue(SlaveCertificatePin.matches("  " + actual + "  ", actual));
    }

    @Test
    public void matchesRejectsOtherOrEmptyFingerprints() throws Exception {
        String actual = SlaveCertificatePin.fingerprint(cert);
        String other = "SHA256:" + "00".repeat(32);
        assertFalse(SlaveCertificatePin.matches(other, actual));
        assertFalse(SlaveCertificatePin.matches("", actual));
        assertFalse(SlaveCertificatePin.matches("SHA256:", actual));
        assertFalse(SlaveCertificatePin.matches(null, actual));
    }

    @Test
    public void notPinnedAcceptsWithOrWithoutCertificate() {
        assertTrue(SlaveCertificatePin.decide("s1", null, cert));
        assertTrue(SlaveCertificatePin.decide("s1", "", null));
    }

    @Test
    public void pinnedAcceptsOnlyTheMatchingCertificate() throws Exception {
        assertTrue(SlaveCertificatePin.decide("s1", KEYTOOL_FINGERPRINT, cert));
        assertFalse(SlaveCertificatePin.decide("s1", "SHA256:" + "00".repeat(32), cert));
    }

    @Test
    public void pinnedRejectsMissingCertificate() {
        assertFalse(SlaveCertificatePin.decide("s1", KEYTOOL_FINGERPRINT, null));
    }

    @Test
    public void plainSocketCountsAsNoCertificate() {
        Socket plain = new Socket();
        assertTrue(SlaveCertificatePin.check("s1", null, plain));
        assertFalse(SlaveCertificatePin.check("s1", KEYTOOL_FINGERPRINT, plain));
    }
}
