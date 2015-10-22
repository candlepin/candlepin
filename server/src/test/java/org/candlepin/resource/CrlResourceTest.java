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
package org.candlepin.resource;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.util.CrlFileUtil;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.util.LinkedList;
import java.util.List;



/**
 * CrlResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CrlResourceTest {
    private CrlResource resource;

    private File testFile;

    @Mock private Configuration config;
    @Mock private CrlFileUtil crlFileUtil;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private PKIUtility pkiUtility;

    @Before
    public void init() throws Exception {
        this.testFile = File.createTempFile("test-", "crl");

        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(this.testFile.getAbsolutePath());
        this.resource = new CrlResource(
            this.config, this.crlFileUtil, this.pkiUtility, this.certSerialCurator
        );
    }

    @After
    public void cleanup() {
        if (this.testFile != null) {
            this.testFile.delete();
        }
    }

    @Test
    public void testGetCurrentCrl() throws Exception {
        Object response = this.resource.getCurrentCrl(null);

        assertTrue(response != null);
        verify(crlFileUtil).syncCRLWithDB(any(File.class));
    }

    @Test
    public void testGetCurrentCrlWithNoFile() throws Exception {
        this.cleanup();
        Object response = this.resource.getCurrentCrl(null);

        assertTrue(response != null);
        verify(crlFileUtil).syncCRLWithDB(any(File.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUnrevokeWithArguments() throws Exception {
        String[] input = new String[] { "123", "456", "789" };

        List<CertificateSerial> serials = new LinkedList<CertificateSerial>();
        serials.add(new CertificateSerial(123L));
        serials.add(new CertificateSerial(456L));
        serials.add(new CertificateSerial(789L));

        when(this.certSerialCurator.listBySerialIds(eq(input))).thenReturn(serials);

        this.resource.unrevoke(input);

        verify(crlFileUtil).updateCRLFile(any(File.class), anyCollection(), anyCollection());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUnrevokeWithNoArguments() throws Exception {
        String[] input = new String[] { };

        List<CertificateSerial> serials = new LinkedList<CertificateSerial>();

        when(this.certSerialCurator.listBySerialIds(eq(input))).thenReturn(serials);

        this.resource.unrevoke(input);

        verifyNoMoreInteractions(crlFileUtil);
    }

}
