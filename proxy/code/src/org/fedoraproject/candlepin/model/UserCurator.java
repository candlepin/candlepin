package org.fedoraproject.candlepin.model;

public class UserCurator extends AbstractHibernateCurator<User> {

    protected UserCurator() {
        super(User.class);
    }
}
