package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

public class ConsumerInfoCurator extends AbstractHibernateCurator<ConsumerInfo> {
    public ConsumerInfoCurator() {
        super(ConsumerInfo.class);
    }
    
    @Transactional
    public ConsumerInfo update(ConsumerInfo updated) {
        return getEntityManager().merge(updated);
    }    
}
