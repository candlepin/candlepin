package org.fedoraproject.candlepin.model;

import java.util.List;

import com.wideplay.warp.persist.Transactional;

public interface EntitlementPoolCurator {

    @SuppressWarnings("unchecked")
    public abstract List<EntitlementPool> listByOwner(Owner o);

    /**
     * Look for an entitlement pool for the given owner, consumer, product.
     *
     * Note that consumer can (and often will be null). This method first
     * checks for a consumer specific method
     * @param owner
     * @param consumer
     * @param product
     * @return
     */
    public abstract EntitlementPool lookupByOwnerAndProduct(Owner owner,
            Consumer consumer, Product product);

    /**
     * Create an entitlement.
     * 
     * @param entPool
     * @param consumer
     * @return
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer parameters
    //       will most certainly be stale. beware!
    //
    @Transactional
    public abstract Entitlement createEntitlement(Owner owner,
            Consumer consumer, Product product);

    @Transactional
    public abstract EntitlementPool create(EntitlementPool entity);

}