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
package org.candlepin.liquibase;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * The MultiOrgUpgradeLiquibaseWrapper class wraps the MultiOrgUpgradeTask to allow it to be
 * programatically performed via Liquibase.
 */
public class MultiOrgUpgradeLiquibaseWrapper implements CustomTaskChange {

    @Override
    public String getConfirmationMessage() {
        return null;
    }

    @Override
    public void setFileOpener(ResourceAccessor accessor) {
        // Do nothing
    }

    @Override
    public void setUp() throws SetupException {
        // Do nothing
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        MultiOrgUpgradeTask task = new MultiOrgUpgradeTask(
            database,
            new LiquibaseCustomTaskLogger()
        );

        try {
            task.execute();
        }
        catch (Exception e) {
            throw new CustomChangeException(e);
        }
    }
}
