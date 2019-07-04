/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.CrlFileUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;



/**
 * Test suite for the CRLUpdateJob class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CRLUpdateJobTest {

    private Configuration config;
    private CrlFileUtil crlFileUtil;

    @BeforeEach
    public void init() {
        this.config = new CandlepinCommonTestConfig();
        this.crlFileUtil = mock(CrlFileUtil.class);
    }

    private CRLUpdateJob createJobInstance() {
        return new CRLUpdateJob(this.config, this.crlFileUtil);
    }

    @Test
    public void executeFailsOnEmptyFilePath() throws JobExecutionException {
        this.config.setProperty(ConfigProperties.CRL_FILE_PATH, "");

        JobExecutionContext context = mock(JobExecutionContext.class);
        CRLUpdateJob job = this.createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    public void executeFailsOnAbsentFilePath() throws JobExecutionException {
        this.config.clearProperty(ConfigProperties.CRL_FILE_PATH);

        JobExecutionContext context = mock(JobExecutionContext.class);
        CRLUpdateJob job = this.createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    public void execute() throws Exception {
        String crlPath = "/tmp/test.crl";
        File crlFile = new File(crlPath);

        this.config.setProperty(ConfigProperties.CRL_FILE_PATH, crlPath);

        JobExecutionContext context = mock(JobExecutionContext.class);
        CRLUpdateJob job = this.createJobInstance();
        job.execute(context);

        verify(this.crlFileUtil).syncCRLWithDB(eq(crlFile));
    }

}
