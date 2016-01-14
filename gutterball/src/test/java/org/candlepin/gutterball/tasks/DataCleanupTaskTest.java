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
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.EventCurator;

import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;



/**
 * DataCleanupTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DataCleanupTaskTest {

    private Configuration config;

    @Mock private ComplianceSnapshotCurator complianceCurator;
    @Mock private EventCurator eventCurator;
    @Mock private UnitOfWork uow;

    @Before
    public void init() {
        this.config = new MapConfiguration();

        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_ENABLED, "true");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_SCHEDULE, "0 13 * * *");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "24");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours");
    }

    @Test
    public void testConfigureDataCleanupTask() throws Exception {
        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        // Nothing to do aside from constructing the object
    }

    @Test(expected = ConfigurationException.class)
    public void testConfigureDataCleanupTaskBadAgeValues() throws Exception {
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "2147483647");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );
    }

    @Test
    public void testExecuteDataCleanupTaskDefaultSettings() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "24");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours");

        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.complianceCurator).cleanupCompliances(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteDataCleanupTaskWithDifferentUnits() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "1");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.complianceCurator).cleanupCompliances(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteDataCleanupTaskWithInvalidUnits() throws Exception {
        int minutes = 1;

        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "60");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "nopes");

        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.complianceCurator).cleanupCompliances(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testExecuteDataCleanupTaskHandlesExceptionsGracefully() throws Exception {
        int minutes = 1440;

        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, "1");
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");

        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );
        when(this.eventCurator.cleanupEvents(anyInt())).thenThrow(new RuntimeException());

        task.run();
        verify(this.uow).begin();
        verify(this.eventCurator).cleanupEvents(eq(minutes));
        verify(this.complianceCurator).cleanupCompliances(eq(minutes));
        verify(this.uow).end();
    }

    @Test
    public void testShutdownDataCleanupTask() throws Exception {
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_ENABLED, "true");
        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        task.shutdown();
    }

    @Test
    public void testShutdownDataCleanupTaskWhileDisabled() throws Exception {
        this.config.setProperty(ConfigProperties.DATA_CLEANUP_TASK_ENABLED, "false");
        DataCleanupTask task = new DataCleanupTask(
            this.config, this.complianceCurator, this.eventCurator, this.uow
        );

        task.shutdown();
    }
}
