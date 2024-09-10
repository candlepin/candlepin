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
package org.candlepin.service.impl;

import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.AdapterEvent;

public class DefaultEventAdapter implements EventAdapter {

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        // Intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        // Intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(AdapterEvent event) {
        // Intentionally left blank
    }

}
