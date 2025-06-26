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
package org.candlepin.resource;

import org.candlepin.dto.api.server.v1.ConsumerFeedDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.RhsmApiCompatCurator;
import org.candlepin.resource.server.v1.RhsmapiApi;

import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entry point for endpoints that are dedicated for RHSM API for compatibility reasons.
 */
public class RhsmApiCompatResource implements RhsmapiApi {

    private final RhsmApiCompatCurator rhsmApiCompatCurator;
    private final OwnerCurator ownerCurator;
    private final I18n i18n;

    @Inject
    public RhsmApiCompatResource(RhsmApiCompatCurator rhsmApiCompatCurator, OwnerCurator ownerCurator, I18n i18n) {
        this.rhsmApiCompatCurator = Objects.requireNonNull(rhsmApiCompatCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public Stream<ConsumerFeedDTO> listConsumerFeeds(String ownerKey, String afterId, String afterUuid,
        OffsetDateTime afterCheckin, Integer page, Integer perPage) {

        Owner owner = this.findOwnerByKey(ownerKey);

        List<Consumer> consumers = this.rhsmApiCompatCurator
            .listConsumers(owner.getId(), afterId, afterCheckin);

        // TODO: Page the consumers

        return null;
    }

    /**
     * Returns the owner object that is identified by the given key, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param key the key that is associated with the owner we are searching for.
     *
     * @return the owner that was found in the system based on the given key.
     *
     * @throws NotFoundException
     *  if the owner with the given key was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Owner key is null or empty.
     */
    private Owner findOwnerByKey(String key) {
        Owner owner;
        if (key != null && !key.isEmpty()) {
            owner = ownerCurator.getByKey(key);
        }
        else {
            throw new BadRequestException(i18n.tr("Owner key is null or empty."));
        }

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found", key));
        }

        return owner;
    }
}