/*
 * Copyright 2011-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis;

import java.lang.reflect.Constructor;
import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;

import com.lambdaworks.redis.internal.LettuceAssert;

import com.lambdaworks.redis.internal.LettuceClassUtils;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Wraps and provides Epoll classes. This is to protect the user from {@link ClassNotFoundException}'s caused by the absence of
 * the {@literal netty-transport-native-epoll} library during runtime. Internal API.
 * 
 * @author Mark Paluch
 */
public class EpollProvider {

    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollProvider.class);

    public static final Class<EventLoopGroup> epollEventLoopGroupClass;
    public static final Class<Channel> epollDomainSocketChannelClass;
    public static final Class<SocketAddress> domainSocketAddressClass;

    static {

        epollEventLoopGroupClass = getClass("io.netty.channel.epoll.EpollEventLoopGroup");
        epollDomainSocketChannelClass = getClass("io.netty.channel.epoll.EpollDomainSocketChannel");
        domainSocketAddressClass = getClass("io.netty.channel.unix.DomainSocketAddress");
        if (epollDomainSocketChannelClass == null || epollEventLoopGroupClass == null) {
            logger.debug("Starting without optional Epoll library");
        }
    }

    /**
     * Try to load class {@literal className}.
     * 
     * @param className
     * @param <T> Expected return type for casting.
     * @return instance of {@literal className} or null
     */
    private static <T> Class<T> getClass(String className) {
        try {
            return (Class) LettuceClassUtils.forName(className);
        } catch (ClassNotFoundException e) {
            logger.debug("Cannot load class " + className, e);
        }
        return null;
    }

    /**
     * Check whether the Epoll library is available on the class path.
     * 
     * @throws IllegalStateException if the {@literal netty-transport-native-epoll} library is not available
     * 
     */
    static void checkForEpollLibrary() {

        LettuceAssert.assertState(domainSocketAddressClass != null && epollDomainSocketChannelClass != null,
                "Cannot connect using sockets without the optional netty-transport-native-epoll library on the class path");
    }

    static SocketAddress newSocketAddress(String socketPath) {
        return get(() -> {
            Constructor<SocketAddress> constructor = domainSocketAddressClass.getConstructor(String.class);
            return constructor.newInstance(socketPath);
        });
    }

    public static EventLoopGroup newEventLoopGroup(int nThreads, ThreadFactory threadFactory) {

        try {
            Constructor<EventLoopGroup> constructor = epollEventLoopGroupClass
                    .getConstructor(Integer.TYPE, ThreadFactory.class);
            return constructor.newInstance(nThreads, threadFactory);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <V> V get(Callable<V> supplier) {
        try {
            return supplier.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
