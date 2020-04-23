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
package com.alibaba.dubbo.rpc.protocol.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.io.Bytes;
import com.alibaba.dubbo.common.io.UnsafeByteArrayInputStream;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.serialize.ObjectInput;
import com.alibaba.dubbo.common.serialize.ObjectOutput;
import com.alibaba.dubbo.common.serialize.Serialization;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.exchange.Request;
import com.alibaba.dubbo.remoting.exchange.Response;
import com.alibaba.dubbo.remoting.exchange.codec.ExchangeCodec;
import com.alibaba.dubbo.remoting.transport.CodecSupport;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

import static com.alibaba.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.encodeInvocationArgument;

/**
 * Dubbo codec.
 * Dubbo编码解码器：
 *
 */
public class DubboCodec extends ExchangeCodec implements Codec2 {

    public static final String NAME = "dubbo";
    public static final String DUBBO_VERSION = Version.getVersion(DubboCodec.class, Version.getVersion());
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    public static final byte RESPONSE_VALUE = 1;
    public static final byte RESPONSE_NULL_VALUE = 2;
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);

    /**
     * 服务提供者接受消费者请求时解码器，栈调用日志如下：
     * decodeBody:61, DubboCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
     * decode:121, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
     * decode:82, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
     * decode:44, DubboCountCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
     * messageReceived:133, NettyCodecAdapter$InternalDecoder (com.alibaba.dubbo.remoting.transport.netty)
     * handleUpstream:70, SimpleChannelUpstreamHandler (org.jboss.netty.channel)
     * sendUpstream:564, DefaultChannelPipeline (org.jboss.netty.channel)
     * sendUpstream:559, DefaultChannelPipeline (org.jboss.netty.channel)
     * fireMessageReceived:268, Channels (org.jboss.netty.channel)
     * fireMessageReceived:255, Channels (org.jboss.netty.channel)
     * read:88, NioWorker (org.jboss.netty.channel.socket.nio)
     * process:108, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
     * run:337, AbstractNioSelector (org.jboss.netty.channel.socket.nio)
     * run:89, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
     * run:178, NioWorker (org.jboss.netty.channel.socket.nio)
     * run:108, ThreadRenamingRunnable (org.jboss.netty.util)
     * run:42, DeadLockProofWorker$1 (org.jboss.netty.util.internal)
     * runWorker:1142, ThreadPoolExecutor (java.util.concurrent)
     * run:617, ThreadPoolExecutor$Worker (java.util.concurrent)
     * run:745, Thread (java.lang)
     * @param channel
     * @param is
     * @param header
     * @return
     * @throws IOException
     */
    /*
     服务端解码器
     1、decodeBody 对部分字段进行了解码，并将解码得到的字段封装到 Request 中。
     2、随后会调用 DecodeableRpcInvocation 的 decode 方法进行后续的解码工作。
     3、此工作完成后，可将调用方法名、attachment、以及调用参数解析出来。
     */
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        // 获取消息头中的第三个字节，并通过逻辑与运算得到序列化器编号
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        Serialization s = CodecSupport.getSerialization(channel.getUrl(), proto);

        // get request id.
        // 获取调用编号
        long id = Bytes.bytes2long(header, 4);

        // 通过逻辑与运算得到调用类型，0 - Response，1 - Request
        if ((flag & FLAG_REQUEST) == 0) {
            // decode response.
            // 对响应结果进行解码，得到 Response 对象。这个非本节内容，后面再分析
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);
            }
            // get status.
            byte status = header[3];
            res.setStatus(status);
            if (status == Response.OK) {
                try {
                    Object data;
                    if (res.isHeartbeat()) {
                        // 对心跳包进行解码，该方法已被标注为废弃
                        data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                    } else if (res.isEvent()) {
                        // 对事件数据进行解码
                        data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                    } else {
                        DecodeableRpcResult result;
                        // 根据 url 参数判断是否在 IO 线程上对消息体进行解码
                        if (channel.getUrl().getParameter(
                                Constants.DECODE_IN_IO_THREAD_KEY,
                                Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            // 在当前线程，也就是 IO 线程上进行后续的解码工作。此工作完成后，可将
                            // 调用方法名、attachment、以及调用参数解析出来
                            result.decode();
                        } else {
                            // 仅创建 DecodeableRpcInvocation 对象，但不在当前线程上执行解码逻辑
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    if (log.isWarnEnabled()) {
                        log.warn("Decode response failed: " + t.getMessage(), t);
                    }
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {
                res.setErrorMessage(deserialize(s, channel.getUrl(), is).readUTF());
            }
            return res;
        } else {
            // decode request.
            // 创建 Request 对象
            Request req = new Request(id);
            req.setVersion("2.0.0");
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            // 通过逻辑与运算得到通信方式，并设置到 Request 对象中
            if ((flag & FLAG_EVENT) != 0) {
                // 设置心跳事件到 Request 对象中
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                if (req.isHeartbeat()) {
                    // 对心跳包进行解码，该方法已被标注为废弃
                    data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                } else if (req.isEvent()) {
                    // 对事件数据进行解码
                    data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                } else {
                    DecodeableRpcInvocation inv;
                    // 根据 url 参数判断是否在 IO 线程上对消息体进行解码
                    if (channel.getUrl().getParameter(
                            Constants.DECODE_IN_IO_THREAD_KEY,
                            Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        // 在当前线程，也就是 IO 线程上进行后续的解码工作。此工作完成后，可将
                        // 调用方法名、attachment、以及调用参数解析出来
                        inv.decode();
                    } else {
                        // 仅创建 DecodeableRpcInvocation 对象，但不在当前线程上执行解码逻辑
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                // 设置 data 到 Request 对象中
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                // 若解码过程中出现异常，则将 broken 字段设为 true，
                // 并将异常对象设置到 Reqeust 对象中
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

    private ObjectInput deserialize(Serialization serialization, URL url, InputStream is)
            throws IOException {
        return serialization.deserialize(url, is);
    }

    private byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }

    /**
     * 消费者向服务提供者发起请求时的编码器，与服务端的解码器对应。
     * encodeRequestData:201, DubboCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
     * encodeRequest:231, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
     * encode:70, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
     * encode:37, DubboCountCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
     * encode:80, NettyCodecAdapter$InternalEncoder (com.alibaba.dubbo.remoting.transport.netty)
     * doEncode:66, OneToOneEncoder (org.jboss.netty.handler.codec.oneone)
     * handleDownstream:59, OneToOneEncoder (org.jboss.netty.handler.codec.oneone)
     * sendDownstream:591, DefaultChannelPipeline (org.jboss.netty.channel)
     * sendDownstream:784, DefaultChannelPipeline$DefaultChannelHandlerContext (org.jboss.netty.channel)
     * writeRequested:292, SimpleChannelHandler (org.jboss.netty.channel)
     * writeRequested:98, NettyHandler (com.alibaba.dubbo.remoting.transport.netty)
     * handleDownstream:254, SimpleChannelHandler (org.jboss.netty.channel)
     * sendDownstream:591, DefaultChannelPipeline (org.jboss.netty.channel)
     * sendDownstream:582, DefaultChannelPipeline (org.jboss.netty.channel)
     * write:704, Channels (org.jboss.netty.channel)
     * write:671, Channels (org.jboss.netty.channel)
     * write:348, AbstractChannel (org.jboss.netty.channel)
     * send:106, NettyChannel (com.alibaba.dubbo.remoting.transport.netty)
     * send:256, AbstractClient (com.alibaba.dubbo.remoting.transport)
     * send:52, AbstractPeer (com.alibaba.dubbo.remoting.transport)
     * request:141, HeaderExchangeChannel (com.alibaba.dubbo.remoting.exchange.support.header)
     * request:100, HeaderExchangeClient (com.alibaba.dubbo.remoting.exchange.support.header)
     * request:85, ReferenceCountExchangeClient (com.alibaba.dubbo.rpc.protocol.dubbo)
     * doInvoke:128, DubboInvoker (com.alibaba.dubbo.rpc.protocol.dubbo)
     * invoke:142, AbstractInvoker (com.alibaba.dubbo.rpc.protocol)
     * invoke:73, ListenerInvokerWrapper (com.alibaba.dubbo.rpc.listener)
     * invoke:74, MonitorFilter (com.alibaba.dubbo.monitor.support)
     * invoke:71, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
     * invoke:53, FutureFilter (com.alibaba.dubbo.rpc.protocol.dubbo.filter)
     * invoke:71, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
     * invoke:47, ConsumerContextFilter (com.alibaba.dubbo.rpc.filter)
     * invoke:71, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
     * invoke:52, InvokerWrapper (com.alibaba.dubbo.rpc.protocol)
     * doInvoke:93, FailoverClusterInvoker (com.alibaba.dubbo.rpc.cluster.support)
     * invoke:241, AbstractClusterInvoker (com.alibaba.dubbo.rpc.cluster.support)
     * invoke:82, MockClusterInvoker (com.alibaba.dubbo.rpc.cluster.support.wrapper)
     * invoke:75, InvokerInvocationHandler (com.alibaba.dubbo.rpc.proxy)
     * sayHello:-1, proxy0 (com.alibaba.dubbo.common.bytecode)
     * main:43, Consumer (com.alibaba.dubbo.demo.consumer)
     *
     *
     * @param channel NettyChannel(默认的通讯协议)
     * @param out Hession2ObjectOutput(默认的序列化协议)
     * @param data RpcInvocation(请求上下文)
     * @throws IOException
     */
    //消费端编码器
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;
        // 依次序列化 dubbo version、path、version
        out.writeUTF(inv.getAttachment(Constants.DUBBO_VERSION_KEY, DUBBO_VERSION));
        out.writeUTF(inv.getAttachment(Constants.PATH_KEY));
        out.writeUTF(inv.getAttachment(Constants.VERSION_KEY));
        //序列化服务提供者方法名称
        out.writeUTF(inv.getMethodName());
        // 将参数类型转换为字符串，并进行序列化
        out.writeUTF(ReflectUtils.getDesc(inv.getParameterTypes()));
        //参数处理
        Object[] args = inv.getArguments();
        if (args != null)
            for (int i = 0; i < args.length; i++) {
                out.writeObject(
                        // 循环对运行时参数进行序列化
                        encodeInvocationArgument(channel, inv, i)
                );
            }
        // 序列化附件参数 attachment,inv.getAttachments()值如下：
        //0 = {HashMap$Node@3482} "path" -> "com.alibaba.dubbo.demo.DemoService"
        //1 = {HashMap$Node@3483} "interface" -> "com.alibaba.dubbo.demo.DemoService"
        //2 = {HashMap$Node@3484} "version" -> "0.0.1"
        //3 = {HashMap$Node@3485} "group" -> "b"
        out.writeObject(inv.getAttachments());
    }

    /**
     * 服务提供者响应进行编码
     * @param channel
     * @param out
     * @param data
     * @throws IOException
     */
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        Result result = (Result) data;

        Throwable th = result.getException();
        if (th == null) {
            Object ret = result.getValue();
            if (ret == null) {
                out.writeByte(RESPONSE_NULL_VALUE);
            } else {
                out.writeByte(RESPONSE_VALUE);
                out.writeObject(ret);
            }
        } else {
            out.writeByte(RESPONSE_WITH_EXCEPTION);
            out.writeObject(th);
        }
    }
}