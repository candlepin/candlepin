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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.CrlFileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobExecutionException;

import java.io.File;



/**
 * CertificateRevocationListTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationListTaskTest {
    private CertificateRevocationListTask task;

    @Mock private Configuration config;
    @Mock private CrlFileUtil crlFileUtil;

    @Before
    public void init() {
        this.task = new CertificateRevocationListTask(config, crlFileUtil);
    }

    @Test(expected = JobExecutionException.class)
    public void executeNullFilePath() throws JobExecutionException {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn(null);
        task.execute(null);
    }

    @Test
    public void execute() throws Exception {
        when(config.getString(ConfigProperties.CRL_FILE_PATH)).thenReturn("/tmp/test.crl");
        when(crlFileUtil.syncCRLWithDB(any(File.class))).thenReturn(true);

        task.execute(null);

        verify(crlFileUtil).syncCRLWithDB(any(File.class));
    }

}
