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
package org.candlepin.audit;

import org.candlepin.model.Consumer;

/**
 * ConsumerEventBuilder Allows us to easily build a consumer modified
 * event one piece at a time.
 *
 * TODO: expand this to work for more types
 */
public class ConsumerEventBuilder {

    private EventFactory eventFactory;

    private String id;
    private String ownerId;
    private String name;

    private String oldJson;
    private String newJson;

    public ConsumerEventBuilder(EventFactory eventFactory) {
        this.eventFactory = eventFactory;
    }

    public ConsumerEventBuilder setOldConsumer(Consumer old) {
        name = old.getName();
        ownerId = old.getOwner().getId();
        id = old.getId();
        oldJson = eventFactory.entityToJson(old);
        return this;
    }

    public ConsumerEventBuilder setNewConsumer(Consumer updated) {
        name = updated.getName();
        ownerId = updated.getOwner().getId();
        id = updated.getId();
        newJson = eventFactory.entityToJson(updated);
        return this;
    }

    public Event buildEvent() {
        return new Event(Event.Type.MODIFIED, Event.Target.CONSUMER,
                name, eventFactory.principalProvider.get(),
                ownerId, id, id, oldJson, newJson, null, null);
    }
}
