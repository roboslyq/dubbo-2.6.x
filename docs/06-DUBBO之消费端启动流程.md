# 06. DUBBO之消费端启动流程

## 1、服务消费时序

首先来看官网服务消费者方消费服务的绿色初始化链，时序图如下：

![1](./images/06/1.jpg)

官网提供的时序比较粗，只有个大概方向。我自己根据官网的时序图作了一更详细的。但总体流程是一样的。

![2](./images/05/2.png)

先从官网提供的图来对整个服务导出(消费)的过程进行总结:

1. Spring事件通知机制：在所有的`ReferenceBean`实现了Spring中的`InitializingBean`接口，因此Spring容器在启动过程中会回调`afterPropertiesSet()`方法。因此`InitializingBean#afterPropertiesSet() `为消费者端启动入口。
2. 经过`ReferenceBean`层层包装及各种配置参数初始化，最终会在`ReferenceBean`中调用`Protocol#refer(Class<T> type, URL url)  Invoker<T>`方法。
3. `Protocol`不是直接获取的具体的RPC协议(例如`DubboProtocol`)，而是先获取`RegistryProtocol`。通过`RegistryProtocol`构建`RegistryDirectory`实例。此时通过`Registry`完成`Consumer`的相关信息注册 ,同时通过`RegistryDirectory`类调用具体的`Registry`（比如`ZookeeperRegistry`）完成服务订阅操作。
4. 具体的`Registry`完成订阅之后，会向`RegitstryDirectory`发起通知，具体调用的是`RegistryDirectory#notify(List<URL> urls)`方法。接收到通知之后 ，将订阅回来的URLS转换成`Invoker`对象 ，如果是集群则有多个Invoker对象 存放在一个Map中。
5. 将URL包装为Invoker：`Protocol$Adaptive`会根据RUL中的具体`Protocol`参数(比如dubbo://)获得具体的服务暴露协议（例如`DubboProtocol`）.然后通过具体的`DubboProtocol#refer()`获得单个的Invoker。如果是集群有多个Invoker，将其存放在Map容器中。
6. 在`DubboProtocol#refer()`获取`Invoker`过程中，会根据服务提供者相关信息，建立起`ExchangeClient`。而在建立`ExchangeClient`过程中会调用具体的`Transporters.connect()`来实现与服务提供者的连接。
7. 上面六步的起始点为第1步，当上面六步完成之后，第1步就根据URLS得到了一个服务的多个提供者(Invokers)。这多个提供放在一个`List<Invoker<?>>`中。此时还同有集群负载均衡功能。通过`Cluster#join(Directory<T> directory)  Invoker<T> `方法，将多个Invoker包装成一个Invoker，方便客户端消费(即统一入口，屏蔽集群调用的复杂性)。在Cluster包装中的Invoker中，包含了`LoadBalance`相关功能
8. 在第7步完成之后，还是一个`Invoker`，Invoker不是直接面向用户的，因为继续使用`ProxyFactory.getProxy(invoker)`包装，得到具体的代理类。此代理类即具体业务接口的代理类，用户可以像使用本地方法一样使用此代理类。

上面的8步，可以用官网的消费服务过程图来进行总结：

![3](./images/06/3.jpg)

上图是服务消费的主过程：

**1、根据配置，生成Invoker**

首先 `ReferenceConfig` 类的 `init` 方法调用 `Protocol` 的 `refer` 方法生成 `Invoker` 实例(如上图中的红色部分)，这是服务消费的关键。

**2.根据Invoker生成具体的代理类Proxy**

即把 `Invoker` 转换为客户端需要的接口(如：HelloWorld)。

关于每种协议如 RMI/Dubbo/Web service 等它们在调用 `refer` 方法生成 `Invoker` 实例的细节和上一章节所描述的类似。

## 2、详解Invoker

由于 `Invoker` 是 Dubbo 领域模型中非常重要的一个概念，很多设计思路都是向它靠拢。这就使得 `Invoker` 渗透在整个实现代码里，对于刚开始接触 Dubbo 的人，确实容易给搞混了。 下面我们用一个精简的图来说明最重要的两种 `Invoker`：服务提供 `Invoker` 和服务消费 `Invoker`：

![4](./images/06/4.jpg)

为了更好的解释上面这张图，我们结合服务消费和提供者的代码示例来进行说明：

服务消费者代码：

```java
public class DemoClientAction {
 	//因为是消费没有直接实现接口DemoService的类，但通过ProxyFacotry动态生成了其实现类，在Dubbo中称这个实现类为Proxy。
    private DemoService demoService;
 
    public void setDemoService(DemoService demoService) {
        this.demoService = demoService;
    }
 
    public void start() {
        //调用Proxy的方法，然后由Proxy调用消费端根据URI生成的Invoker。
        String hello = demoService.sayHello("world" + i);
    }
}
```

上面代码中的 `DemoService` 就是上图中服务消费端的 proxy，用户代码通过这个 proxy 调用其对应的 `Invoker`。而该 `Invoker` 实现了真正的远程服务调用。

服务提供者代码：

```java
public class DemoServiceImpl implements DemoService {
    public String sayHello(String name) throws RemoteException {
        return "Hello " + name;
    }
}
```

上面这个类会被封装成为一个 `AbstractProxyInvoker` 实例，并新生成一个 `Exporter` 实例。这样当网络通讯层收到一个请求后，会找到对应的 `Exporter` 实例，并调用它所对应的 `AbstractProxyInvoker` 实例，从而真正调用了服务提供者的代码。Dubbo 里还有一些其他的 `Invoker` 类，但上面两种是最重要的。

## 3、参考资料

https://www.jianshu.com/p/7f3871492c71

https://mp.weixin.qq.com/s/J1yUqFPN6Cf9M01W0paYcA

http://dubbo.apache.org/zh-cn/docs/source_code_guide/export-service.html

 http://dubbo.apache.org/zh-cn/docs/dev/implementation.html 