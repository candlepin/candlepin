package org.candlepin.gutterball.curator.jpa;

import org.candlepin.gutterball.model.jpa.ConsumerState;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.List;

public class ConsumerStateCurator extends BaseCurator<ConsumerState> {

    @Inject
    protected ConsumerStateCurator() {
        super(ConsumerState.class);
    }

    public ConsumerState findByUuid(String uuid) {
        return (ConsumerState) this.currentSession()
            .createCriteria(ConsumerState.class)
            .add(Restrictions.eq("uuid", uuid))
            .setMaxResults(1)
            .uniqueResult();
    }

    @Transactional
    public void setConsumerDeleted(String uuid, Date deletedOn) {
        ConsumerState consumer = this.findByUuid(uuid);
        if (consumer == null) {
            // If consumer state didn't exist, we don't care.
            // The consumer may have already existed before
            // we started collecting data.
            return;
        }

        consumer.setDeleted(deletedOn);
        save(consumer);
    }

    public List<String> getConsumerUuidsOnDate(Date targetDate, List<String> ownerKeys, List<String> uuids) {
        Criteria find = currentSession().createCriteria(ConsumerState.class);
        if (ownerKeys != null && !ownerKeys.isEmpty()) {
            find.add(Restrictions.in("ownerKey", ownerKeys));
        }
        if (uuids != null && !uuids.isEmpty()) {
            find.add(Restrictions.in("uuid", uuids));
        }

        Date toCheck = targetDate == null ? new Date() : targetDate;
        find.add(Restrictions.or(
            Restrictions.isNull("deleted"),
            Restrictions.gt("deleted", toCheck)
        ));
        find.add(Restrictions.le("created", toCheck));
        find.setProjection(Property.forName("uuid"));

        return find.list();
    }
}
