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

import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;

import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

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

    /**
     * Builds a {@link ConsumerCloudData} object based on the facts from the given consumer.
     *
     * @param consumer the consumer whose facts will be used for building the cloud data.
     * @return an {@link Optional} containing the {@link ConsumerCloudData} if a supported
     *         cloud provider is found or an empty {@link Optional} if no supported provider is found.
     */
    public Optional<ConsumerCloudData> build(Consumer consumer) {
        return buildConsumerCloudData(consumer.getFacts());
    }

    /**
     * Builds a {@link ConsumerCloudData} object based on the facts from the given consumer DTO.
     *
     * @param consumer the consumer DTO whose facts will be used for building the cloud data.
     * @return an {@link Optional} containing the {@link ConsumerCloudData} if a supported cloud
     *         provider is found, or an empty {@link Optional} if no supported provider is found.
     */
    public Optional<ConsumerCloudData> build(ConsumerDTO consumer) {
        return buildConsumerCloudData(consumer.getFacts());
    }

    /**
     * Processes the provided facts and builds a {@link ConsumerCloudData} object if exactly one
     * {@link CloudProviderFactParser} supports the facts.
     * <p>
     * If more than one parser supports the facts, a {@link BadRequestException} is thrown.
     * If no parser supports the facts, an empty {@link Optional} is returned.
     * </p>
     *
     * @param facts the map of cloud provider facts.
     * @return an {@link Optional} containing the {@link ConsumerCloudData} or an empty {@link Optional}.
     * @throws BadRequestException if more than one parser supports the provided facts.
     */
    private Optional<ConsumerCloudData> buildConsumerCloudData(Map<String, String> facts) {
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

        return Optional.of(createConsumerCloudData(supportedParsers.get(0), facts));
    }

    /**
     * Creates a {@link ConsumerCloudData} object using the provided {@link CloudProviderFactParser}
     * and facts.
     *
     * @param parser the cloud provider fact parser that supports the provided facts.
     * @param facts  the map of cloud provider facts.
     * @return a fully constructed {@link ConsumerCloudData} object.
     */
    private ConsumerCloudData createConsumerCloudData(
        CloudProviderFactParser parser, Map<String, String> facts) {
        return new ConsumerCloudData()
            .setCloudAccountId(parser.getAccountId(facts).orElse(null))
            .setCloudInstanceId(parser.getInstanceId(facts).orElse(null))
            .setCloudOfferingIds(parser.getOfferingIds(facts).orElse(List.of()))
            .setCloudProviderShortName(parser.getShortName());
    }
}
