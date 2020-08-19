# 7.DUBBO之消费端调用流程

## 服务端NettyHandler初始化流程

```
Exchangers : new ChannelHandlerAdapter()

Exchangers : new ExchangeHandlerDispatcher(replier, new ChannelHandlerAdapter())

HeaderExchanger :  new DecodeHandler( new HeaderExchangeHandler(new ExchangeHandlerDispatcher(replier, new ChannelHandlerAdapter()))

NettyServer:  ChannelHandlers.wrap(new DecodeHandler( new HeaderExchangeHandler(new ExchangeHandlerDispatcher(replier, new ChannelHandlerAdapter())), ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)))

NettyServer: new NettyServerHandler(getUrl(), NettyServer.this);
```



## 调用栈

- 第一段： 服务器接收到请求到解码，得到Request阶段。(断点在`DubboCodec.decodeBody`中)

```verilog
decodeBody:61, DubboCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
      decode:121, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
      decode:82, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec)
      decode:44, DubboCountCodec (com.alibaba.dubbo.rpc.protocol.dubbo)
      messageReceived:133, NettyCodecAdapter$InternalDecoder (com.alibaba.dubbo.remoting.transport.netty)
      handleUpstream:70, SimpleChannelUpstreamHandler (org.jboss.netty.channel)
      sendUpstream:564, DefaultChannelPipeline (org.jboss.netty.channel)
      sendUpstream:559, DefaultChannelPipeline (org.jboss.netty.channel)
      fireMessageReceived:268, Channels (org.jboss.netty.channel)
      fireMessageReceived:255, Channels (org.jboss.netty.channel)
      read:88, NioWorker (org.jboss.netty.channel.socket.nio)
      process:108, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
      run:337, AbstractNioSelector (org.jboss.netty.channel.socket.nio)
      run:89, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
      run:178, NioWorker (org.jboss.netty.channel.socket.nio)
      run:108, ThreadRenamingRunnable (org.jboss.netty.util)
      run:42, DeadLockProofWorker$1 (org.jboss.netty.util.internal)
      runWorker:1142, ThreadPoolExecutor (java.util.concurrent)
      run:617, ThreadPoolExecutor$Worker (java.util.concurrent)
      run:745, Thread (java.lang)


```

- 第二段：解码器将数据包解析成 Request 对象后，NettyHandler 的 messageReceived 方法紧接着会收到这个对象，并将这个对象继续向下传递。这期间该对象会被依次传递给 NettyServer、MultiMessageHandler、HeartbeatHandler 以及 AllChannelHandler。最后由 AllChannelHandler 将该对象封装到 Runnable 实现类对象中，并将 Runnable 放入线程池中执行后续的调用逻辑。整个调用栈如下：

  ```verilog
  NettyHandler#messageReceived(ChannelHandlerContext, MessageEvent)
    —> AbstractPeer#received(Channel, Object)
      —> MultiMessageHandler#received(Channel, Object)
        —> HeartbeatHandler#received(Channel, Object)
          —> AllChannelHandler#received(Channel, Object)
            —> ExecutorService#execute(Runnable)    // 由线程池执行后续的调用逻
  ```

  断点在`AllChannelHandler#received`中

```verilog
—> ExecutorService#execute(Runnable)    // 由线程池执行后续的调用逻辑
received:58, AllChannelHandler (com.alibaba.dubbo.remoting.transport.dispatcher.all)
received:88, HeartbeatHandler (com.alibaba.dubbo.remoting.exchange.support.header)
received:43, MultiMessageHandler (com.alibaba.dubbo.remoting.transport)
received:136, AbstractPeer (com.alibaba.dubbo.remoting.transport)
//入口方法
messageReceived:90, NettyHandler (com.alibaba.dubbo.remoting.transport.netty)

handleUpstream:88, SimpleChannelHandler (org.jboss.netty.channel)
sendUpstream:564, DefaultChannelPipeline (org.jboss.netty.channel)
sendUpstream:791, DefaultChannelPipeline$DefaultChannelHandlerContext (org.jboss.netty.channel)
fireMessageReceived:296, Channels (org.jboss.netty.channel)
messageReceived:157, NettyCodecAdapter$InternalDecoder (com.alibaba.dubbo.remoting.transport.netty)
handleUpstream:70, SimpleChannelUpstreamHandler (org.jboss.netty.channel)
sendUpstream:564, DefaultChannelPipeline (org.jboss.netty.channel)
sendUpstream:559, DefaultChannelPipeline (org.jboss.netty.channel)
fireMessageReceived:268, Channels (org.jboss.netty.channel)
fireMessageReceived:255, Channels (org.jboss.netty.channel)
read:88, NioWorker (org.jboss.netty.channel.socket.nio)
process:108, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
run:337, AbstractNioSelector (org.jboss.netty.channel.socket.nio)
run:89, AbstractNioWorker (org.jboss.netty.channel.socket.nio)
run:178, NioWorker (org.jboss.netty.channel.socket.nio)
run:108, ThreadRenamingRunnable (org.jboss.netty.util)
run:42, DeadLockProofWorker$1 (org.jboss.netty.util.internal)
runWorker:1142, ThreadPoolExecutor (java.util.concurrent)
run:617, ThreadPoolExecutor$Worker (java.util.concurrent)
run:745, Thread (java.lang)
```



第三阶段：断点在业务方法“DemoServiceImpl1#sayHello”中。由第二阶段的ExecutorService#execute(Runnable)   触发：

cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));

```
sayHello:28, DemoServiceImpl1 (com.alibaba.dubbo.demo.provider)
invokeMethod:-1, Wrapper2 (com.alibaba.dubbo.common.bytecode)
doInvoke:56, JavassistProxyFactory$1 (com.alibaba.dubbo.rpc.proxy.javassist)
invoke:73, AbstractProxyInvoker (com.alibaba.dubbo.rpc.proxy)
invoke:48, DelegateProviderMetaDataInvoker (com.alibaba.dubbo.config.invoker)
invoke:54, InvokerWrapper (com.alibaba.dubbo.rpc.protocol)
invoke:61, ExceptionFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:74, MonitorFilter (com.alibaba.dubbo.monitor.support)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:41, TimeoutFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:77, TraceFilter (com.alibaba.dubbo.rpc.protocol.dubbo.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:71, ContextFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:131, GenericFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:37, ClassLoaderFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
invoke:42, EchoFilter (com.alibaba.dubbo.rpc.filter)
invoke:72, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol)
reply:115, DubboProtocol$1 (com.alibaba.dubbo.rpc.protocol.dubbo)
handleRequest:96, HeaderExchangeHandler (com.alibaba.dubbo.remoting.exchange.support.header)
received:168, HeaderExchangeHandler (com.alibaba.dubbo.remoting.exchange.support.header)
received:50, DecodeHandler (com.alibaba.dubbo.remoting.transport)
run:79, ChannelEventRunnable (com.alibaba.dubbo.remoting.transport.dispatcher)
runWorker:1142, ThreadPoolExecutor (java.util.concurrent)
run:617, ThreadPoolExecutor$Worker (java.util.concurrent)
run:745, Thread (java.lang)
```

## 总结

```verilog
NettyHandler#messageReceived(ChannelHandlerContext, MessageEvent)
  —> AbstractPeer#received(Channel, Object)
    —> MultiMessageHandler#received(Channel, Object)
      —> HeartbeatHandler#received(Channel, Object)
        —> AllChannelHandler#received(Channel, Object)
          —> ExecutorService#execute(Runnable)    // 由线程池执行后续的调用逻辑
```

