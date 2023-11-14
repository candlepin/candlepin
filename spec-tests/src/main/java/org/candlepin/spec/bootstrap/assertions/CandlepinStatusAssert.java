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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.invoker.client.ApiException;

import org.assertj.core.api.ThrowableAssert;

public class CandlepinStatusAssert extends ThrowableAssert<ApiException> {
    public CandlepinStatusAssert(ApiException throwable) {
        super(throwable);
    }

    public CandlepinStatusAssert isBadRequest() {
        this.hasFieldOrPropertyWithValue("code", 400);
        return this;
    }

    public CandlepinStatusAssert isUnauthorized() {
        this.hasFieldOrPropertyWithValue("code", 401);
        return this;
    }

    public CandlepinStatusAssert isForbidden() {
        this.hasFieldOrPropertyWithValue("code", 403);
        return this;
    }

    public CandlepinStatusAssert isNotFound() {
        this.hasFieldOrPropertyWithValue("code", 404);
        return this;
    }

    public CandlepinStatusAssert isGone() {
        this.hasFieldOrPropertyWithValue("code", 410);
        return this;
    }

    public CandlepinStatusAssert isTooMany() {
        this.hasFieldOrPropertyWithValue("code", 429);
        return this;
    }

    public CandlepinStatusAssert hasHeaderWithValue(String name, String value) {
        String time = this.actual.getResponseHeaders().get(name).get(0);
        assertEquals(value, time);
        return this;
    }

    public CandlepinStatusAssert hasHeaderWithValue(String name, int value) {
        String time = this.actual.getResponseHeaders().get(name).get(0);
        assertEquals(String.valueOf(value), time);
        return this;
    }
}
