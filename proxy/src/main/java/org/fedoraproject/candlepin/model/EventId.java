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
package org.fedoraproject.candlepin.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * EventId: A simple database sequence used to assign events unique IDs.
 */
@Entity
@Table(name = "cp_event_id_generator")
@SequenceGenerator(name = "seq_event_id", sequenceName = "seq_event_id",
        allocationSize = 1)
public class EventId extends AbstractHibernateObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator =
        "seq_event_id")
    private Long id;

    public EventId() {
    }

    public EventId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String toString() {
        return "EventId[id=" + id + "]";
    }

}
