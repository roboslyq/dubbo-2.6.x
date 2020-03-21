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
package com.alibaba.dubbo.remoting.transport.netty;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractClient;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * NettyClient.
 * Netty客户端
 */
public class NettyClient extends AbstractClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    // ChannelFactory's closure has a DirectMemory leak, using static to avoid
    // https://issues.jboss.org/browse/NETTY-424
    private static final ChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(new NamedThreadFactory("NettyClientBoss", true)),
            Executors.newCachedThreadPool(new NamedThreadFactory("NettyClientWorker", true)),
            Constants.DEFAULT_IO_THREADS);
    private ClientBootstrap bootstrap;

    // 这里的 Channel 全限定名称为 org.jboss.netty.channel.Channel
    private volatile Channel channel; // volatile, please copy reference to use

    /**
     * 构造函数，调用父类的构造函数，在父类中通过模板方法，调用下面的doOpen()实现。从而启动客户端
     * @param url 服务提供者URL
     * @param handler 事件处理器。
     * @throws RemotingException
     */
    public NettyClient(final URL url, final ChannelHandler handler) throws RemotingException {
        super(url,
                wrapChannelHandler(url, handler) //构造事件转发模型(Dispatch)：对当前Handler进行包装
        );
    }

    /**
     * 调用doOpen初始化客户端调用模型
     * 重点注意：只是构造模型并未发起链接，具体连接由doConnect()执行
     * @throws Throwable
     */
    @Override
    protected void doOpen() throws Throwable {
        NettyHelper.setNettyLoggerFactory();
        //创建Netty客户端启动实例bootstrap.
        bootstrap = new ClientBootstrap(channelFactory);
        // config
        // @see org.jboss.netty.channel.socket.SocketChannelConfig
        bootstrap.setOption("keepAlive", true);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("connectTimeoutMillis", getTimeout());
        //getUrl = dubbo://192.168.43.62:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-consumer&check=false&codec=dubbo&dubbo=2.0.0&generic=false&group=b&heartbeat=60000&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello&module=mo1&owner=luoyq&pid=7000&qos.port=33333&register.ip=192.168.43.62&remote.timestamp=1584664893538&revision=0.0.1&side=consumer&timestamp=1584665130868&version=0.0.1
        //创建NettyClientHandler。
        final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                //设置编码解码器NettyCodecAdapter
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyClient.this);
                //DefaultChannelPipeline{(decoder = com.alibaba.dubbo.remoting.transport.netty.NettyCodecAdapter$InternalDecoder)}
                ChannelPipeline pipeline = Channels.pipeline();
                //NettyCodecAdapter$InternalDecoder
                pipeline.addLast("decoder", adapter.getDecoder());
                //NettyCodecAdapter$InternalEncoder
                pipeline.addLast("encoder", adapter.getEncoder());
                //NettyHandler
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        });
    }

    /**
     * 向服务器发起连接
     * @throws Throwable
     */
    protected void doConnect() throws Throwable {
        long start = System.currentTimeMillis();
//        调用bootstrap.connect方法发起TCP连接。
        ChannelFuture future = bootstrap.connect(getConnectAddress());
        try {
//            future.awaitUninterruptibly，连接事件只等待getConnectTimeout() 秒
            boolean ret = future.awaitUninterruptibly(getConnectTimeout(), TimeUnit.MILLISECONDS);
            //连接成功
            if (ret && future.isSuccess()) {
                //获取channel
                Channel newChannel = future.getChannel();
                //设置channel可读可写
                newChannel.setInterestOps(Channel.OP_READ_WRITE);
                try {
                    // Close old channel
                    //关闭旧Channel
                    Channel oldChannel = NettyClient.this.channel; // copy reference
                    if (oldChannel != null) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close old netty channel " + oldChannel + " on create new netty channel " + newChannel);
                            }
                            oldChannel.close();
                        } finally {
                            NettyChannel.removeChannelIfDisconnected(oldChannel);
                        }
                    }
                } finally {
                    if (NettyClient.this.isClosed()) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close new netty channel " + newChannel + ", because the client closed.");
                            }
                            newChannel.close();
                        } finally {
                            //将新的channel设置为当前channel
                            NettyClient.this.channel = null;
                            NettyChannel.removeChannelIfDisconnected(newChannel);
                        }
                    } else {
                        NettyClient.this.channel = newChannel;
                    }
                }
            } else if (future.getCause() != null) {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + ", error message is:" + future.getCause().getMessage(), future.getCause());
            } else {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + " client-side timeout "
                        + getConnectTimeout() + "ms (elapsed: " + (System.currentTimeMillis() - start) + "ms) from netty client "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
            }
        } finally {
            if (!isConnected()) {
                future.cancel();
            }
        }
    }

    /**
     * 关闭连接
     * @throws Throwable
     */
    @Override
    protected void doDisConnect() throws Throwable {
        try {
            NettyChannel.removeChannelIfDisconnected(channel);
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }
    }

    @Override
    protected void doClose() throws Throwable {
        /*try {
            bootstrap.releaseExternalResources();
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }*/
    }

    @Override
    protected com.alibaba.dubbo.remoting.Channel getChannel() {
        Channel c = channel;
        if (c == null || !c.isConnected())
            return null;
        return NettyChannel.getOrAddChannel(c, getUrl(), this);
    }

}