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
import org.candlepin.spec.bootstrap.client.request.Response;

import java.nio.charset.StandardCharsets;

public class ApiExceptionAdapter extends ApiException {

    private final Response response;

    public ApiExceptionAdapter(Response response) {
        this.response = response;
    }

    @Override
    public int getCode() {
        return this.response.getCode();
    }

    @Override
    public String getMessage() {
        return new String(this.response.getBody(), StandardCharsets.UTF_8);
    }
}
