/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudOffering;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Builder for constructing {@link ConsumerCloudData} from consumer facts.
 * <p>
 * This class selects the appropriate {@link CloudProviderFactParser} based on the provided facts and
 * builds a {@link ConsumerCloudData} object containing cloud-specific information.
 * </p>
 */
@Singleton
public class ConsumerCloudDataBuilder {

    private final I18n i18n;
    private final Set<CloudProviderFactParser> parsers;

    @Inject
    public ConsumerCloudDataBuilder(I18n i18n, Set<CloudProviderFactParser> parsers) {
        this.parsers = parsers;
        this.i18n = i18n;
    }

    public Optional<CloudData> build(Consumer consumer) {
        return buildConsumerCloudData(consumer.getFacts());
    }


    public Optional<CloudData> build(ConsumerDTO consumer) {
        return buildConsumerCloudData(consumer.getFacts());
    }

    private Optional<CloudData> buildConsumerCloudData(Map<String, String> facts) {
        List<CloudProviderFactParser> supportedParsers = parsers.stream()
            .filter(parser -> parser.isSupported(facts))
            .toList();

        if (supportedParsers.size() > 1) {
            String msg = I18n.marktr("During consumer cloud creation more than one cloud " +
                "provider facts found");
            throw new BadRequestException(this.i18n.tr(msg));
        }
        else if (supportedParsers.isEmpty()) {
            return Optional.empty();
        }

        return this.createConsumerCloudData(supportedParsers.get(0), facts);
    }

    private Optional<CloudData> createConsumerCloudData(
        CloudProviderFactParser parser, Map<String, String> facts) {

        List<String> rawOfferings = parser.getOfferingIds(facts).orElse(List.of());
        if (rawOfferings.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CloudData(
            parser.getAccountId(facts).orElse(null),
            parser.getInstanceId(facts).orElse(null),
            rawOfferings,
            parser.getShortName()));
    }

    public record CloudData (String accountId, String instanceId, List<String> offerings, String providerShortName) {

    }

}
