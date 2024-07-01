package org.candlepin.model;

import javax.inject.Singleton;
import javax.persistence.NoResultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConsumerCloudDataCurator extends AbstractHibernateCurator<ConsumerCloudData> {
    private static final Logger log = LoggerFactory.getLogger(ConsumerCloudDataCurator.class);

    public ConsumerCloudDataCurator() {
        super(ConsumerCloudData.class);
    }

    public ConsumerCloudData getByConsumerId(String consumerId) {
        if (consumerId == null || consumerId.isBlank()) {
            return null;
        }

        String jpql = "SELECT ccd FROM ConsumerCloudData ccd WHERE ccd.consumerId = :consumerId";

        try {
            return this.getEntityManager()
                .createQuery(jpql, ConsumerCloudData.class)
                .setParameter("consumerId", consumerId)
                .getSingleResult();
        }
        catch(NoResultException e) {
            return null;
        }
    }

}
