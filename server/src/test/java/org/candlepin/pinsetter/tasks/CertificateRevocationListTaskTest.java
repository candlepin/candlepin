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
package org.candlepin.pinsetter.tasks;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;


import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CrlGenerator;
import org.candlepin.util.CrlFileUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobExecutionException;

import java.io.File;
import java.security.cert.X509CRL;

/**
 * CertificateRevocationListTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationListTaskTest {
    private CertificateRevocationListTask task;

    @Mock private Config config;
    @Mock private CrlFileUtil crlFileUtil;
    @Mock private CrlGenerator generator;
    @Mock private X509CRL crl;

    @Before
    public void init() {
        this.task = new CertificateRevocationListTask(config, crlFileUtil, generator);
    }

    @Test(expected = JobExecutionException.class)
    public void executeNullFilePath() throws JobExecutionException {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(null);
        task.execute(null);
    }

    @Test
    public void execute() throws Exception {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn("/tmp/test.crl");
        when(crlFileUtil.readCRLFile(any(File.class))).thenReturn(crl);
        when(generator.syncCRLWithDB(eq(crl))).thenReturn(crl);

        task.execute(null);

        verify(crlFileUtil).writeCRLFile(any(File.class), eq(crl));
    }

}
