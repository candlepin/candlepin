/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.audit;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.PrincipalData;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.jboss.resteasy.plugins.providers.atom.Content;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.atom.Link;
import org.jboss.resteasy.plugins.providers.atom.Person;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * EventAdapterImpl
 */
public class EventAdapterImpl implements EventAdapter {

    private static final HashMap<String, String> MESSAGES;

    private I18n i18n;
    private Config config;

    @Inject
    public EventAdapterImpl(Config config, I18n i18n) {
        this.config = config;
        this.i18n = i18n;
    }

    @Override
    public Feed toFeed(List<Event> events, String path) {
        String url =  config.getString(ConfigProperties.CANDLEPIN_URL) + path + "/";
        Feed feed = new Feed();
        feed.setUpdated(new Date());
        feed.getAuthors().add(new Person("Red Hat, Inc."));
        try {
            feed.setId(new URI(url));
        }
        catch (Exception e) {
            // ignore, shouldn't happen
        }

        if (events == null) {
            return feed;
        }

        // Add the friendly message text
        this.addMessageText(events);

        for (Event e : events) {
            Entry entry = new Entry();
            entry.setTitle(e.getMessageText());
            entry.setPublished(e.getTimestamp());
            entry.setUpdated(e.getTimestamp());
            entry.getAuthors().add(new Person("Red Hat, Inc."));
            URI eventURI = null;
            try {
                eventURI = new URI(url + e.getId());
            }
            catch (Exception error) {
                // ignore, shouldn't happen
            }
            entry.setId(eventURI);
            entry.getLinks().add(
                new Link(
                    "alternate",
                    eventURI,
                    MediaType.APPLICATION_JSON_TYPE));

            Content content = new Content();
            content.setType(MediaType.APPLICATION_XML_TYPE);
            content.setJAXBObject(e);
            entry.setContent(content);
            entry.setSummary(e.getMessageText());
            feed.getEntries().add(entry);
        }
        // Use the most recent event as the feed's published time. Assumes events do not
        // get modified, if they do then the feed published date could be inaccurate.
        if (events.size() > 0) {
            feed.setUpdated(events.get(0).getTimestamp());
        }

        return feed;
    }

    public void addMessageText(List<Event> events) {
        for (Event event : events) {
            String eventType = (event.getTarget().name() + event.getType().name());
            String message = MESSAGES.get(eventType);
            if (message == null) {
                message = i18n.tr("Unknown event for user {0} and target {1}");
            }
            PrincipalData pd = event.getPrincipal();
            event.setMessageText(i18n.tr(message,
                pd.getName(),
                event.getTargetName()));
        }
    }

    //TODO: Make them nicer strings if the system did it
    static {
        MESSAGES = new HashMap<String, String>();
        MESSAGES.put("CONSUMERCREATED", I18n.marktr("{0} created new consumer {1}"));
        MESSAGES.put("CONSUMERMODIFIED", I18n.marktr("{0} modified the consumer {1}"));
        MESSAGES.put("CONSUMERDELETED", I18n.marktr("{0} deleted the consumer {1}"));
        MESSAGES.put("OWNERCREATED", I18n.marktr("{0} created new owner {1}"));
        MESSAGES.put("OWNERMODIFIED", I18n.marktr("{0} modified the owner {1}"));
        MESSAGES.put("OWNERDELETED", I18n.marktr("{0} deleted the owner {1}"));
        MESSAGES.put("ENTITLEMENTCREATED",
            I18n.marktr("{0} consumed a subscription for product {1}"));
        MESSAGES.put("ENTITLEMENTMODIFIED",
            I18n.marktr("{0} modified a subscription for product {1}"));
        MESSAGES.put("ENTITLEMENTDELETED",
            I18n.marktr("{0} returned the subscription for {1}"));
        MESSAGES.put("POOLCREATED", I18n.marktr("{0} created a pool for product {1}"));
        MESSAGES.put("POOLMODIFIED", I18n.marktr("{0} modified a pool for product {1}"));
        MESSAGES.put("POOLDELETED", I18n.marktr("{0} deleted a pool for product {1}"));
        MESSAGES.put("EXPORTCREATED",
            I18n.marktr("{0} created an export for consumer {1}"));
        MESSAGES.put("IMPORTCREATED", I18n.marktr("{0} imported a manifest for owner {1}"));
        MESSAGES.put("USERCREATED", I18n.marktr("{0} created new user {1}"));
        MESSAGES.put("USERMODIFIED", I18n.marktr("{0} modified the user {1}"));
        MESSAGES.put("USERDELETED", I18n.marktr("{0} deleted the user {1}"));
        MESSAGES.put("ROLECREATED", I18n.marktr("{0} created new role {1}"));
        MESSAGES.put("ROLEMODIFIED", I18n.marktr("{0} modified the role {1}"));
        MESSAGES.put("ROLEDELETED", I18n.marktr("{0} deleted the role {1}"));
        MESSAGES.put("SUBSCRIPTIONCREATED",
            I18n.marktr("{0} created new subscription for product {1}"));
        MESSAGES.put("SUBSCRIPTIONMODIFIED",
            I18n.marktr("{0} modified a subscription for product {1}"));
        MESSAGES.put("SUBSCRIPTIONDELETED",
            I18n.marktr("{0} deleted a subscription for product {1}"));
        MESSAGES.put("ACTIVATIONKEYCREATED",
            I18n.marktr("{0} created the activation key {1}"));
        MESSAGES.put("ACTIVATIONKEYDELETED",
            I18n.marktr("{0} deleted the activation key {1}"));
    }

}
