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
package org.candlepin.gutterball.db;

import org.candlepin.gutterball.DatabaseTestFixture;

import org.junit.Test;

import java.util.Properties;

import javax.persistence.Persistence;

/**
 * The purpose of this test is to simply verify that the database schema is
 * valid according to hibernate after the schema is loaded by liquibase.
 */
public class DatabaseSchemaValidationTest extends DatabaseTestFixture {

    @Test
    public void testSchemaValidation() {
        // Setting the JPA module to validate the schema below. The test sets up the
        // entity manager which will cause a failure if the schema is invalid.
        //
        // NOTE: If an exception is not thrown while the entity manager is being
        //       created, we know it is valid.
        Properties overrides = new Properties();
        overrides.setProperty("hibernate.hbm2ddl.auto", "validate");
        Persistence.createEntityManagerFactory("testing", overrides);
    }

}
