/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.strategy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
 * After granting the read lock it is released, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 */
public class FileLockExclusiveReadLockStrategy extends MarkerFileExclusiveReadLockStrategy {
    private static final transient Logger LOG = LoggerFactory.getLogger(FileLockExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 1000;
    private FileLock lock;
    private String lockFileName;

    public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) {
        // noop
    }

    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call super
        if (!super.acquireExclusiveReadLock(operations, file, exchange)) {
            return false;
        }

        File target = new File(file.getAbsoluteFilePath());

        LOG.trace("Waiting for exclusive read lock to file: {}", target);

        try {
            // try to acquire rw lock on the file before we can consume it
            FileChannel channel = new RandomAccessFile(target, "rw").getChannel();

            boolean exclusive = false;
            StopWatch watch = new StopWatch();

            while (!exclusive) {
                // timeout check
                if (timeout > 0) {
                    long delta = watch.taken();
                    if (delta > timeout) {
                        LOG.warn("Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + target);
                        // we could not get the lock within the timeout period, so return false
                        return false;
                    }
                }

                // get the lock using either try lock or not depending on if we are using timeout or not
                try {
                    lock = timeout > 0 ? channel.tryLock() : channel.lock();
                } catch (IllegalStateException ex) {
                    // Also catch the OverlappingFileLockException here. Do nothing here                    
                }
                if (lock != null) {
                    LOG.trace("Acquired exclusive read lock: {} to file: {}", lock, target);
                    lockFileName = target.getName();
                    exclusive = true;
                } else {
                    boolean interrupted = sleep();
                    if (interrupted) {
                        // we were interrupted while sleeping, we are likely being shutdown so return false
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            // must handle IOException as some apps on Windows etc. will still somehow hold a lock to a file
            // such as AntiVirus or MS Office that has special locks for it's supported files
            if (timeout == 0) {
                // if not using timeout, then we cant retry, so rethrow
                throw e;
            }
            LOG.debug("Cannot acquire read lock. Will try again.", e);
            boolean interrupted = sleep();
            if (interrupted) {
                // we were interrupted while sleeping, we are likely being shutdown so return false
                return false;
            }
        }

        return true;
    }

    public void releaseExclusiveReadLock(GenericFileOperations<File> operations,
                                         GenericFile<File> file, Exchange exchange) throws Exception {

        // must call super
        super.releaseExclusiveReadLock(operations, file, exchange);

        if (lock != null) {
            Channel channel = lock.channel();
            try {
                lock.release();
            } finally {
                // must close channel first
                IOHelper.close(channel, "while acquiring exclusive read lock for file: " + lockFileName, LOG);
            }
        }
    }

    private boolean sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for {} millis.", checkInterval);
        try {
            Thread.sleep(checkInterval);
            return false;
        } catch (InterruptedException e) {
            LOG.debug("Sleep interrupted while waiting for exclusive read lock, so breaking out");
            return true;
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

}
