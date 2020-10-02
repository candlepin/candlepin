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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.util.CrlFileUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;


/**
 * CrlResourceTest
 */
@ExtendWith(MockitoExtension.class)
public class CrlResourceTest {
    private CrlResource resource;

    private File testFile;

    @Mock private Configuration config;
    @Mock private CrlFileUtil crlFileUtil;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private PKIUtility pkiUtility;

    @BeforeEach
    public void init() throws Exception {
        this.testFile = File.createTempFile("test-", "crl");

        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(this.testFile.getAbsolutePath());
        this.resource = new CrlResource(
            this.config, this.crlFileUtil, this.pkiUtility, this.certSerialCurator
        );
    }

    @AfterEach
    public void cleanup() {
        if (this.testFile != null) {
            this.testFile.delete();
        }
    }

    @Test
    public void testGetCurrentCrl() throws Exception {
        Object response = this.resource.getCurrentCrl();

        assertNotNull(response);
        verify(crlFileUtil).syncCRLWithDB(any(File.class));
    }

    @Test
    public void testGetCurrentCrlWithNoFile() throws Exception {
        this.cleanup();
        Object response = this.resource.getCurrentCrl();

        assertNotNull(response);
        verify(crlFileUtil).syncCRLWithDB(any(File.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUnrevokeWithArguments() throws Exception {
        List<String> input = Arrays.asList("123", "456", "789");

        CandlepinQuery<CertificateSerial> cqmock = mock(CandlepinQuery.class);
        List<CertificateSerial> serials = new LinkedList<>();
        serials.add(new CertificateSerial(123L));
        serials.add(new CertificateSerial(456L));
        serials.add(new CertificateSerial(789L));

        when(cqmock.iterator()).thenReturn(serials.iterator());
        when(this.certSerialCurator.listBySerialIds(eq(input))).thenReturn(cqmock);

        this.resource.unrevoke(input);

        verify(crlFileUtil).updateCRLFile(any(File.class), nullable(Collection.class),
            anyCollection());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUnrevokeWithNoArguments() {
        List<String> input = new ArrayList<>();

        CandlepinQuery<CertificateSerial> cqmock = mock(CandlepinQuery.class);
        List<CertificateSerial> serials = new LinkedList<>();

        when(cqmock.iterator()).thenReturn(serials.iterator());
        when(this.certSerialCurator.listBySerialIds(eq(input))).thenReturn(cqmock);

        this.resource.unrevoke(input);

        verifyNoMoreInteractions(crlFileUtil);
    }

}
