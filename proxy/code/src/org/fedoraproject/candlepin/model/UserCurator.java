package org.fedoraproject.candlepin.model;

public class UserCurator extends AbstractHibernateRepository<User> {

    protected UserCurator() {
        super(User.class);
    }
}
