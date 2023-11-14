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

import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.request.Request;

import java.util.HashMap;
import java.util.Map;

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
    private static final Map<String, Boolean> EXTENSION_MAP = new HashMap<>();

    static {
        try {
            Boolean standalone = ApiClients.admin()
                .status()
                .status()
                .getStandalone();

            if (standalone == null) {
                throw new ApiException("Standalone flag not set in the status output");
            }

            IS_STANDALONE = standalone;
        }
        catch (ApiException e) {
            throw new IllegalStateException("Unable to determine Candlepin's deployment mode!", e);
        }
    }

    public static boolean isStandalone() {
        return IS_STANDALONE;
    }

    public static boolean isHosted() {
        return !IS_STANDALONE;
    }

    public static boolean hasTestExtension(String extension) {
        return EXTENSION_MAP.computeIfAbsent(extension, (ext) -> {
            // Impl note: hostedtest predates the "testext" prefix, so we handle it differently here
            String endpoint = "hostedtest".equals(extension) ?
                "/hostedtest/alive" :
                "/testext/" + extension + "/alive";

            int code = Request.from(ApiClients.admin())
                .setPath(endpoint)
                .execute()
                .getCode();

            return code == 200;
        });
    }

    public static boolean hasHostedTestExtension() {
        return hasTestExtension("hostedtest");
    }

    public static boolean hasManifestGenTestExtension() {
        return hasTestExtension("manifestgen");
    }

}
