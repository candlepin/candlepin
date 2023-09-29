/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.exceptions.GoneException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
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
    private final Map<Class<? extends Persisted>, EntityStore<? extends Persisted>>
        storeMap = new HashMap<>();

    @Inject
    public StoreFactory(Injector injector) {
        storeMap.put(Owner.class, injector.getInstance(OwnerStore.class));
        storeMap.put(Environment.class, injector.getInstance(EnvironmentStore.class));
        storeMap.put(Consumer.class, injector.getInstance(ConsumerStore.class));
        storeMap.put(AnonymousCloudConsumer.class, injector.getInstance(AnonymousCloudConsumerStore.class));
        storeMap.put(Entitlement.class, injector.getInstance(EntitlementStore.class));
        storeMap.put(Pool.class, injector.getInstance(PoolStore.class));
        storeMap.put(User.class, new UserStore());
        storeMap.put(ActivationKey.class, injector.getInstance(ActivationKeyStore.class));
        storeMap.put(Product.class, injector.getInstance(ProductStore.class));
        storeMap.put(AsyncJobStatus.class, injector.getInstance(AsyncJobStatusStore.class));
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
        private OwnerCurator ownerCurator;

        @Inject
        public OwnerStore(OwnerCurator ownerCurator) {
            this.ownerCurator = ownerCurator;
        }

        @Override
        public Owner lookup(String key) {
            return this.ownerCurator.getByKeySecure(key);
        }

        @Override
        public List<Owner> lookup(Collection<String> keys) {
            return this.ownerCurator.getByKeys(keys).list();
        }

        @Override
        public Owner getOwner(Owner entity) {
            return entity;
        }
    }

    private static class EnvironmentStore implements EntityStore<Environment> {
        private EnvironmentCurator envCurator;

        @Inject
        public EnvironmentStore(EnvironmentCurator environmentCurator) {
            this.envCurator = environmentCurator;
        }

        @Override
        public Environment lookup(String key) {
            return envCurator.secureGet(key);
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

    private static class ConsumerStore implements EntityStore<Consumer> {
        private ConsumerCurator consumerCurator;
        private DeletedConsumerCurator deletedConsumerCurator;
        private Provider<I18n> i18nProvider;
        private OwnerCurator ownerCurator;

        @Inject
        public ConsumerStore(ConsumerCurator consumerCurator,
            DeletedConsumerCurator deletedConsumerCurator, Provider<I18n> i18nProvider,
            OwnerCurator ownerCurator) {
            this.consumerCurator = consumerCurator;
            this.deletedConsumerCurator = deletedConsumerCurator;
            this.i18nProvider = i18nProvider;
            this.ownerCurator = ownerCurator;
        }

        @Override
        public Consumer lookup(final String consumerUuid) {
            final Consumer byUuid = consumerCurator.findByUuid(consumerUuid);
            if (byUuid != null) {
                return byUuid;
            }
            if (wasDeleted(consumerUuid)) {
                throw new GoneException(i18nProvider.get()
                    .tr("Unit {0} has been deleted", consumerUuid), consumerUuid);
            }
            return null;
        }

        private boolean wasDeleted(final String consumerUuid) {
            return deletedConsumerCurator.countByConsumerUuid(consumerUuid) > 0;
        }

        @Override
        public Collection<Consumer> lookup(Collection<String> keys) {
            // Do not look for deleted consumers because we do not want to throw
            // an exception and reject the whole request just because one of
            // the requested items is deleted.
            return consumerCurator.findByUuids(keys);
        }

        @Override
        public Owner getOwner(Consumer entity) {
            return ownerCurator.findOwnerById(entity.getOwnerId());
        }
    }

    private static class AnonymousCloudConsumerStore implements EntityStore<AnonymousCloudConsumer> {
        private AnonymousCloudConsumerCurator anonymousConsumerCurator;

        @Inject
        public AnonymousCloudConsumerStore(AnonymousCloudConsumerCurator anonymousConsumerCurator) {
            this.anonymousConsumerCurator = anonymousConsumerCurator;
        }

        @Override
        public AnonymousCloudConsumer lookup(String consumerUuid) {
            if (consumerUuid == null || consumerUuid.isBlank()) {
                return null;
            }

            return anonymousConsumerCurator.getByUuid(consumerUuid);
        }

        @Override
        public Collection<AnonymousCloudConsumer> lookup(
            Collection<String> keys) {
            return this.anonymousConsumerCurator.getByUuids(keys);
        }

        @Override
        public Owner getOwner(AnonymousCloudConsumer entity) {
            // Anonymous cloud consumers do not have an owner
            return null;
        }

    }

    private static class EntitlementStore implements EntityStore<Entitlement> {
        private EntitlementCurator entitlementCurator;

        @Inject
        public EntitlementStore(EntitlementCurator entitlementCurator) {
            this.entitlementCurator = entitlementCurator;
        }

        @Override
        public Entitlement lookup(String key) {
            return entitlementCurator.secureGet(key);
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

    private static class PoolStore implements EntityStore<Pool> {
        private PoolCurator poolCurator;

        @Inject
        public PoolStore(PoolCurator poolCurator) {
            this.poolCurator = poolCurator;
        }

        @Override
        public Pool lookup(String key) {
            return poolCurator.secureGet(key);
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

    private static class ActivationKeyStore implements EntityStore<ActivationKey> {
        private ActivationKeyCurator activationKeyCurator;

        @Inject
        public ActivationKeyStore(ActivationKeyCurator activationKeyCurator) {
            this.activationKeyCurator = activationKeyCurator;
        }

        @Override
        public ActivationKey lookup(String key) {
            return activationKeyCurator.secureGet(key);
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

    private static class ProductStore implements EntityStore<Product> {
        private ProductCurator productCurator;

        @Inject
        public ProductStore(ProductCurator productCurator) {
            this.productCurator = productCurator;
        }

        @Override
        public Product lookup(String key) {
            return productCurator.get(key);
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

    private static class AsyncJobStatusStore implements EntityStore<AsyncJobStatus> {
        private AsyncJobStatusCurator jobCurator;

        @Inject
        public AsyncJobStatusStore(AsyncJobStatusCurator jobCurator) {
            this.jobCurator = jobCurator;
        }

        @Override
        public AsyncJobStatus lookup(String jobId) {
            return jobCurator.get(jobId);
        }

        @Override
        public List<AsyncJobStatus> lookup(Collection<String> jobIds) {
            return jobCurator.listAllByIds(jobIds).list();
        }

        @Override
        public Owner getOwner(AsyncJobStatus entity) {
            return null;
        }
    }

    private static class UserStore implements EntityStore<User> {
        @Override
        public User lookup(String key) {
            /*
             * WARNING: Semi-risky business here, we need a user object for the security code to validate, but
             * in this area we seem to only need the username.
             */
            return new User(key, null);
        }

        @Override
        public List<User> lookup(Collection<String> keys) {
            List<User> users = new ArrayList<>();
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
