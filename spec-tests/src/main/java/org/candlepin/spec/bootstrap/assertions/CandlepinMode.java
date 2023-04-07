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
package org.candlepin.spec.bootstrap.assertions;

import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClients;

/**
 * Condition used to determine the mode in which Candlepin is running.
 *
 * Check is done only once and cached for the whole spec test run.
 */
@SuppressWarnings("unused")
public final class CandlepinMode {

    private CandlepinMode() {
        throw new UnsupportedOperationException();
    }

    private static final boolean IS_STANDALONE;

    static {
        StatusDTO status;
        try {
            status = ApiClients.admin().status().status();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Unable to determine Candlepin's deployment mode!", e);
        }
        Boolean standalone = status.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("Unable to determine Candlepin's deployment mode!");
        }
        IS_STANDALONE = standalone;
    }

    public static boolean isHosted() {
        return !IS_STANDALONE;
    }

    public static boolean isStandalone() {
        return IS_STANDALONE;
    }

}
