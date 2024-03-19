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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.TestConfig;
import org.candlepin.model.Owner;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;



public class EventAdapterTest {
    private I18n i18n;

    @BeforeEach
    public void init() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void toFeed() {
        EventAdapter ea = new EventAdapterImpl(TestConfig.defaults(), i18n);
        List<Event> events = new LinkedList<>();
        events.add(mockEvent(Event.Target.CONSUMER, Event.Type.CREATED));
        events.add(mockEvent(Event.Target.ENTITLEMENT, Event.Type.DELETED));
        Feed f = ea.toFeed(events, "/test/path");
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertFalse(f.getEntries().isEmpty());
        assertEquals(2, f.getEntries().size());
        Entry e = f.getEntries().get(0);
        assertNotNull(e);
        assertNotNull(e.getTitle());
        assertTrue(e.getTitle().contains("CONSUMER"));
        assertTrue(e.getTitle().contains("CREATED"));
        assertTrue(e.getSummaryElement().getText().contains("unit"));
        assertTrue(e.getSummaryElement().getText().contains("created"));
        assertEquals(events.get(0).getTimestamp(), f.getUpdated());
    }

    private Event mockEvent(Event.Target target, Event.Type type) {
        Owner owner = TestUtil.createOwner();
        UserPrincipal principal = TestUtil.createOwnerPrincipal(owner);
        return new Event(type, target, principal.getData());
    }

    @Test
    public void nullList() {
        EventAdapter ea = new EventAdapterImpl(TestConfig.defaults(), i18n);
        Feed f = ea.toFeed(null, null);
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }

    @Test
    public void emptyList() {
        EventAdapter ea = new EventAdapterImpl(TestConfig.defaults(), i18n);
        Feed f = ea.toFeed(new LinkedList<>(), "/some/path");
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }

}
