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

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.controller.CRLGenerator;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobExecutionException;

/**
 * CertificateRevocationListTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationListTaskTest{
    private CertificateRevocationListTask task;
     
    @Mock private CRLGenerator generator; 
    @Mock private Config config;

    @Before
    public void init() {
        this.task = new CertificateRevocationListTask(generator, config);
    }

    @Test
    @Ignore("This is not really asserting anything - not sure what it should do")
    public void testUpdateURLStreams() throws JobExecutionException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String principal = "CN=test, UID=" + UUID.randomUUID();
        this.task.updateCRL(null, principal, out);
    }
    
}
