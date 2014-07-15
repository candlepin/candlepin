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
package org.candlepin.resource.test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CrlGenerator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.resource.CrlResource;
import org.candlepin.util.CrlFileUtil;
import org.junit.Test;

import java.io.File;
import java.security.cert.X509CRL;
import java.util.HashMap;
import java.util.List;

/**
 * CrlResourceTest
 */
public class CrlResourceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void unrevoke() throws Exception {
        CrlGenerator crlgen = mock(CrlGenerator.class);
        CrlFileUtil fileutil = mock(CrlFileUtil.class);
        CertificateSerialCurator sercur = mock(CertificateSerialCurator.class);
        Config config = new ConfigForTesting();
        X509CRL crl = mock(X509CRL.class);
        when(fileutil.readCRLFile(any(File.class))).thenReturn(crl);
        when(crlgen.removeEntries(eq(crl), any(List.class))).thenReturn(crl);

        CrlResource res = new CrlResource(crlgen, fileutil, config, sercur);
        String[] ids = {"10"};
        res.unrevoke(ids);
        verify(crlgen, atLeastOnce()).removeEntries(eq(crl), any(List.class));
        verify(fileutil, atLeastOnce()).writeCRLFile(any(File.class), eq(crl));
    }

    private static class ConfigForTesting extends Config {
        public ConfigForTesting() {
            super(new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;
                {
                    this.put(ConfigProperties.CRL_FILE_PATH, "/tmp/test-crl.crl");
                }
            });
        }
    }
}
