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
package org.candlepin.audit;

import com.google.inject.ImplementedBy;

import org.jboss.resteasy.plugins.providers.atom.Feed;

import java.util.List;

/**
 * EventAdapter
 */
@ImplementedBy(EventAdapterImpl.class)
public interface EventAdapter {

    Feed toFeed(List<Event> events, String path);
    void addMessageText(List<Event> events);
}
