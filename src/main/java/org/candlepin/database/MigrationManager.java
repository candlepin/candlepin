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

import static org.candlepin.config.ConfigProperties.DB_MANAGE_ON_START;

import org.candlepin.config.Configuration;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.command.CommandScope;
import liquibase.command.core.StatusCommandStep;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.exception.LiquibaseException;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for managing database migrations.
 */
public class MigrationManager {
    private static Logger log = LoggerFactory.getLogger(MigrationManager.class);

    private static final String CHANGELOG_FILE_NAME = "db/changelog/changelog-update.xml";

    private Configuration config;
    private DatabaseConnectionManager connectionManager;

    public MigrationManager(Configuration config, DatabaseConnectionManager connectionManager) {
        this.config = Objects.requireNonNull(config);
        this.connectionManager = Objects.requireNonNull(connectionManager);
    }

    /**
     * Runs the liquibase changesets based on the manage on startup configuration value.
     *
     * @throws RuntimeException
     *  if unable to determine the migration level
     *
     * @throws LiquibaseException
     *  if unable to run the migrations
     */
    public void migrate() throws LiquibaseException {
        String migrationConfig = config.getString(DB_MANAGE_ON_START);
        log.info("Liquibase startup management set to {}", migrationConfig);
        MigrationManagementLevel migrationLevel = null;
        try {
            migrationLevel = MigrationManagementLevel.valueOf(migrationConfig.toUpperCase());
        }
        catch (IllegalArgumentException iae) {
            log.error("The value {} of parameter '{}' is not allowed", migrationConfig, DB_MANAGE_ON_START);
            throw new RuntimeException(iae.getMessage());
        }

        if (MigrationManagementLevel.NONE.equals(migrationLevel)) {
            return;
        }

        Database database = connectionManager.getDatabase();
        List<ChangeSet> unrunChangeSets = getUnrunChangeSets(database);
        if (unrunChangeSets.isEmpty()) {
            log.info("Candlepin database is up to date!");
        }
        else {
            Stream<String> csStream = unrunChangeSets.stream()
                .map(changeset ->
                String.format("file: %s, changeset: %s", changeset.getFilePath(), changeset.getId()));

            switch (migrationLevel) {
                case REPORT:
                    log.warn("Database has {} unrun changeset(s): \n{}", unrunChangeSets.size(),
                        csStream.collect(Collectors.joining("\n  ", "  ", "")));
                    break;
                case HALT:
                    log.error("Database has {} unrun changeset(s); halting startup...\n{}",
                        unrunChangeSets.size(), csStream.collect(Collectors.joining("\n  ", "  ", "")));
                    throw new RuntimeException("The database is missing Liquibase changeset(s)");
                case MANAGE:
                    log.info("Calling liquibase to update the database");
                    log.info("Database has {} unrun changeset(s): \n{}", unrunChangeSets.size(),
                        csStream.collect(Collectors.joining("\n  ", "  ", "")));
                    executeUpdate(database);
                    log.info("Update complete");
                    break;
                default:
                    throw new RuntimeException("Cannot determine database management mode.");
            }
        }
    }

    /**
     * Reads the list of unrun changesets from the database supplied based on the changelog.
     *
     * @param database
     *  used to verify if the changelog changeset has been applied or not
     *
     * @throws LiquibaseException
     *  if there is an issue with reading the changesets
     *
     * @return List of unrun changesets
     */
    protected List<ChangeSet> getUnrunChangeSets(Database database) throws LiquibaseException {
        try {
            DatabaseChangeLog changeLog = new XMLChangeLogSAXParser()
                .parse(CHANGELOG_FILE_NAME, new ChangeLogParameters(), new ClassLoaderResourceAccessor());

            return new StatusCommandStep()
                .listUnrunChangeSets(null, null, changeLog, database);
        }
        catch (Exception e) {
            throw new LiquibaseException(e.getMessage());
        }
    }

    protected void executeUpdate(Database database) throws LiquibaseException {
        CommandScope commandScope = new CommandScope(UpdateCommandStep.COMMAND_NAME)
            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_FILE_NAME);
        commandScope.execute();
    }
}
