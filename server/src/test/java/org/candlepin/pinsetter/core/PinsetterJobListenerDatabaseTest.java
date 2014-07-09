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
package org.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.quartz.JobKey.jobKey;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.jpa.JpaPersistModule;

import org.apache.commons.lang.RandomStringUtils;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.auth.Principal;
import org.candlepin.guice.I18nProvider;
import org.candlepin.guice.JPAInitializer;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.guice.TestPrincipalProvider;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

/**
 * PinsetterJobListenerDatabaseTest is a unit test that uses a real database to
 * test the PinsetterJobListener for database type failures not seen during
 * mock testing.
 */
public class PinsetterJobListenerDatabaseTest {
    protected Injector injector;
    protected UnitOfWork unitOfWork;
    private JobCurator curator;

    @Before
    public void init() {
        TestModule testingModule = new TestModule();
        injector = Guice.createInjector(testingModule,
            new CandlepinNonServletEnvironmentTestingModule());
        unitOfWork = injector.getInstance(UnitOfWork.class);
        curator = injector.getInstance(JobCurator.class);
    }

    @Test
    public void verifyDatabaseConstraintIsNotViolated() {
        PinsetterJobListener listener = injector.getInstance(PinsetterJobListener.class);
        JobExecutionException e = mock(JobExecutionException.class);
        String longstr = RandomStringUtils.randomAlphanumeric(300);
        when(e.getMessage()).thenReturn(longstr);

        JobDataMap map = new JobDataMap();
        Principal principal = mock(Principal.class);
        when(principal.getPrincipalName()).thenReturn("test-admin");
        map.put(PinsetterJobListener.PRINCIPAL_KEY, principal);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, "10");

        JobDetail detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(jobKey("name", "group"));
        when(detail.getJobDataMap()).thenReturn(map);

        JobStatus status = new JobStatus(detail);

        // store a merge so we can find it in the test run
        curator.merge(status);

        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(map);
        when(ctx.getJobDetail()).thenReturn(detail);

        // thing to be tested
        listener.jobWasExecuted(ctx, e);

        // verify the message stored is a substring of the long message
        JobStatus verify = curator.find("name");
        assertEquals(longstr.substring(0, JobStatus.RESULT_COL_LENGTH),
            verify.getResult());
    }

    public class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new JpaPersistModule("default"));
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(JPAInitializer.class).asEagerSingleton();
            bind(JobFactory.class).to(GuiceJobFactory.class);
            bind(JobListener.class).to(PinsetterJobListener.class);
            bind(PrincipalProvider.class).to(TestPrincipalProvider.class);
            bind(Principal.class).toProvider(TestPrincipalProvider.class);
        }
    }
}
