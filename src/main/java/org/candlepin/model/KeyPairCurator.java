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

import org.candlepin.pki.PKIUtility;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Query;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;



@Singleton
public class KeyPairCurator extends AbstractHibernateCurator<KeyPair> {

    private PKIUtility pki;

    @Inject
    public KeyPairCurator(PKIUtility pki) {
        super(KeyPair.class);
        this.pki = pki;
    }

    /**
     * Lookup the keypair for this consumer. If none exists, a pair will be generated.
     * Returns the java.security.KeyPair, not our internal KeyPair.
     * @return server-wide keypair.
     */
    public java.security.KeyPair getConsumerKeyPair(Consumer c) {
        // Lookup all key pairs, there should only ever be one, so raise exception
        // if multiple exist.
        KeyPair cpKeyPair = c.getKeyPair();
        if (cpKeyPair == null) {
            cpKeyPair = generateKeyPair();
            c.setKeyPair(cpKeyPair);
        }
        java.security.KeyPair returnMe = new java.security.KeyPair(
            cpKeyPair.getPublicKey(), cpKeyPair.getPrivateKey());
        return returnMe;
    }

    /**
     * Creates a key pair that is not associated with a known entity.
     *
     * @return the the generated key pair.
     */
    public java.security.KeyPair getKeyPair() {
        KeyPair cpKeyPair = this.generateKeyPair();
        java.security.KeyPair returnMe = new java.security.KeyPair(
            cpKeyPair.getPublicKey(), cpKeyPair.getPrivateKey());
        return returnMe;
    }

    private KeyPair generateKeyPair() {
        try {
            java.security.KeyPair newPair = pki.generateNewKeyPair();
            KeyPair cpKeyPair = new KeyPair(newPair.getPrivate(), newPair.getPublic());
            return create(cpKeyPair);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Takes a list of consumer ids and find all key pairs associated with these consumers.
     *
     * @param consumerIds ids of consumers whose key pairs are to be found
     * @return a list of found key pair ids
     */
    @Transactional
    public List<String> findKeyPairIdsOf(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "SELECT c.keyPair.id FROM Consumer c WHERE c.id IN (:idsToFind)";
        Query query = this.currentSession().createQuery(hql);

        List<String> found = new ArrayList<>(consumerIds.size());
        for (Collection<String> consumerIdBlock : this.partition(consumerIds)) {
            found.addAll(query.setParameter("idsToFind", consumerIdBlock).getResultList());
        }

        return found;
    }

    @Transactional
    public int unlinkKeyPairsFromConsumers(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return 0;
        }

        String hql = "UPDATE Consumer c SET c.keyPair = null WHERE c.id IN (:idsToFind)";
        Query query = this.currentSession().createQuery(hql);

        int updated = 0;
        for (Collection<String> consumerIdBlock : this.partition(consumerIds)) {
            updated += query.setParameter("idsToFind", consumerIdBlock).executeUpdate();
        }

        return updated;
    }

    /**
     * Takes a list of ids and deletes all associated key pairs.
     *
     * @param ids ids of key pairs which are to be deleted
     * @return a number of deleted key pairs
     */
    @Transactional
    public int bulkDeleteKeyPairs(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String hql = "DELETE FROM KeyPair k WHERE k.id IN (:idsToDelete)";
        Query query = this.currentSession().createQuery(hql);

        int deleted = 0;
        for (Collection<String> idBlock : this.partition(ids)) {
            deleted += query.setParameter("idsToDelete", idBlock).executeUpdate();
        }

        return deleted;
    }

}
