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
package org.candlepin.config;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Factory class that returns correct database configuration given a database type.
 */
public class DatabaseConfigFactory {
    public static final String IN_OPERATOR_BLOCK_SIZE = "db.config.in.operator.block.size";
    public static final String CASE_OPERATOR_BLOCK_SIZE = "db.config.case.operator.block.size";
    public static final String BATCH_BLOCK_SIZE = "db.config.batch.block.size";
    public static final String QUERY_PARAMETER_LIMIT = "db.config.query.parameter.limit";

    public static final Map<String, String> POSTGRESQL_CONFIG = ImmutableMap.of(
        // Based on testing with the hypervisor check in process, and going a bit conservative
        IN_OPERATOR_BLOCK_SIZE, "15000",
        CASE_OPERATOR_BLOCK_SIZE, "100",
        BATCH_BLOCK_SIZE, "500",
        QUERY_PARAMETER_LIMIT, "32000"
    );

    public static final Map<String, String> MYSQL_CONFIG = ImmutableMap.of(
        // The limit is based on element size instead of cardinality.  We'll go with 15000 to be conservative.
        // See http://stackoverflow.com/questions/1532366
        IN_OPERATOR_BLOCK_SIZE, "15000",
        CASE_OPERATOR_BLOCK_SIZE, "100",
        BATCH_BLOCK_SIZE, "500",
        QUERY_PARAMETER_LIMIT, "32000"
    );

    private DatabaseConfigFactory() {
        // Utility class
    }

    /**
     * Enum containing the databases we support and the relevant configurations for them.
     */
    public enum SupportedDatabase {
        POSTGRESQL("postgresql", POSTGRESQL_CONFIG),
        MYSQL("mysql", MYSQL_CONFIG),
        MARIADB("mariadb", MYSQL_CONFIG);

        private final String label;
        private final Map<String, String> settings;

        SupportedDatabase(String label, Map<String, String> settings) {
            this.label = label;
            this.settings = settings;
        }

        public String getLabel() {
            return label;
        }

        public Map<String, String> getSettings() {
            return settings;
        }

        @Override
        public String toString() {
            return getLabel();
        }
    }

}
