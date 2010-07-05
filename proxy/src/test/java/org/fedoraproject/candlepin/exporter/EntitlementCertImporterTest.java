/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.exporter;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * EntitlementCertImporterTest
 */
public class EntitlementCertImporterTest {

    private EntitlementCertImporter importer;
    private ObjectMapper mapper;
    private FileReader certFileReader;

    @Before
    public void setUp() throws FileNotFoundException {
        importer = new EntitlementCertImporter();
        certFileReader = 
            new FileReader(getClass().getResource("entitlement_cert.pem").getFile());
    }
    
    @Ignore
    @Test
    public void shouldSetKeyAndCertificateFields() throws IOException {
        EntitlementCertificate cert = importer.createObject(mapper, certFileReader);
        
        assertTrue(cert.getCert().startsWith(PKIUtility.BEGIN_CERTIFICATE));
        assertTrue(cert.getCert().endsWith(PKIUtility.END_CERTIFICATE));
        assertTrue(cert.getKey().startsWith(PKIUtility.BEGIN_KEY));
        assertTrue(cert.getKey().endsWith(PKIUtility.END_KEY));
    }
    
    @Ignore
    @Test
    public void shoudSetCorrectSerialNumber() throws IOException {
        EntitlementCertificate cert = importer.createObject(mapper, certFileReader);
        assertEquals(new BigInteger("2"), cert.getSerial());
    }
}
