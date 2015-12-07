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

package org.candlepin.gutterball.tasks;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.curator.EventCurator;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;



/**
 * EventCleanupTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EventCleanupTaskTest {

    private Configuration config;

    @Mock private EventCurator eventCurator;
    @Mock private UnitOfWork uow;

    @Before
    public void init() {
        this.config = new MapConfiguration();
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_ENABLED, "true");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL, "24");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL_UNIT, "hours");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "24");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours");
    }

    @Test
    public void testConfigureEventCleanupTask() throws Exception {
        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);
        // Nothing to do aside from constructing the object
    }

    @Test(expected = ConfigurationException.class)
    public void testConfigureEventCleanupTaskBadAgeValues() throws Exception {
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "2147483647");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);
    }

    @Test
    public void testExecuteEventCleanupTaskDefaultSettings() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "24");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours");

        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteEventCleanupTaskWithDifferentUnits() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "1");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteEventCleanupTaskWithInvalidUnits() throws Exception {
        int minutes = 1;

        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "60");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "nopes");

        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteEventCleanupTaskHandlesExceptionsGracefully() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, "1");
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);
        when(this.eventCurator.cleanupEvents(anyInt())).thenThrow(new RuntimeException());

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testShutdownEventCleanupTask() throws Exception {
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_ENABLED, "true");
        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);

        task.shutdown();
    }

    @Test
    public void testShutdownEventCleanupTaskWhileDisabled() throws Exception {
        this.config.setProperty(ConfigProperties.EVENT_CLEANUP_TASK_ENABLED, "false");
        EventCleanupTask task = new EventCleanupTask(this.config, this.eventCurator, this.uow);

        task.shutdown();
    }
}
