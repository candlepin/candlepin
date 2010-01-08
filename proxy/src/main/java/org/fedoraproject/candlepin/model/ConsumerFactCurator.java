package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

public class ConsumerFactCurator extends AbstractHibernateCurator<ConsumerFact> {
    public ConsumerFactCurator() {
        super(ConsumerFact.class);
    }
    
    @Transactional
    public ConsumerFact update(ConsumerFact updated) {
        return getEntityManager().merge(updated);
    }    
}
