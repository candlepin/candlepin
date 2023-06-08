/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class RegenEnvEntitlementCertsJobTest {

    @Mock private Owner owner;
    @Mock private PoolManager poolManager;
    private Environment environment;
    private Set<String> content;
    private RegenEnvEntitlementCertsJob job;

    @BeforeEach
    public void init() {
        environment = new Environment();
        environment.setId("env_id_1");
        content = new HashSet<>();
        content.add("cont_id_1");
        job = new RegenEnvEntitlementCertsJob(poolManager);
    }

    @Test
    public void validateJobConfig() {
        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setOwner(owner)
            .setEnvironment(environment)
            .setLazyRegeneration(true)
            .setContent(content);

        assertDoesNotThrow(config::validate);
    }

    @Test
    public void ownerAndLazyRegenNotRequired() {
        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setEnvironment(environment)
            .setContent(content);

        assertDoesNotThrow(config::validate);
    }

    @Test
    public void environmentMustBePresent() {
        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(true)
            .setContent(content);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void contentMustBePresent() {
        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setOwner(owner)
            .setEnvironment(environment)
            .setLazyRegeneration(true);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void ensureJobSuccess() {
        JobConfig jobConfig = RegenEnvEntitlementCertsJob.createJobConfig()
            .setEnvironment(environment)
            .setContent(content);

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        this.job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        verify(poolManager).regenerateCertificatesOf(environment.getId(), content, true);
        assertEquals("Successfully regenerated entitlements for environment: " + environment.getId(), result);
    }
}
