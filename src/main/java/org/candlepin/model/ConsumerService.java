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
package org.candlepin.model;

import org.candlepin.auth.Principal;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;


@Singleton
public class ConsumerService {
    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private DeletedConsumerCurator deletedConsumerCurator;
    @Inject private PrincipalProvider principalProvider;
    @Inject private PoolManager poolManager;
    @Inject private IdentityCertificateCurator identityCertificateCurator;
    @Inject private ContentAccessCertificateCurator contentAccessCertificateCurator;
    @Inject private CertificateSerialCurator certificateSerialCurator;
    @Inject private FactValidator factValidator;

    public void delete(Consumer consumer) {
        log.debug("Deleting consumer: {}", consumer);
        this.bulkDelete(Collections.singletonList(consumer));
    }

    public void bulkDelete(List<Consumer> consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        List<String> uuidsToDelete = new ArrayList<>(consumers.size());
        List<Long> serialsToRevoke = new ArrayList<>(consumers.size());
        List<String> idCertsToDelete = new ArrayList<>(consumers.size());
        List<String> caCertsToDelete = new ArrayList<>(consumers.size());

        for (Consumer c : consumers) {
            log.info("Deleting consumer: {}", c);

            uuidsToDelete.add(c.getUuid());

            IdentityCertificate idCert = c.getIdCert();
            if (idCert != null) {
                idCertsToDelete.add(idCert.getId());
                serialsToRevoke.add(idCert.getSerial().getId());
            }

            ContentAccessCertificate contentAccessCert = c.getContentAccessCert();
            if (contentAccessCert != null) {
                caCertsToDelete.add(contentAccessCert.getId());
                serialsToRevoke.add(contentAccessCert.getSerial().getId());
            }
        }

        List<Entitlement> entsToRevoke = this.entitlementCurator.listByConsumerUuids(uuidsToDelete);
        // We're about to delete these consumers; no need to regen/dirty their dependent
        // entitlements or recalculate status.
        this.poolManager.revokeEntitlements(entsToRevoke, false);

        this.deleteConsumers(consumers);

        int deletedIdCerts = this.identityCertificateCurator.deleteByIds(idCertsToDelete);
        log.debug("Deleted {} identity certificates", deletedIdCerts);

        int deletedCaCerts = this.contentAccessCertificateCurator.deleteByIds(caCertsToDelete);
        log.debug("Deleted {} content access certificates", deletedCaCerts);

        int revokedSerials = this.certificateSerialCurator.revokeByIds(serialsToRevoke);
        log.debug("Revoked {} certificate serials", revokedSerials);
    }

    private void deleteConsumers(Collection<Consumer> consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        log.debug("Deleting {} consumer(s)", consumers.size());

        Map<String, Consumer> consumerMap = consumers.stream()
            .collect(Collectors.toMap(Consumer::getUuid, Function.identity()));

        int deleted = this.consumerCurator.deleteByUuids(consumerMap.keySet());
        log.debug("Deleted {} consumer(s)", deleted);

        Collection<DeletedConsumer> dcRecords = this.buildDeletedConsumerRecords(consumerMap);
        this.deletedConsumerCurator.saveOrUpdateAll(dcRecords, false, false);
    }

    private List<DeletedConsumer> buildDeletedConsumerRecords(Map<String, Consumer> consumerMap) {
        Principal principal = this.principalProvider.get();
        String principalName = principal != null ? principal.getName() : null;

        Map<String, DeletedConsumer> dcs = this.deletedConsumerCurator
            .findByConsumerUuids(consumerMap.keySet()).stream()
            .collect(Collectors.toMap(DeletedConsumer::getConsumerUuid, Function.identity()));

        Function<Consumer, DeletedConsumer> mapper = (consumer) -> {
            DeletedConsumer dc = dcs.get(consumer.getUuid());
            if (dc == null) {
                dc = new DeletedConsumer();
            }

            Owner owner = consumer.getOwner();

            return dc.setConsumerUuid(consumer.getUuid())
                .setOwnerId(owner.getOwnerId())
                .setOwnerKey(owner.getKey())
                .setOwnerDisplayName(owner.getDisplayName())
                .setPrincipalName(principalName);
        };

        return consumerMap.values()
            .stream()
            .map(mapper)
            .collect(Collectors.toList());
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param user User
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    public Consumer findByUser(User user) {
        return user != null ? this.findByUsername(user.getUsername()) : null;
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param username the username to use to find a consumer
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    public Consumer findByUsername(String username) {
        ConsumerType person = consumerTypeCurator
            .getByLabel(ConsumerType.ConsumerTypeEnum.PERSON.getLabel());

        // todo
        return this.consumerCurator.findByUsername(username, person);
    }


    /**
     * Updates an existing consumer with the state specified by the given Consumer instance. If the
     * consumer has not yet been created, it will be created.
     * <p></p>
     * <strong>Warning:</strong> Using an pre-existing and persisted Consumer entity as the update
     * to apply may cause issues, as Hibernate may opt to save changes to nested collections
     * (facts, guestIds, tags, etc.) when any other database operation is performed. To avoid this
     * issue, it is advised to use only detached or otherwise unmanaged entities for the updated
     * consumer to pass to this method.
     *
     * @param updatedConsumer
     *  A Consumer instance representing the updated state of a consumer
     *
     * @param flush
     *  Whether or not to flush pending database operations after creating or updating the given
     *  consumer
     *
     * @return
     *  The persisted, updated consumer
     */
    @Transactional
    public Consumer update(Consumer updatedConsumer, boolean flush) {
        // TODO: FIXME:
        // We really need to use a DTO here. Hibernate has so many pitfalls with this approach that
        // can and will lead to odd, broken or out-of-order behavior.

        // Validate inbound facts before even attempting to apply the update
        this.factValidator.validate(updatedConsumer);

        Consumer existingConsumer = this.consumerCurator.get(updatedConsumer.getId());

        if (existingConsumer == null) {
            return this.consumerCurator.create(updatedConsumer, flush);
        }

        // TODO: Are any of these read-only?
        existingConsumer.setEntitlements(entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements()));

        // This set of updates is strange. We're ignoring the "null-as-no-change" semantics we use
        // everywhere else, and just blindly copying everything over.
        existingConsumer.setFacts(updatedConsumer.getFacts());
        existingConsumer.setName(updatedConsumer.getName());
        existingConsumer.setOwner(updatedConsumer.getOwner());

        // Set TypeId only if the existing consumer and update consumer typeId is not equal.
        // This check has been added for updating Swatch timestamp
        if (updatedConsumer.getTypeId() != null &&
            !Util.equals(existingConsumer.getTypeId(), updatedConsumer.getTypeId())) {
            existingConsumer.setTypeId(updatedConsumer.getTypeId());
        }

        existingConsumer.setUuid(updatedConsumer.getUuid());

        if (flush) {
            this.consumerCurator.save(existingConsumer);
        }

        return existingConsumer;
    }

}
