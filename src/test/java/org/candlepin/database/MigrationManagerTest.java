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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.liquibase.LiquibaseConnectionGenerator;
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
    private LiquibaseConnectionGenerator connectionGenerator;

    @Mock
    private Database database;

    private DevConfig config;

    @BeforeEach
    public void beforeEach() throws Exception {
        this.config = TestConfig.defaults();

        doReturn(this.database).when(this.connectionGenerator).getDatabase();
    }

    private MigrationManager buildMigrationManager() {
        return new MigrationManager(this.config, this.connectionGenerator);
    }

    private ChangeSet generateChangeSet() {
        String id = TestUtil.randomString(14, TestUtil.CHARSET_NUMERIC);

        return new ChangeSet(id, "test_changeset", true, true, "db/changelog/" + id + "-test.xml", null, null,
            Mockito.mock(DatabaseChangeLog.class));
    }

    @Test
    public void testMigrateWithUnknownMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, TestUtil.randomString());

        MigrationManager migrationManager = this.buildMigrationManager();

        assertThrows(RuntimeException.class, () -> migrationManager.migrate());

        // verify we never even open a connection that has to be closed later
        verify(this.connectionGenerator, never()).getDatabase();
    }

    @Test
    public void testMigrateWithNoneMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.NONE.toString());

        MigrationManager migrationManager = spy(this.buildMigrationManager());

        migrationManager.migrate();

        verify(migrationManager, never()).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);

        // verify we never even open a connection that has to be closed later
        verify(this.connectionGenerator, never()).getDatabase();
    }

    @Test
    public void testMigrateWithReportMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.REPORT.toString());

        MigrationManager migrationManager = spy(this.buildMigrationManager());
        doReturn(List.of(this.generateChangeSet()))
            .when(migrationManager)
            .getUnrunChangeSets(any(Database.class));

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);

        // Verify we only open a single connection and closed it properly
        verify(this.connectionGenerator, times(1)).getDatabase();
        verify(this.database, atLeastOnce()).close();
    }

    @Test
    public void testMigrateWithHaltMigrationLevel() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.toString());

        MigrationManager migrationManager = spy(this.buildMigrationManager());
        doReturn(List.of(this.generateChangeSet()))
            .when(migrationManager)
            .getUnrunChangeSets(any(Database.class));

        assertThrows(RuntimeException.class, () -> migrationManager.migrate());

        // Verify we only open a single connection and closed it properly
        verify(this.connectionGenerator, times(1)).getDatabase();
        verify(this.database, atLeastOnce()).close();
    }

    @Test
    public void testMigrateWithManageMigrationLevelAndNoUnrunChanges() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.MANAGE.toString());

        MigrationManager migrationManager = spy(this.buildMigrationManager());
        doReturn(List.of())
            .when(migrationManager)
            .getUnrunChangeSets(any(Database.class));
        doNothing()
            .when(migrationManager)
            .executeUpdate(any(Database.class));

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager, never()).executeUpdate(database);

        // Verify we only open a single connection and closed it properly
        verify(this.connectionGenerator, times(1)).getDatabase();
        verify(this.database, atLeastOnce()).close();
    }

    @Test
    public void testMigrateWithManageMigrationLevelAndUnrunChanges() throws Exception {
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.MANAGE.toString());

        MigrationManager migrationManager = spy(this.buildMigrationManager());
        doReturn(List.of(this.generateChangeSet()))
            .when(migrationManager)
            .getUnrunChangeSets(any(Database.class));
        doNothing()
            .when(migrationManager)
            .executeUpdate(any(Database.class));

        migrationManager.migrate();

        verify(migrationManager).getUnrunChangeSets(database);
        verify(migrationManager).executeUpdate(database);

        // Verify we only open a single connection and closed it properly
        verify(this.connectionGenerator, times(1)).getDatabase();
        verify(this.database, atLeastOnce()).close();
    }
}
