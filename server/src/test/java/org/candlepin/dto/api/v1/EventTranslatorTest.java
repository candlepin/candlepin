/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.*;

import org.candlepin.audit.Event;
import org.candlepin.auth.PrincipalData;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;

import java.util.Date;

/**
 * Test suite for the EventTranslator class
 */
public class EventTranslatorTest extends AbstractTranslatorTest<Event, EventDTO, EventTranslator> {

    protected EventTranslator translator = new EventTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Event.class, EventDTO.class);
    }

    @Override
    protected EventTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Event initSourceObject() {
        Event source = new Event();
        source.setId("id");
        source.setTargetName("target-name");
        source.setConsumerUuid("consumer-uuid");
        source.setEntityId("entity-id");
        source.setMessageText("message-text");
        source.setOwnerId("owner-id");
        source.setPrincipalStore("principal-store");
        source.setPrincipal(new PrincipalData("principal-type", "principal-name"));
        source.setReferenceId("reference-id");
        source.setTimestamp(new Date());
        source.setType(Event.Type.CREATED);
        source.setTarget(Event.Target.POOL);
        source.setReferenceType(Event.ReferenceType.POOL);
        source.setEventData("event-data");
        return source;
    }

    @Override
    protected EventDTO initDestinationObject() {
        return new EventDTO();
    }

    @Override
    protected void verifyOutput(Event source, EventDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getTargetName(), dest.getTargetName());
            assertEquals(source.getConsumerUuid(), dest.getConsumerUuid());
            assertEquals(source.getEntityId(), dest.getEntityId());
            assertEquals(source.getOwnerId(), dest.getOwnerId());
            assertEquals(source.getPrincipalStore(), dest.getPrincipalStore());
            assertEquals(source.getMessageText(), dest.getMessageText());
            assertEquals(source.getReferenceId(), dest.getReferenceId());
            assertEquals(source.getTimestamp(), dest.getTimestamp());
            assertEquals(source.getType().toString(), dest.getType());
            assertEquals(source.getTarget().toString(), dest.getTarget());
            assertEquals(source.getReferenceType().toString(), dest.getReferenceType());
            assertEquals(source.getEventData(), dest.getEventData());

            if (childrenGenerated) {
                PrincipalData principalDataSource = source.getPrincipal();
                EventDTO.PrincipalDataDTO principalDataDTO = dest.getPrincipal();
                if (principalDataSource != null) {
                    assertEquals(principalDataSource.getType(), principalDataDTO.getType());
                    assertEquals(principalDataSource.getName(), principalDataDTO.getName());
                }
                else {
                    assertNull(principalDataDTO);
                }
            }
            else {
                assertNull(dest.getPrincipal());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
