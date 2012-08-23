/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.controller.CrlGenerator;
import org.candlepin.pki.PKIUtility;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;

/**
 * CrlFileUtilTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CrlFileUtilTest {

    private CrlFileUtil cfu;

    @Mock private CrlGenerator crlGenerator;
    @Mock private PKIUtility pkiUtility;

    @Before
    public void init() {
        this.cfu = new CrlFileUtil(pkiUtility);
    }

    @Test(expected = IOException.class)
    public void executeGivenDirectory() throws IOException,
                   CRLException, CertificateException {
        File crlFile = new File("/tmp");
        cfu.readCRLFile(crlFile);
    }

    @Test
    public void executeGivenNonExistentFile() throws Exception {
        File crlFile = new File("/tmp/biteme.crl");

        X509CRL crl = mock(X509CRL.class);
        when(crl.getEncoded()).thenReturn(Base64.encodeBase64("encoded".getBytes()));
        when(crlGenerator.syncCRLWithDB(any(X509CRL.class))).thenReturn(crl);
        when(pkiUtility.getPemEncoded(any(X509CRL.class))).thenReturn(new byte [2]);
        cfu.writeCRLFile(crlFile, crl);
        File f = new File("/tmp/biteme.crl");
        assertTrue(f.exists());
        assertTrue(f.length() > 0);
        f.delete();
    }

    @Test
    public void emptyFile() throws IOException, CRLException, CertificateException {
        File f = null;
        try {
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            X509CRL crl = mock(X509CRL.class);
            when(crl.getEncoded()).thenReturn(Base64.encodeBase64("encoded".getBytes()));
            when(crlGenerator.syncCRLWithDB(any(X509CRL.class))).thenReturn(crl);
            when(pkiUtility.getPemEncoded(any(X509CRL.class))).thenReturn(new byte [2]);
            X509CRL updatedcrl = cfu.readCRLFile(f);
            cfu.writeCRLFile(f, updatedcrl);
            assertTrue(f.length() > 0);
        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    @Test(expected = CRLException.class)
    public void handleCRLException() throws IOException,
            CRLException, CertificateException {
        File f = null;
        try {
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            // put some garbage in the file to cause the CRLException
            FileUtils.writeByteArrayToFile(f, "gobbledygook".getBytes());
            cfu.readCRLFile(f);

        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    @Test
    public void updatecrlfile() throws Exception {
        File f = null;
        try {
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            X509CRL crl = mock(X509CRL.class);
            when(crl.getEncoded()).thenReturn(Base64.encodeBase64("encoded".getBytes()));
            when(pkiUtility.getPemEncoded(any(X509CRL.class))).thenReturn(new byte [2]);
            cfu.writeCRLFile(f, crl);
            assertTrue(f.length() > 0);
        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }
}
