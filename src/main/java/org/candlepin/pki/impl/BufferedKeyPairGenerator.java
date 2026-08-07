/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.Scheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;



/**
 * The BufferedKeyPairGenerator is a decorator class that provides buffering and pre-generation of key pairs
 * to avoid the overhead of generating key pairs on demand.
 */
public class BufferedKeyPairGenerator implements KeyPairGenerator {
    private static Logger log = LoggerFactory.getLogger(BufferedKeyPairGenerator.class);

    private static final class GeneratorTask implements Runnable {

        private final KeyPairGenerator generator;
        private final ExecutorService executor;
        private final BlockingQueue<KeyPair> buffer;

        public GeneratorTask(KeyPairGenerator generator, ExecutorService executor,
            BlockingQueue<KeyPair> buffer) {

            this.generator = Objects.requireNonNull(generator);
            this.executor = Objects.requireNonNull(executor);
            this.buffer = Objects.requireNonNull(buffer);
        }

        private void reschedule() {
            try {
                if (!this.executor.isShutdown()) {
                    this.executor.execute(this);
                }
            }
            catch (RejectedExecutionException e) {
                // This should only ever happen if the executor is actually shutdown between our check and
                // the call to .execute, or if the ExecutorService is misconfigured for our uses.
                log.warn("Unable to reschedule key pair generator task; task will be permanently lost!", e);
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // PROTO: For demo purposes only
                    log.debug("GENERATING {} KEYPAIR", this.generator.getCryptoScheme().name());

                    // This will add up to the capacity of the blocking queue, and then block. Once an
                    // element is consumed, more will be added to the queue.
                    this.buffer.put(this.generator.generateKeyPair());

                    // PROTO: This is just for demo purposes.
                    if (this.buffer.remainingCapacity() <= 0) {
                        log.info("BUFFER IS FULL FOR SCHEME: {}", this.generator.getCryptoScheme().name());
                    }
                }
            }
            catch (InterruptedException | KeyException e) {
                // This will also catch the interrupted exception that can occur if this thread is being
                // terminated while the executor is shutting down. In such a case, the submit method will
                // hopefully not throw an exception, but will just silently discard the task. In the event
                // it is a sporadic wakeup, then this will reschedule the task, possibly recreating the
                // thread if necessary.
                log.warn("Unexpected error occurred while generating key pairs; restarting generator", e);
                this.reschedule();
            }
        }
    }


    private final KeyPairGenerator generator;
    private final BlockingQueue<KeyPair> buffer;
    private final ExecutorService executor;

    /**
     * TODO
     *
     * @param generator
     *  the actual key pair generator to use to generate key pairs; cannot be null
     */
    public BufferedKeyPairGenerator(KeyPairGenerator generator, ExecutorService executor, int capacity,
        int taskCount) {

        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be a positive integer");
        }

        if (taskCount < 1) {
            throw new IllegalArgumentException("taskCount must be a positive integer");
        }

        this.generator = Objects.requireNonNull(generator);
        this.executor = Objects.requireNonNull(executor);
        this.buffer = new ArrayBlockingQueue<>(capacity);

        this.initializeTasks(taskCount);
    }

    private void initializeTasks(int taskCount) {
        // PROTO: This is for demo purposes only
        log.info("STARTING BUFFERING OF KEY PAIRS FOR SCHEME: {} ({} threads, {} remaining capacity)", this.generator.getCryptoScheme().name(), taskCount, this.buffer.remainingCapacity());

        // Kick off our tasks...
        for (int i = 0; i < taskCount; ++i) {
            this.executor.execute(new GeneratorTask(this.generator, this.executor, this.buffer));
        }
    }

    @Override
    public Scheme getCryptoScheme() {
        return this.generator.getCryptoScheme();
    }

    @Override
    public KeyPair generateKeyPair() throws KeyException {
        try {
            // Attempt to pull from the buffer
            log.info("ATTEMPTING TO FETCH KEY PAIR FROM BUFFER");
            return this.buffer.remove();
        }
        catch (NoSuchElementException e) {
            // Buffer was empty, generate key pair directly
            log.warn("BUFFER EMPTY, GENERATING KEY PAIR ON DEMAND");
            return this.generator.generateKeyPair();
        }
    }

}
