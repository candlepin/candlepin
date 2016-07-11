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
package org.candlepin.resteasy.filter;

import org.candlepin.common.exceptions.GoneException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.JobCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Persisted;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Injector;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;


/**
 * This class should be bound to an instance
 */
public class StoreFactory {
    private final Map<Class<? extends Persisted>, EntityStore<? extends Persisted>> storeMap =
        new HashMap<Class<? extends Persisted>, EntityStore<? extends Persisted>>();

    @Inject
    public StoreFactory(Injector injector) {
        storeMap.put(Owner.class, new OwnerStore());
        storeMap.put(Environment.class, new EnvironmentStore());
        storeMap.put(Consumer.class, new ConsumerStore());
        storeMap.put(Entitlement.class, new EntitlementStore());
        storeMap.put(Pool.class, new PoolStore());
        storeMap.put(User.class, new UserStore());
        storeMap.put(ActivationKey.class, new ActivationKeyStore());
        storeMap.put(Product.class, new ProductStore());
        storeMap.put(JobStatus.class, new JobStatusStore());

        for (EntityStore<? extends Persisted> store : storeMap.values()) {
            injector.injectMembers(store);
        }
    }

    public EntityStore<? extends Persisted> getFor(Class<? extends Persisted> clazz) {
        EntityStore<? extends Persisted> store = this.storeMap.get(clazz);

        if (store == null) {
            throw new IllegalArgumentException("EntityStore for type '" + clazz + "' not found");
        }

        return store;
    }

    public boolean canValidate(Class<?> clazz) {
        return storeMap.containsKey(clazz);
    }

    private static class OwnerStore implements EntityStore<Owner> {
        @Inject private OwnerCurator ownerCurator;

        @Override
        public Owner lookup(String key) {
            return this.ownerCurator.lookupByKey(key);
        }

        @Override
        public List<Owner> lookup(Collection<String> keys) {
            return this.ownerCurator.lookupByKeys(keys).list();
        }

        @Override
        public Owner getOwner(Owner entity) {
            return entity;
        }
    }

    private class EnvironmentStore implements EntityStore<Environment> {
        @Inject private EnvironmentCurator envCurator;

        @Override
        public Environment lookup(String key) {
            return envCurator.secureFind(key);
        }

        @Override
        public List<Environment> lookup(Collection<String> keys) {
            return envCurator.listAllByIds(keys).list();
        }

        @Override
        public Owner getOwner(Environment entity) {
            return entity.getOwner();
        }
    }

    private class ConsumerStore implements EntityStore<Consumer> {
        @Inject private ConsumerCurator consumerCurator;
        @Inject private DeletedConsumerCurator deletedConsumerCurator;
        @Inject private Provider<I18n> i18nProvider;

        @Override
        public Consumer lookup(String key) {
            if (deletedConsumerCurator.countByConsumerUuid(key) > 0) {
                throw new GoneException(i18nProvider.get().tr("Unit {0} has been deleted", key), key);
            }

            return consumerCurator.findByUuid(key);
        }

        @Override
        public List<Consumer> lookup(Collection<String> keys) {
            // Do not look for deleted consumers because we do not want to throw
            // an exception and reject the whole request just because one of
            // the requested items is deleted.
            return consumerCurator.findByUuids(keys);
        }

        @Override
        public Owner getOwner(Consumer entity) {
            return entity.getOwner();
        }
    }

    private class EntitlementStore implements EntityStore<Entitlement> {
        @Inject private EntitlementCurator entitlementCurator;

        @Override
        public Entitlement lookup(String key) {
            return entitlementCurator.secureFind(key);
        }

        @Override
        public List<Entitlement> lookup(Collection<String> keys) {
            return entitlementCurator.listAllByIds(keys).list();
        }

        @Override
        public Owner getOwner(Entitlement entity) {
            return entity.getOwner();
        }
    }

    private class PoolStore implements EntityStore<Pool> {
        @Inject private PoolCurator poolCurator;

        @Override
        public Pool lookup(String key) {
            return poolCurator.secureFind(key);
        }

        @Override
        public List<Pool> lookup(Collection<String> keys) {
            return poolCurator.listAllByIds(keys).list();
        }

        @Override
        public Owner getOwner(Pool entity) {
            return entity.getOwner();
        }
    }

    private class ActivationKeyStore implements EntityStore<ActivationKey> {
        @Inject private ActivationKeyCurator activationKeyCurator;

        @Override
        public ActivationKey lookup(String key) {
            return activationKeyCurator.secureFind(key);
        }

        @Override
        public List<ActivationKey> lookup(Collection<String> keys) {
            return activationKeyCurator.listAllByIds(keys).list();
        }

        @Override
        public Owner getOwner(ActivationKey entity) {
            return entity.getOwner();
        }
    }

    private class ProductStore implements EntityStore<Product> {
        @Inject private ProductCurator productCurator;

        @Override
        public Product lookup(String key) {
            return productCurator.find(key);
        }

        @Override
        public List<Product> lookup(Collection<String> keys) {
            return productCurator.listAllByUuids(keys).list();
        }

        @Override
        public Owner getOwner(Product entity) {
            return null;
        }
    }

    private class JobStatusStore implements EntityStore<JobStatus> {
        @Inject private JobCurator jobCurator;

        @Override
        public JobStatus lookup(String jobId) {
            return jobCurator.find(jobId);
        }

        @Override
        public List<JobStatus> lookup(Collection<String> jobIds) {
            return jobCurator.listAllByIds(jobIds).list();
        }

        @Override
        public Owner getOwner(JobStatus entity) {
            return null;
        }
    }

    private static class UserStore implements EntityStore<User> {
        @Override
        public User lookup(String key) {
            /* WARNING: Semi-risky business here, we need a user object for the security
             * code to validate, but in this area we seem to only need the username.
             */
            return new User(key, null);
        }

        @Override
        public List<User> lookup(Collection<String> keys) {
            List<User> users = new ArrayList<User>();
            for (String username : keys) {
                users.add(new User(username, null));
            }

            return users;
        }

        @Override
        public Owner getOwner(User entity) {
            return null;
        }
    }
}
