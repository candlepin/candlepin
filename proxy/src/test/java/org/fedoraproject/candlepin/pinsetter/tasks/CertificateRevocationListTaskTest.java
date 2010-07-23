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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.controller.CrlGenerator;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;

/**
 * CertificateRevocationListTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationListTaskTest {
    private CertificateRevocationListTask task;
     
    @Mock private CrlGenerator generator; 
    @Mock private Config config;

    @Before
    public void init() {
        this.task = new CertificateRevocationListTask(generator, config);
    }

    @Test(expected = JobExecutionException.class)
    public void executeNullFilePath() throws JobExecutionException {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(null);
        task.execute(null);
    }

    @Test(expected = JobExecutionException.class)
    public void executeGivenDirectory() throws JobExecutionException {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn("/tmp");
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        task.execute(ctx);
    }

    @Test
    public void executeGivenNonExistentFile() throws Exception {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(
            "/tmp/biteme.crl");
        X509CRL crl = mock(X509CRL.class);
        when(crl.getEncoded()).thenReturn(Base64.encode("encoded".getBytes()));
        when(generator.updateCRL(any(X509CRL.class))).thenReturn(crl);
        task.execute(null);
        File f = new File("/tmp/biteme.crl");
        assertTrue(f.exists());
        assertTrue(f.length() > 0);
        f.delete();
    }
    
    @Test
    public void emptyFile() throws IOException, JobExecutionException, CRLException {
        File f = null;
        try {
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(
                f.getAbsolutePath());
            X509CRL crl = mock(X509CRL.class);
            when(crl.getEncoded()).thenReturn(Base64.encode("encoded".getBytes()));
            when(generator.updateCRL(any(X509CRL.class))).thenReturn(crl);

            task.execute(null);
            assertTrue(f.length() > 0);
        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }
    
    @Test(expected = JobExecutionException.class)
    public void handleCRLException() throws Exception {
        File f = null;
        try {
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            // put some garbage in the file to cause the CRLException
            FileUtils.writeByteArrayToFile(f, "gobbledygook".getBytes());
            when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(
                f.getAbsolutePath());

            task.execute(null);
        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    @Test
    @Ignore("need to write a VALID CRL to the file first in order to verify")
    public void existingFile() throws Exception {
        File f = null;
        try {
            X509CRL crl = mock(X509CRL.class);
            f = File.createTempFile("test", ".crl");
            assertEquals(0, f.length());
            when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(
                f.getAbsolutePath());

            when(crl.getEncoded()).thenReturn(Base64.encode("encoded".getBytes()))
                .thenReturn(Base64.encode("added".getBytes()));
            when(generator.updateCRL(any(X509CRL.class))).thenReturn(crl);

            task.execute(null);
            long len = f.length();
            assertTrue(len > 0);
            task.execute(null);
            // make sure we added to the file
            assertTrue(f.length() > len);
        }
        finally {
            if (f != null) {
                f.delete();
            }
        }
    }
}
