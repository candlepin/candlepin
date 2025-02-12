/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

package org.candlepin.resource.util;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConsumerCloudDataBuilder {

    private final Set<CloudProviderFactParser> parsers;

    @Inject
    public ConsumerCloudDataBuilder(Set<CloudProviderFactParser> parsers) {
        this.parsers = parsers;
    }

    public Optional<ConsumerCloudData> build(Consumer consumer) {
        Map<String, String> facts = consumer.getFacts();
        List<CloudProviderFactParser> supportedParsers = parsers.stream()
            .filter(parser -> parser.isSupported(facts))
            .toList();

        if (supportedParsers.size() > 1) {
            throw new BadRequestException("More than one cloud provider facts found");
        }
        else if (supportedParsers.size() == 1) {
            CloudProviderFactParser parser = supportedParsers.get(0);
            return Optional.of(new ConsumerCloudData()
                .setCloudAccountId(parser.getAccountId(facts).orElse(null))
                .setCloudInstanceId(parser.getInstanceId(facts).orElse(null))
                .setCloudOfferingIds(parser.getOfferingIds(facts).orElse(List.of()))
                .setCloudProviderShortName(parser.getShortName()));
        }
        else {
            return Optional.empty();
        }
    }
}
