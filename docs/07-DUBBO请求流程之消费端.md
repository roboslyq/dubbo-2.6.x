# 7. DUBBO之消费端调用流程

## Dubbo消费端调用栈

> ```verilog
> //七===================交易发送(此时回到10.1步骤)====================
> 
> 
> //六===================编码解/码器/序列化====================
> //12、dubbo扩展的netty编码器DubboCountCodec->ExchangeCodec->DubboCodec
> // ExchangeCodec
> // Serialization serialization = getSerialization(channel);
> 
> encodeRequestData:201, DubboCodec (com.alibaba.dubbo.rpc.protocol.dubbo) encodeRequest:231, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec) encode:70, ExchangeCodec (com.alibaba.dubbo.remoting.exchange.codec) encode:37, 
> DubboCountCodec (com.alibaba.dubbo.rpc.protocol.dubbo) encode:80, 
> //11、进入Netty编码器
> NettyCodecAdapter$InternalEncoder (com.alibaba.dubbo.remoting.transport.netty) 
> 
> //五===================Netty的Client和Channel层====================
> //10.1 OneToOneEncoder#doEncode方法很关键，先编码encode(ctx, e.getChannel(), originalMessage)，然后实现请求发送： write(ctx, e.getFuture(), encodedMessage, e.getRemoteAddress())
> doEncode:66, OneToOneEncoder方法很关键，先调用 (org.jboss.netty.handler.codec.oneone) handleDownstream:59, OneToOneEncoder (org.jboss.netty.handler.codec.oneone) sendDownstream:591, DefaultChannelPipeline (org.jboss.netty.channel) sendDownstream:784, DefaultChannelPipeline$DefaultChannelHandlerContext (org.jboss.netty.channel) writeRequested:292, SimpleChannelHandler (org.jboss.netty.channel) writeRequested:98, NettyHandler 
> //10、进入到NettyChannel，并调用NioClientSocketChannel
> (com.alibaba.dubbo.remoting.transport.netty) handleDownstream:254, 
> SimpleChannelHandler (org.jboss.netty.channel) sendDownstream:591, DefaultChannelPipeline (org.jboss.netty.channel) sendDownstream:582, DefaultChannelPipeline (org.jboss.netty.channel) write:704, Channels 
> (org.jboss.netty.channel) write:671, Channels (org.jboss.netty.channel) write:348, 
> AbstractChannel (org.jboss.netty.channel) send:106, NettyChannel 
> //9、进入到NettyClient相关
> (com.alibaba.dubbo.remoting.transport.netty) send:256, AbstractClient (com.alibaba.dubbo.remoting.transport) send:52, AbstractPeer 
> 
> //四、===================Exchange层====================
> //8、进入HeaderExchangeChannel
> (com.alibaba.dubbo.remoting.transport) request:141, HeaderExchangeChannel (com.alibaba.dubbo.remoting.exchange.support.header) request:100, 
> //7、进入HeaderExchangeClient模块
> HeaderExchangeClient (com.alibaba.dubbo.remoting.exchange.support.header) request:85,
> //6、经过过滤器之后，再调用具体的Invoker实现(比如DubboInvoker)
> ReferenceCountExchangeClient (com.alibaba.dubbo.rpc.protocol.dubbo) doInvoke:128, DubboInvoker (com.alibaba.dubbo.rpc.protocol.dubbo) invoke:142, AbstractInvoker
> 
> //三、===================过滤器链====================
> //5、从Cluster中调起的不是Invoker的直接实现，而是ProtocolFilterWrapper构造的过滤器链。
> (com.alibaba.dubbo.rpc.protocol) invoke:73, ListenerInvokerWrapper (com.alibaba.dubbo.rpc.listener) invoke:74, MonitorFilter (com.alibaba.dubbo.monitor.support) invoke:71, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol) invoke:53, FutureFilter (com.alibaba.dubbo.rpc.protocol.dubbo.filter) invoke:71, ProtocolFilterWrapper$1 (com.alibaba.dubbo.rpc.protocol) invoke:47, ConsumerContextFilter (com.alibaba.dubbo.rpc.filter) invoke:71, ProtocolFilterWrapper$1 
> (com.alibaba.dubbo.rpc.protocol) invoke:52, InvokerWrapper 
> 
> //二、===================Cluster====================
> //4、进入Cluster模块，选出一个合适的Invoker。其中Cluster内部使用了层层包装模式
> (com.alibaba.dubbo.rpc.protocol) doInvoke:93, FailoverClusterInvoker 
> (com.alibaba.dubbo.rpc.cluster.support) invoke:241, AbstractClusterInvoker 
> (com.alibaba.dubbo.rpc.cluster.support) invoke:82, MockClusterInvoker 
> 
> //一、===================代理====================
> //3、调用InvokerInvocationHandler.invoke()方法
> (com.alibaba.dubbo.rpc.cluster.support.wrapper) invoke:75, InvokerInvocationHandler
> //2、代理类中的sayHello方法
> (com.alibaba.dubbo.rpc.proxy) sayHello:-1, proxy0 (com.alibaba.dubbo.common.bytecode)
> //1、主入口
> main:43, Consumer (com.alibaba.dubbo.demo.consumer)
> ```

## 1、消费端DEMO示例

`Dubbo`源码自带的Demo模块示例如下：

```java
public class Consumer {

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"META-INF/spring/dubbo-demo-consumer.xml"});
        context.start();
        DemoService demoService = (DemoService) context.getBean("demoService1");  
        String hello = demoService.sayHello("world"); // call remote method
    }
}
```

当调用`demoService.sayHello("world")`到底发生了什么呢？如果想知道发生了什么，就要知道`demoService`到底是什么，因为消费者没有具体的DemoService实现。所以此处用的是字节码技术，生成了接口`DemoService`代理。具体生成过程见`DUBBO之消费端启动流程`。

## 2、`DemoService`源码分析

我们可以通过阿里提供的***Arthas***工具获得动态代理生成的源码，大概如下：

```java
/**
 * 消费者端的代理类源码(示例)
 * 当消费者调用demoService.sayHello("")时，实现调用下面的源码。
 /
public class Proxy0 implements ClassGenerator.DC, EchoService, DemoService {
    // 方法数组:此数据有排序，然后根据排序选择具体的方法。
    public static Method[] methods;

    // InvocationHanler，具体实现为InvokerInvocationHandler
    private InvocationHandler handler;

    public Proxy0(InvocationHandler invocationHandler) {
        this.handler = invocationHandler;
    }

    public Proxy0() {
    }

    public String sayHello(String string) throws Throwable {
        // 将参数存储到 Object 数组中
        Object[] arrobject = new Object[]{string};
        // 调用 InvocationHandler 实现类的 invoke 方法得到调用结果
        Object object = this.handler.invoke(this, methods[0], arrobject);
        // 返回调用结果
        return (String) object;
    }

    // 回声测试方法
    public Object $echo(Object object)   {
        Object[] arrobject = new Object[]{object};
        Object object2 = null;
        try {
            object2 = this.handler.invoke(this, methods[1], arrobject);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return object2;
    }
}
```

我们可以看到，当调用`demoService.sayHello`时，其它调用的是`Proxy0.sayHello`。在Proxy0.sayHello中，我们通过`InvocationHandler.invoke(this, methods[0], arrobject)`来实现具体调用。所以，真正的远程调用入口就是`InovcationHandler`。

## 3、InvocationHandler

在`DUBBO`消费端启动过程中，我们可以知道，具体代理生成代理如下(以`JavassitProxy`为例)：

```java
  /**
   *  消费端启动时，获取代理proxy对象<其中InvokerInvocationHandler为生成代理类的类属性
   * ，同时完成InvokerInvocationHandler中的invoker初始化。
    /
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }
```

由此可以，`Proxy0`中的`InvocationHandler`具体实例是`InvokerInvocationHandler`。

*InvokerInvocatoinHandler*源码如下：

```java
/**
  *1、消费者发起接口调用<Proxy0代理类中>,触发此方法调用InvokerHandler：Invoker代理类，此处的Invoke()方法会调用真正的	Invoker实现
  * 2、具体初始化详情见{@link ProxyFactory#getProxy },具体实现为{@link JavassistProxyFactory} 和{@link JdkProxyFactory}
  * 其中，默认实现是{@link JavassistProxyFactory}
 */
public class InvokerInvocationHandler implements InvocationHandler {

    //此变量类型为MockClusterInvoker 内部封装了服务降级逻辑。
    private final Invoker<?> invoker;

    /**
     * @param handler
     */
    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    /**
      *@param proxy 被代理对象
      *@param method 调用方法
      *@param args  调用参数
      *@return
      *@throws Throwable
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 拦截定义在 Object 类中的方法（未被子类重写），比如 wait/notify
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        // 如果 toString、hashCode 和 equals 等方法被子类重写了，这里也直接调用
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        /**
          *1、此inovker实例为：MockClusterInvoker
          *2、将 method 和 args 封装到 RpcInvocation 中，并执行后续的调用创建上下文Invocation
         */
        return invoker.invoke(new RpcInvocation(method, args)
            ).recreate();
    }

}
```

其中，最为关键的一句代码就是`invoker.invoke(new RpcInvocation(method, args)).recreate();`

这里有一个关键点：实例化了调用上下文`RpcInvocation`。此上下文包含了要调用的方法和参数。

那么上面的invoker又是哪个实例呢？从导出的过程中可知，具体实例是`MockClusterInvoker`。

## 4、ClusterInvoker

### (1)`MockClusterInvoker`

上面InvocationHanler中的Invoker对应的实例是`MockClusterInvoker`。部分源码如下：

```java
public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
        // 获取 mock 配置值
        String value = directory.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, Boolean.FALSE.toString()).trim();
        if (value.length() == 0 || value.equalsIgnoreCase("false")) {
            // no mock
            // 无 mock 逻辑，直接调用其他 Invoker 对象的 invoke 方法，比如（默认） FailoverClusterInvoker
            result = this.invoker.invoke(invocation);
        } else if (value.startsWith("force")) {
            if (logger.isWarnEnabled()) {
                logger.info("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + directory.getUrl());
            }
            // force:direct mock
            // force:xxx 直接执行 mock 逻辑，不发起远程调用
            result = doMockInvoke(invocation, null);
        } else {
            // fail-mock
            // fail:xxx 表示消费方对调用服务失败后，再执行 mock 逻辑，不抛出异常
            try {
                // 调用其他 Invoker 对象的 invoke 方法（Invoker:默认为 FailoverClusterInvoker）
                result = this.invoker.invoke(invocation);
            } catch (RpcException e) {
                if (e.isBiz()) {
                    throw e;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.info("fail-mock: " + invocation.getMethodName() + " fail-mock enabled , url : " + directory.getUrl(), e);
                    }
                    // 调用失败，执行 mock 逻辑
                    result = doMockInvoke(invocation, e);
                }
            }
        }
        return result;
    }
```

其实，`ClusterInvoker`是一种包装模式，即一个Invoker会调用另一个Invoker，一直这样不断调用，到最后`DubboInvoker`时，才会发生远程调用。

### (2) `FailoverClusterInvoker`

此处我们分析`FailoverClusterInvoker`实现，因为这是`Dubbo`的默认实现。其它实现还有`FailbackClusterInvoker`,`FailsafeClusterInvoker`,`FailfastClusterInvoker`,`ForkingClusterInvoker`等。用户自己可以决定具体使用哪一种策略。

```java
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {
    /**
      *消费者远程方法调用生产
      *@param invocation 调用上下文
      *@param invokers 可用的invoker。默认实现的协议为dubbo，所以默认此处是DubboInvoker
      *@param loadbalance 负载均衡器
      *@return
      *@throws RpcException
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(Invocation invocation,
                            final List<Invoker<T>> invokers,
                           LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyinvokers = invokers;
        //保证invoker不为空
        checkInvokers(copyinvokers, invocation);
        int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        // 设置 Invoker
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //Reselect before retry to avoid a change of candidate `invokers`.
            //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
            if (i > 0) {
                checkWhetherDestroyed();
                copyinvokers = list(invocation);
                // check again
                checkInvokers(copyinvokers, invocation);
            }
            //根据负载均衡算法，选择一个可用的Invoker
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            //往已经使用过的Invoker列表中添加一条记录
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List) invoked);
            try {
           //发起远程调用(此处为InvokerWrapper，具体实现类是RegistryDirectory$InvokerDelegate)
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                    logger.warn("Although retry the method " + invocation.getMethodName()
                            + " in the service " + getInterface().getName()
                            + " was successful by the provider " + invoker.getUrl().getAddress()
                            + ", but there have been failed providers " + providers
                            + " (" + providers.size() + "/" + copyinvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost()
                            + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                            + le.getMessage(), le);
                }
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
    }

}
```

首先，我们来分析一下，方法签名`doInvoke(Invocation invocation,
                            final List<Invoker<T>> invokers,
                           LoadBalance loadbalance)`。

要注意，此处的3个参数均十分关键， 我们逐一分析。

- Invocatoin
  - 此参数是Dubbo调用的上下文环境，所有调用所需要的数据均包装在这里面
- invokers
  - 表示可用的Invoker集合。服务器部署集群时，一个服务对应多个Invoker.
- loadBalance
  - 负载均衡，通过相关的负载均衡算法，在invokers列表中选择出一个可用的Invoker，使用此Invoker发起具体的服务调用。此处的Invoker是具体的协议Invoker实现，比如`DubboInvoker.`



## 5、Filter过滤器链

此处以`DubboInvoker`进行分析

此处的Invoker也是`ClusterInvoker`的父接口，但此处不是指`ClusterInvoker`。而是别个一个分支，其抽象类为`AbstractInvoker`。具体的实现有`DubboInvoker`，`InjvmInvoker`,`ChannelWrapperInvoker`，`MockInvoker`,`ThriftInvoker`等。

并且此处也使用代理包装模式进行了层层包装调用，具体包装过程如下：

`RegistryDirectory$InvokerDelegate -> ProtocolFilterWrapper-->ConsumerContextFilter --> FutureFilter-->MonitorFilter-->ListenerInvokerWrapper`。

最终，在`ListenerInvokerWrapper`中实现`DubboInvoker`的调用。

**`ProtocolFilterWrapper`**中进行链条的构造，源码如下：

```java
private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
        Invoker<T> last = invoker;
        /**
         * 此处为filter链，
         */
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);
        if (!filters.isEmpty()) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                last = new Invoker<T>() {

                    public Class<T> getInterface() {
                        return invoker.getInterface();
                    }

                    public URL getUrl() {
                        return invoker.getUrl();
                    }

                    public boolean isAvailable() {
                        return invoker.isAvailable();
                    }

                    public Result invoke(Invocation invocation) throws RpcException {
                        // filter = ConsumerContextFilter --> FutureFilter
                        return filter.invoke(next, invocation);
                    }

                    public void destroy() {
                        invoker.destroy();
                    }

                    @Override
                    public String toString() {
                        return invoker.toString();
                    }
                };
            }
        }
        return last;
    }
```

## 6、DubboInvoker

`DubboInvoker`核心方法`doInvoke`代码如下：

```java
/**
     * Dubbo invoker调用
     * 1、Dubbo 实现同步和异步调用比较关键的一点就在于由谁调用 ResponseFuture 的 get 方法。
     *    同步调用模式下，由框架自身调用 ResponseFuture 的 get 方法。异步调用模式下，则由用户调用该方法。
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        // methodName = "sayHello"
        final String methodName = RpcUtils.getMethodName(invocation);
        // 设置 path 和 version 到 attachment 中
        // getUrl().getPath() = com.alibaba.dubbo.demo.DemoService
        inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
        inv.setAttachment(Constants.VERSION_KEY, version);
        //调用客户端，有多层client包装，具体顺序为【ReferenceCountExchangeClient -> HeaderExchangeClient -> HeaderExchangeChannel】
        ExchangeClient currentClient;
        if (clients.length == 1) {
            // 长度为1，直接从 clients 数组中获取 ExchangeClient
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
            // 获取异步配置
            boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
            // isOneway 为 true，表示“单向”通信
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            //获取超时时间
            int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
            if (isOneway) {            // 异步无返回值
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                // 发送请求
                currentClient.send(inv, isSent);
                // 设置上下文中的 future 字段为 null
                RpcContext.getContext().setFuture(null);
                // 返回一个空的 RpcResult
                return new RpcResult();
            } else if (isAsync) {            // 异步有返回值
                // 发送请求，并得到一个 ResponseFuture 实例《具体实现为DefaultFuture》
                ResponseFuture future = currentClient.request(inv, timeout);
                // 设置 future 到上下文中
                RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
                // 暂时返回一个空结果
                return new RpcResult();
            } else {                // 同步调用<当前版本默认的调用方式>
                RpcContext.getContext().setFuture(null);
                // currentClient = ReferenceCountExchangeClient： 发送请求，得到一个 ResponseFuture 实例，并调用该实例的 get 方法进行等待
                return (Result) currentClient.request(inv, timeout).get();
            }
        } catch (TimeoutException e) {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
```



## 7、ExchangeClient



## 8、NettyChannel

> NettyClient实现了AbstractPeer接口，因此，在HeaderExchangeClient中，实际调用的是NettyClient实现。

最终调用netty包中的`NioClientSocketChannel#write(Object)`方法，实现数据发送。

## 9.调用过程总结

```xml
proxy0#sayHello(String)
  —> InvokerInvocationHandler#invoke(Object, Method, Object[])
    —> MockClusterInvoker#invoke(Invocation)
      —> AbstractClusterInvoker#invoke(Invocation)
        —> FailoverClusterInvoker#doInvoke(Invocation, List<Invoker<T>>, LoadBalance)
          —> Filter#invoke(Invoker, Invocation)  // 包含多个 Filter 调用
            —> ListenerInvokerWrapper#invoke(Invocation) 
              —> AbstractInvoker#invoke(Invocation) 
                —> DubboInvoker#doInvoke(Invocation)
                  —> ReferenceCountExchangeClient#request(Object, int)
                    —> HeaderExchangeClient#request(Object, int)
                      —> HeaderExchangeChannel#request(Object, int)
                        —> AbstractPeer#send(Object)
                          —> AbstractClient#send(Object, boolean)
                            —> NettyChannel#send(Object, boolean)
                              —> NioClientSocketChannel#write(Object)
```

其中，在NettyChannel#send之前，还要完成编码操作。编码操作的入口为**` ExchangeCodec `** 。



