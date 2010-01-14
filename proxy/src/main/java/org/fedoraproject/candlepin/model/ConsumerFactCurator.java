package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

public class ConsumerFactCurator extends AbstractHibernateCurator<ConsumerFacts> {
    public ConsumerFactCurator() {
        super(ConsumerFacts.class);
    }
    
    @Transactional
    public ConsumerFacts update(ConsumerFacts updated) {
        return getEntityManager().merge(updated);
    }    
}
