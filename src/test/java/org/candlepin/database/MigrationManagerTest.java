/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.database;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.test.TestUtil;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MigrationManagerTest {
    @Mock
    private DatabaseConnectionManager connectionManager;
    @Mock
    private Database database;

    private DevConfig config;

    @BeforeEach
    public void beforeEach() {
        config = TestConfig.defaults();
    }

    @Test
    public void testMigrateWithUnknownMigrationLevel() {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, TestUtil.randomString());

        MigrationManager migrationManager = new MigrationManager(config, connectionManager);

        assertThrows(RuntimeException.class, () -> migrationManager.migrate());
    }

    @Test
    public void testMigrateWithNoneMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.NONE.toString());
        doReturn(database).when(connectionManager).getDatabase();

        MigrationManager migrationManager = spy(new MigrationManager(config, connectionManager));

        migrationManager.migrate();

        verify(migrationManager, never()).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);
    }

    @Test
    public void testMigrateWithReportMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.REPORT.toString());
        doReturn(database).when(connectionManager).getDatabase();

        MigrationManager migrationManager = spy(new MigrationManager(config, connectionManager) {
            @Override
            protected List<ChangeSet> getUnrunChangeSets(Database database) {
                ChangeSet changeSet = new ChangeSet("21220101",
                    "tester",
                    true,
                    true,
                    "db/changelog/21220101-test.xml",
                    null,
                    null,
                    Mockito.mock(DatabaseChangeLog.class));

                return List.of(changeSet);
            }
        });

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);
    }

    @Test
    public void testMigrateWithHaltMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.toString());
        doReturn(database).when(connectionManager).getDatabase();

        MigrationManager migrationManager = spy(new MigrationManager(config, connectionManager) {
            @Override
            protected List<ChangeSet> getUnrunChangeSets(Database database) {
                ChangeSet changeSet = new ChangeSet("21220101",
                    "tester",
                    true,
                    true,
                    "db/changelog/21220101-test.xml",
                    null,
                    null,
                    Mockito.mock(DatabaseChangeLog.class));

                return List.of(changeSet);
            }
        });

        assertThrows(RuntimeException.class, () -> migrationManager.migrate());
    }

    @Test
    public void testMigrateWithManageMigrationLevelAndNoUnrunChanges() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.MANAGE.toString());
        doReturn(database).when(connectionManager).getDatabase();

        MigrationManager migrationManager = spy(new MigrationManager(config, connectionManager) {
            @Override
            protected List<ChangeSet> getUnrunChangeSets(Database database) {
                return List.of();
            }

            @Override
            protected void executeUpdate(Database database) {
                // Intentionally left blank
            }
        });

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);
    }

    @Test
    public void testMigrateWithManageMigrationLevelAndUnrunChanges() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.MANAGE.toString());
        doReturn(database).when(connectionManager).getDatabase();

        MigrationManager migrationManager = spy(new MigrationManager(config, connectionManager) {
            @Override
            protected List<ChangeSet> getUnrunChangeSets(Database database) {
                ChangeSet changeSet = new ChangeSet("21220101",
                    "tester",
                    true,
                    true,
                    "db/changelog/21220101-test.xml",
                    null,
                    null,
                    Mockito.mock(DatabaseChangeLog.class));

                return List.of(changeSet);
            }

            @Override
            protected void executeUpdate(Database database) {
                // Intentionally left blank
            }
        });

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager).executeUpdate(database);
    }
}
