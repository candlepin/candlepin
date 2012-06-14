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
package org.candlepin.sync;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;

/**
 * ConsumerDTO
 */
public class ConsumerDto {
    private String uuid;
    private String name;
    private ConsumerType type;

    public ConsumerDto() {
    }

    ConsumerDto(String uuid, String name, ConsumerType type) {
        this.uuid = uuid;
        this.name = name;
        this.type = type;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConsumerType getType() {
        return type;
    }

    public void setType(ConsumerType type) {
        this.type = type;
    }

    public Consumer consumer() {
        Consumer toReturn = new Consumer();
        toReturn.setUuid(uuid);
        toReturn.setName(name);
        toReturn.setType(type);
        return toReturn;
    }
}
