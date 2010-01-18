package org.fedoraproject.candlepin.controller;

import java.util.Date;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.PostEntitlementProcessor;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

public class Entitler {
    
    private EntitlementPoolCurator epCurator;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;

    private Enforcer enforcer;
    private PostEntitlementProcessor postEntProcessor;
    
    @Inject
    protected Entitler(EntitlementPoolCurator epCurator,
            EntitlementCurator entitlementCurator, ConsumerCurator consumerCurator,
            Enforcer enforcer, PostEntitlementProcessor postEntProcessor) {
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.postEntProcessor = postEntProcessor;
        
    }
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
    public Entitlement createEntitlement(Owner owner, Consumer consumer, Product product) {
        
        EntitlementPool ePool = epCurator.lookupByOwnerAndProduct(owner, consumer, product);
        if (ePool == null) {
            throw new RuntimeException("No entitlements for product: " + product.getName());
        }
        
        if (!enforcer.validate(consumer, ePool)) {
            throw new RuntimeException(enforcer.errors().toString());
        }
        
        Entitlement e = new Entitlement(ePool, consumer, new Date());
        
        consumer.addEntitlement(e);
        consumer.addConsumedProduct(product);
        
        ePool.bumpCurrentMembers();
        
        entitlementCurator.create(e);
        consumerCurator.update(consumer);
        epCurator.merge(ePool);

        postEntProcessor.run(e);
        
        return e;
    }
    
}
