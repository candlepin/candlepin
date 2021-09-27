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
import org.candlepin.guice.PrincipalProvider;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;



public class ConsumerService {
    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private DeletedConsumerCurator deletedConsumerCurator;
    @Inject private PrincipalProvider principalProvider;
    @Inject private PoolManager poolManager;
    @Inject private IdentityCertificateCurator identityCertificateCurator;
    @Inject private ContentAccessCertificateCurator contentAccessCertificateCurator;
    @Inject private CertificateSerialCurator certificateSerialCurator;
    @Inject private KeyPairCurator keyPairCurator;

    @Transactional
    public void delete(Consumer consumer) {
        log.debug("Deleting consumer: {}", consumer);
        this.bulkDelete(Collections.singletonList(consumer));
    }

    @Transactional
    public void bulkDelete(List<Consumer> consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        List<String> consumerIdsToDelete = new ArrayList<>(consumers.size());
        List<String> consumerUuidsToDelete = new ArrayList<>(consumers.size());
        List<String> idCertsToDelete = new ArrayList<>(consumers.size());
        List<String> caCertsToDelete = new ArrayList<>(consumers.size());
        List<Long> serialsToRevoke = new ArrayList<>(consumers.size());

        for (Consumer consumer : consumers) {
            log.info("Deleting consumer: {}", consumer);

            consumerIdsToDelete.add(consumer.getId());
            consumerUuidsToDelete.add(consumer.getUuid());

            IdentityCertificate idCert = consumer.getIdCert();
            if (idCert != null) {
                idCertsToDelete.add(idCert.getId());
                serialsToRevoke.add(idCert.getSerial().getId());
            }

            ContentAccessCertificate contentAccessCert = consumer.getContentAccessCert();
            if (contentAccessCert != null) {
                caCertsToDelete.add(contentAccessCert.getId());
                serialsToRevoke.add(contentAccessCert.getSerial().getId());
            }
        }

        List<Entitlement> entsToRevoke = this.entitlementCurator.listByConsumerUuids(consumerUuidsToDelete);
        // We're about to delete these consumers; no need to regen/dirty their dependent
        // entitlements or recalculate status.
        this.poolManager.revokeEntitlements(entsToRevoke, false);

        int deletedFacts = this.consumerCurator.bulkDeleteFactsOf(consumerIdsToDelete);
        log.debug("Deleted {} facts", deletedFacts);

        List<String> keyPairsToDelete = this.keyPairCurator.findKeyPairIdsOf(consumerIdsToDelete);
        int unlinkedKeyPairs = this.keyPairCurator.unlinkKeyPairsFromConsumers(consumerIdsToDelete);
        log.debug("Unlinked {} key pairs from consumers", unlinkedKeyPairs);
        int deletedKeyPairs = this.keyPairCurator.bulkDeleteKeyPairs(keyPairsToDelete);
        log.debug("Deleted {} key pairs", deletedKeyPairs);
        int deletedInstalledProducts = this.consumerCurator.bulkDeleteInstalledProductsOf(consumerIdsToDelete);
        log.debug("Deleted {} installed products", deletedInstalledProducts);

        List<String> guestIdsToDelete = this.consumerCurator.findGuestIdsOf(consumerIdsToDelete);
        int deletedAttributes = this.consumerCurator.bulkDeleteGuestAttributesOf(guestIdsToDelete);
        log.debug("Deleted {} guest attributes", deletedAttributes);
        int deletedGuests = this.consumerCurator.bulkDeleteGuestsOf(consumerIdsToDelete);
        log.debug("Deleted {} guests", deletedGuests);

        int deletedCapabilities = this.consumerCurator.bulkDeleteCapabilitiesOf(consumerIdsToDelete);
        log.debug("Deleted {} capabilities", deletedCapabilities);
        int deletedHypervisors = this.consumerCurator.bulkDeleteHypervisorsOf(consumerIdsToDelete);
        log.debug("Deleted {} hypervisors", deletedHypervisors);
        int deletedActivationKeys = this.consumerCurator.bulkDeleteActivationKeysOf(consumerIdsToDelete);
        log.debug("Deleted {} activation keys", deletedActivationKeys);
        int deletedAddons = this.consumerCurator.bulkDeleteAddonsOf(consumerIdsToDelete);
        log.debug("Deleted {} addons", deletedAddons);
        int deletedContentTags = this.consumerCurator.bulkDeleteContentTagsOf(consumerIdsToDelete);
        log.debug("Deleted {} content tags", deletedContentTags);

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

}
