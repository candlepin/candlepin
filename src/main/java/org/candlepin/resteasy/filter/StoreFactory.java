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
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityNotFoundException;





/**
 * This class should be bound to an instance
 */
public class StoreFactory {
    private final Map<Class<? extends Persisted>, EntityStore<? extends Persisted>>
        storeMap = new HashMap<>();

    @Inject
    public StoreFactory(
        OwnerStore ownerStore,
        EnvironmentStore environmentStore,
        ConsumerStore consumerStore,
        EntitlementStore entitlementStore,
        PoolStore poolStore,
        UserStore userStore,
        ActivationKeyStore activationKeyStore,
        AsyncJobStatusStore asyncJobStatusStore,
        AnonymousCloudConsumerStore anonymousCloudConsumerStore
    ) {
        storeMap.put(Owner.class, ownerStore);
        storeMap.put(Environment.class, environmentStore);
        storeMap.put(Consumer.class, consumerStore);
        storeMap.put(Entitlement.class, entitlementStore);
        storeMap.put(Pool.class, poolStore);
        storeMap.put(User.class, userStore);
        storeMap.put(ActivationKey.class, activationKeyStore);
        storeMap.put(AsyncJobStatus.class, asyncJobStatusStore);
        storeMap.put(AnonymousCloudConsumer.class, anonymousCloudConsumerStore);
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

    @Singleton
    public static class OwnerStore implements EntityStore<Owner> {
        private final OwnerCurator ownerCurator;

        @Inject
        public OwnerStore(OwnerCurator ownerCurator) {
            this.ownerCurator = Objects.requireNonNull(ownerCurator);
        }

        @Override
        public Owner lookup(String key) {
            return this.ownerCurator.getByKeySecure(key);
        }

        @Override
        public List<Owner> lookup(Collection<String> keys) {
            return this.ownerCurator.getByKeysSecure(keys);
        }

        @Override
        public Owner getOwner(Owner entity) {
            return entity;
        }
    }

    @Singleton
    public static class EnvironmentStore implements EntityStore<Environment> {
        private final EnvironmentCurator envCurator;

        @Inject
        public EnvironmentStore(EnvironmentCurator environmentCurator) {
            this.envCurator = Objects.requireNonNull(environmentCurator);
        }

        @Override
        public Environment lookup(String key) {
            return envCurator.secureGet(key);
        }

        @Override
        public List<Environment> lookup(Collection<String> keys) {
            return envCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Environment entity) {
            return entity.getOwner();
        }
    }

    @Singleton
    public static class ConsumerStore implements EntityStore<Consumer> {
        private final ConsumerCurator consumerCurator;
        private final DeletedConsumerCurator deletedConsumerCurator;
        private final Provider<I18n> i18nProvider;
        private final OwnerCurator ownerCurator;

        @Inject
        public ConsumerStore(ConsumerCurator consumerCurator,
            DeletedConsumerCurator deletedConsumerCurator, Provider<I18n> i18nProvider,
            OwnerCurator ownerCurator) {
            this.consumerCurator = Objects.requireNonNull(consumerCurator);
            this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
            this.i18nProvider = Objects.requireNonNull(i18nProvider);
            this.ownerCurator = Objects.requireNonNull(ownerCurator);
        }

        @Override
        public Consumer lookup(final String consumerUuid) {
            Consumer consumer = null;
            try {
                consumer = consumerCurator.findByUuid(consumerUuid);
            }
            catch (EntityNotFoundException e) {
                // Intentionally ignoring exception
            }

            if (consumer != null) {
                return consumer;
            }

            if (wasDeleted(consumerUuid)) {
                throw new GoneException(i18nProvider.get()
                    .tr("Unit {0} has been deleted", consumerUuid), consumerUuid);
            }

            return consumer;
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

    @Singleton
    public static class EntitlementStore implements EntityStore<Entitlement> {
        private final EntitlementCurator entitlementCurator;

        @Inject
        public EntitlementStore(EntitlementCurator entitlementCurator) {
            this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        }

        @Override
        public Entitlement lookup(String key) {
            return entitlementCurator.secureGet(key);
        }

        @Override
        public List<Entitlement> lookup(Collection<String> keys) {
            return entitlementCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Entitlement entity) {
            return entity.getOwner();
        }
    }

    @Singleton
    public static class PoolStore implements EntityStore<Pool> {
        private final PoolCurator poolCurator;

        @Inject
        public PoolStore(PoolCurator poolCurator) {
            this.poolCurator = Objects.requireNonNull(poolCurator);
        }

        @Override
        public Pool lookup(String key) {
            return poolCurator.secureGet(key);
        }

        @Override
        public List<Pool> lookup(Collection<String> keys) {
            return poolCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Pool entity) {
            return entity.getOwner();
        }
    }

    @Singleton
    public static class ActivationKeyStore implements EntityStore<ActivationKey> {
        private final ActivationKeyCurator activationKeyCurator;

        @Inject
        public ActivationKeyStore(ActivationKeyCurator activationKeyCurator) {
            this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        }

        @Override
        public ActivationKey lookup(String key) {
            return activationKeyCurator.secureGet(key);
        }

        @Override
        public List<ActivationKey> lookup(Collection<String> keys) {
            return activationKeyCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(ActivationKey entity) {
            return entity.getOwner();
        }
    }

    @Singleton
    public static class AsyncJobStatusStore implements EntityStore<AsyncJobStatus> {
        private final AsyncJobStatusCurator jobCurator;

        @Inject
        public AsyncJobStatusStore(AsyncJobStatusCurator jobCurator) {
            this.jobCurator = Objects.requireNonNull(jobCurator);
        }

        @Override
        public AsyncJobStatus lookup(String jobId) {
            return jobCurator.get(jobId);
        }

        @Override
        public List<AsyncJobStatus> lookup(Collection<String> jobIds) {
            return jobCurator.listAllByIds(jobIds);
        }

        @Override
        public Owner getOwner(AsyncJobStatus entity) {
            return null;
        }
    }

    @Singleton
    public static class UserStore implements EntityStore<User> {
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
