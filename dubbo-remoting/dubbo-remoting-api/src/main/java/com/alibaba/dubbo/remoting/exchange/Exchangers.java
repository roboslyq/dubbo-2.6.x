/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.exchange;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.exchange.support.ExchangeHandlerDispatcher;
import com.alibaba.dubbo.remoting.exchange.support.Replier;
import com.alibaba.dubbo.remoting.transport.ChannelHandlerAdapter;

/**
 * Exchanger facade. (API, Static, ThreadSafe)
 * Exchanger的门面类，将Exchanger进行包装，包括启动服务(bind()),客户端连接(connect())
 * 1、服务端bind()时，返回{@link ExchangeServer}
 * 2、客户端connect()时，返回{@link ExchangeClient}
 */
public class Exchangers {

    static {
        // check duplicate jar package
        Version.checkDuplicate(Exchangers.class);
    }

    private Exchangers() {
    }

    public static ExchangeServer bind(String url, Replier<?> replier) throws RemotingException {
        return bind(URL.valueOf(url), replier);
    }

    /**
     * 服务启动入口
     * @param url
     * @param replier
     * @return
     * @throws RemotingException
     */
    public static ExchangeServer bind(URL url, Replier<?> replier) throws RemotingException {
        return bind(url, new ChannelHandlerAdapter(), replier);
    }

    public static ExchangeServer bind(String url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return bind(URL.valueOf(url), handler, replier);
    }

    public static ExchangeServer bind(URL url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return bind(url, new ExchangeHandlerDispatcher(replier, handler));
    }

    public static ExchangeServer bind(String url, ExchangeHandler handler) throws RemotingException {
        return bind(URL.valueOf(url), handler);
    }

    /**
     * 启动服务入口
     * @param url
     * @param handler
     * @return
     * @throws RemotingException
     */
    public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        // 获取 Exchanger，默认为 HeaderExchanger 紧接着调用 HeaderExchanger 的 bind 方法创建 ExchangeServer 实例
        //url= dubbo://192.168.0.101:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&bind.ip=192.168.0.101&bind.port=20880&channel.readonly.sent=true&codec=dubbo&dubbo=2.0.0&generic=false&group=a&heartbeat=60000&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello&pid=12060&qos.port=22222&revision=0.0.2&side=provider&timestamp=1530023473206&version=0.0.2
        return getExchanger(url).bind(url, handler);
    }

    public static ExchangeClient connect(String url) throws RemotingException {
        return connect(URL.valueOf(url));
    }

    public static ExchangeClient connect(URL url) throws RemotingException {
        return connect(url, new ChannelHandlerAdapter(), null);
    }

    public static ExchangeClient connect(String url, Replier<?> replier) throws RemotingException {
        return connect(URL.valueOf(url), new ChannelHandlerAdapter(), replier);
    }

    public static ExchangeClient connect(URL url, Replier<?> replier) throws RemotingException {
        return connect(url, new ChannelHandlerAdapter(), replier);
    }

    public static ExchangeClient connect(String url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return connect(URL.valueOf(url), handler, replier);
    }

    public static ExchangeClient connect(URL url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return connect(url, new ExchangeHandlerDispatcher(replier, handler));
    }

    public static ExchangeClient connect(String url, ExchangeHandler handler) throws RemotingException {
        return connect(URL.valueOf(url), handler);
    }

    /**
     * 连接服务器，返回一个客户端：消费者订阅服务时，获取服务器的连接。
     * @param url
     * @param handler
     * @return
     * @throws RemotingException
     */
    public static ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        return getExchanger(url)    //获取Exchanger
                .connect(url, handler); //由Exchager发起连接
    }

    /**
     * 获取一个Exchager
     * @param url
     * @return
     */
    public static Exchanger getExchanger(URL url) {
        String type = url.getParameter(Constants.EXCHANGER_KEY, Constants.DEFAULT_EXCHANGER);
        //默认是HeaderExchanger
        return getExchanger(type);
    }
    /**
     * 通过扩展点得到一个Exchanger,默认是HeaderExchanger，然后HeaderExchanger，然后层层调用到Transporters.
     * 而Transporters默认是Netty实现。此处是本地发布服务即开启监听
     * @param type
     * @return
     */
    public static Exchanger getExchanger(String type) {
        //默认是HeaderExchanger。（见Exchanger.class上的@SPI注解）
        return ExtensionLoader.getExtensionLoader(Exchanger.class).getExtension(type);
    }

}