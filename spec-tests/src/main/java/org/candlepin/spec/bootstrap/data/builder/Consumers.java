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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Class meant to provide fully randomized instances of consumer.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Consumers {

    private Consumers() {
        throw new UnsupportedOperationException();
    }

    public static ConsumerDTO randomNoOwner() {
        return random((NestedOwnerDTO) null);
    }

    public static ConsumerDTO random(OwnerDTO owner) {
        return random(owner != null ? Owners.toNested(owner) : null);
    }

    public static ConsumerDTO random(OwnerDTO owner, ConsumerTypes type) {
        return random(owner)
            .type(type.value());
    }

    /**
     * Creates a randomly generated cloud consumer with AWS facts.
     *
     * @param owner
     *  the owner of the randomly generated consumer
     *
     * @return a randomly generated AWS cloud consumer
     */
    public static ConsumerDTO randomAWS(OwnerDTO owner) {
        ConsumerDTO consumer  = random(owner != null ? Owners.toNested(owner) : null);

        Map<String, String> cloudFacts = new HashMap<>();
        cloudFacts.put("aws_instance_id", StringUtil.random("instance-"));
        cloudFacts.put("aws_account_id", StringUtil.random("cloud-account-"));

        consumer.facts(cloudFacts);

        return consumer;
    }

    private static ConsumerDTO random(NestedOwnerDTO owner) {
        // TODO: fill in rest of the data
        return new ConsumerDTO()
            .name(StringUtil.random("test_consumer-", 8, StringUtil.CHARSET_NUMERIC_HEX))
            .owner(owner)
            .type(ConsumerTypes.System.value())
            .putFactsItem("system.certificate_version", "3.3")
            .releaseVer(new ReleaseVerDTO().releaseVer("version-1"));
    }

}
