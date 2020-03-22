> zk是比较典型场景，所以注册中心都是以zk作为例子的



# 1 对于Registry，提供者没有这个，消费者才有。为什么?
因为只有消费者才需要去注册中心拿到provide的信息，而provider是不需要关注的，provider只需要去注册就好。在RegistryProtocol的export方法中，可以看到在registry方法里面直接在注册中心写信息就够了。



# 2 RegistryDirectory是啥意思？如果有三个zk，有几个directory？几个registry？
一个目录其实就是很容易想到就是一个dubbo提供者的interfacename在zk上面的/duboo+interfacename的目录，但不仅仅是这样，如果有多个zk，那么有三个目录，因为这三个目录在不同的zk上面。

一个消费者在初始化得到引用的时候，在loadRegistries里面，如果url里面有多个以分号隔开的注册中心的ip+port，那么就得到多个注册中心的url，那么每个url都需要经过registryProcol处理，具体来说createProxy里面就是要对每个注册url进行refer操作

所以directory的构造函数里面有两个东西来限定唯一性：注册中心url和provider的interfacename。对于一个消费者，假设只有一个provider的interfacename需要引用的话，在三个zk的前提下，那么要维护三个registry，三个registry都各自有自己的directory。消费者初始化引用的时候每个directory和对应的registry都需要一个方面跟提供者一样，去不同的zk上面的consumer写入自己的信息，另外要分别订阅这个interfaceface路径下的provider、configure、route信息。第一次订阅的时候，顺便把provider的具体信息都存在directory的methodInvokerMap中，以后要调用的时候就从这里取。



# 3 dubbo里面经常说的FailoverClusterinvoer、BroadcastClusterInvoker等等这些Cluster是啥意思？

​		首先cluster接口只有一个方法，就是通过join得到一个`invoker`，不要被名字误导了，虽然叫`cluster`，其实没有保存多个invoker，并不是保存了一个集合（此集合保存在具体的`Invoker`中）。虽然叫join，但是其实是利用spi根据配置得到不同的cluster，可以理解成：cluster的join就是根据配置得到不同ClusterInvoker实现。当然还有一个`MockClusterWrapper`，所以所有的Cluster其实都被这个Wrapper都包了一层，这个是dubbo的spi注入做的，看名字就知道加这一层是为了提前拦截，方便mock测试用的。



# 4 Cluster和loadbalance的关系

不同的Cluster的实现里面都有一个doInvoke方法，dubbo提供者被调用的时候都会走这个方法，因为cluster本质也是一个invoker，不同的Cluster具有不同的doInvoke实现，在遇到多个invoker调用不顺利情况下 做不同处理。
而loadbalance不同，他是用来挑选优先使用哪个invoker的，一般有随机、一致性哈希，这个也是spi来挑选的。在cluster的共同的父类实现中，也就是在doinvoke之前，都会调用一次loadbalance的select来选出优先的invoker



# 5 dubbo与zk的关系咋样的，dubbo怎么被zk通知的？

我们说过provider是不需要监听的，消费者才要。我感觉dubbo的zklistener太绕了，dubbox稍微好点。消费者在两个地方进行监听：与zk连接的statechanged回调以及自己引用的interface目录的provider、configure、route目录发生变化的时候，也会有childchanged的回调。
statechanged回调的注册应该比较好找，就是zkclient去连接zk的时候，建立的回调注册。这个里的回调处理是，一旦发现重连，那么把已经注册的、已经订阅的全部划分到failed的list里面，zkregistry有一个心跳，定期检查这些failed的list里面是不是有数据，如果有那么重新注册、订阅。
childchanged是在订阅的时候注册的，也就是doSubscribe里面。当provider、configure、route发生变化的时候，最终调用到RegistryDirectory的notify方法，回调的传过来的参数是当前这个目录比如provider目录下面的实际子节点的全部信息。以provider目录为例子，拿到这些重要信息以后，RegistryDirectory就会根据新的provider的url-list，做一次refreshInvoker，做之前还把缓存的文件也根据这个更新的。

所以说consumer就是通过这个回调感知provider的变化的，看得出来如果zk回调出问题，dubbo就找不到提供者，并且缓存文件也坏了。并且dubbo没有主动去轮训检查zk的当前信息，这块还是比较脆弱的。
我看dubbo官网说的是，阿里内部没用zk，而是用自己的数据库作为注册中心。



# 6 refreshInvoker 里面做了啥？
前面只是讲到得到了一个provider的url、list，并没有得到一个provider的实体，其实有了provider，需要使用dubboProcol的refer去真正引用一个service，与service建立长链接关系。底层建立transport层的通信关系。
至于序列化那块，默认用的hessian，比较繁琐。dubbo没有用protobuf，如果用的话，性能更好，并且代码应该也不需要写这么多。



# 7 消费者refer得到的到底是啥？
根据前面提到的，我们可以简单总结下，假如有三个zk，那么dorefer干下面几个事情：
创建三个registry、三个directory。
与三个registry建立长链接，创建consumer目录上自己的信息，这个叫注册。
然后订阅provider的信息，并且把provider的url信息拿到后refreshInvoker
与这些dubbo-invoker建立连接关系

# 8 那么问题来了，refer方法结果是dubbo-invoker吗？如果是的话 就用不到cluster、loadbalance这些了。
在doRefer方法的最后，还是调用了cluster.join(directory)得到invoke返回回去。也就是最终返回了三个MockClusterWrapper，里面是FailoverClusterInvoker（默认spi）
对于directory或者说registry做了一次cluster.join，这是因为一个directory可能相同的版本的provider都不止一个，所以不同的provider是一个cluster，这里面存在一个选择。

刚刚外面说了返回了三个MockClusterWrapper，但是consumer或者说我们的业务使用者来说，不应该感知这些，所以对于三个注册中心的三个MockClusterWrapper还要做一次cluster.join：
URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
invoker = cluster.join(new StaticDirectory(u, invokers));
这次是指定用了AvailableCluster，于是又返回了一个MockClusterInvoker，里面包裹了真正的AvailableCluster，这个AvailableCluster的doInvoke方法就是遍历自己的invoke-list，只要可用就用这个，也就是说三个zk，只要有一个没问题，就直接用这个zk的url代表的provider
当然这个ref还要做一次proxy动态代理才能真正返回给用户使用

# 9 Route是干啥用的，在哪里生效的？
在执行真正的invoke时候，为了使用cluster、loadbalance，必须经过clusterinvoke，在虚基类AbstractClusterInvoker的doInvoke方法中，首先是利用directory.list针对这个invocation做一次筛选，
List<Invoker<T>> invokers = doList(invocation);可以不用考虑，就是根据调用方法拿到invokers（这里的invokers都是通过refreshinvokers拿到的），对于StaticDirectory直接返回所有invokers。
然后会用默认的MockInvokersSelector做一次route筛选处理，通过名字知道还是为了mock调试用的。其他的route还有ConditionRouter和ScriptRouter，路由规则决定一次 dubbo 服务调用的目标服务器。dubbo官网目前是试用阶段。

# 10 一次调用进行了两次cluster的选择
一次调用，首先选择任一一个可用registry或者说directory的invoker，也就是确定用这个注册中心。后面还要选择到底用这个注册中心的哪个provider，默认spi是FailoverClusterinvoer，然后走loadbalance、cluster逻辑选出真正的invoker，走dubbo调用流程





# 11 一机器上的service在两个不同的zk上面，有两个registry，那么是不是也有两个dubboinvoker？
不是的，在doLocalExport里面通过ip-端口-interfacename以及其他service的url作为key，存到map里面后面如果相同，那么服用者dubboinvoker
那么，一台机器上不同的service肯定用的是不同的dubboinvoker，毕竟接口不一样、动态代理都不一样



# 12 一台机器不同的service用的同一个transport的server吗，如果用的NETTY4，是不是意味着服用了一个netty-server？
DubboProtocol里面的openServer（url）方法中，ExchangeServer server = serverMap.get(key);这个key就是ip+port。也就是说只要service用的ip+port是一样的，那么就是复用netty-server


# 13 provider在注册的时候，也subscribe了，那到底订阅了啥？
getSubscribedOverrideUrl里面，addParameters(Constants.CATEGORY_KEY, Constants.CONFIGURATORS_CATEGORY,
默认给自己的catogary是configures目录。在后面doSubscribe方法里面，通过for (String path : toCategoriesPath(url)) 拿到自己关心的目录，也就是configure目录了，而不是providers目录。

如果configure目录有变化，那么会走到RegistryProtocol的OverrideListener里面，通过把configureUrl和当前的url进行合并，再与当前url比较，如果不一样，那么基于dubboprotocol重新export一次。从而让configure上面的url生效

# 14 SPI扩展

# 15 Adaptive自适应

# 16 Active激活

# 17 Dubbo序列化

## 一、通信协议

Dubbo支持dubbo、rmi、hessian、http、webservice、thrift、redis等多种协议，但是Dubbo官网是推荐我们使用Dubbo协议的，默认也是用的dubbo协议。

先介绍几种常见的协议：

### 1. dubbo协议
缺省协议，使用基于mina1.1.7+hessian3.2.1的tbremoting交互。
连接个数：单连接
连接方式：长连接
传输协议：TCP
传输方式：NIO异步传输
序列化：Hessian二进制序列化
适用范围：传入传出参数数据包较小（建议小于100K），消费者比提供者个数多，单一消费者无法压满提供者，尽量不要用dubbo协议传输大文件或超大字符串。
适用场景：常规远程服务方法调用

1、dubbo默认采用dubbo协议，dubbo协议采用单一长连接和NIO异步通讯，适合于小数据量大并发的服务调用，以及服务消费者机器数远大于服务提供者机器数的情况
2、他不适合传送大数据量的服务，比如传文件，传视频等，除非请求量很低。
配置如下：

<dubbo:protocol name="dubbo" port="20880" />
<dubbo:protocol name=“dubbo” port=“9090” server=“netty” client=“netty” codec=“dubbo”
serialization=“hessian2” charset=“UTF-8” threadpool=“fixed” threads=“100” queues=“0” iothreads=“9”
buffer=“8192” accepts=“1000” payload=“8388608” />
3、Dubbo协议缺省每服务每提供者每消费者使用单一长连接，如果数据量较大，可以使用多个连接。

<dubbo:protocol name="dubbo" connections="2" />
4、为防止被大量连接撑挂，可在服务提供方限制大接收连接数，以实现服务提供方自我保护

<dubbo:protocol name="dubbo" accepts="1000" />

###  2. rmi协议
Java标准的远程调用协议。
连接个数：多连接
连接方式：短连接
传输协议：TCP
传输方式：同步传输
序列化：Java标准二进制序列化
适用范围：传入传出参数数据包大小混合，消费者与提供者个数差不多，可传文件。
适用场景：常规远程服务方法调用，与原生RMI服务互操作

RMI协议采用JDK标准的java.rmi.*实现，采用阻塞式短连接和JDK标准序列化方式 。



### 3. hessian协议
基于Hessian的远程调用协议。
连接个数：多连接
连接方式：短连接
传输协议：HTTP
传输方式：同步传输
序列化：表单序列化
适用范围：传入传出参数数据包大小混合，提供者比消费者个数多，可用浏览器查看，可用表单或URL传入参数，暂不支持传文件。
适用场景：需同时给应用程序和浏览器JS使用的服务。

1、Hessian协议用于集成Hessian的服务，Hessian底层采用Http通讯，采用Servlet暴露服务，Dubbo缺省内嵌Jetty作为服务器实现。
2、Hessian是Caucho开源的一个RPC框架：http://hessian.caucho.com，其通讯效率高于WebService和Java自带的序列化。



### 4. http协议
基于http表单的远程调用协议。参见：[HTTP协议使用说明]
连接个数：多连接
连接方式：短连接
传输协议：HTTP
传输方式：同步传输
序列化：表单序列化
适用范围：传入传出参数数据包大小混合，提供者比消费者个数多，可用浏览器查看，可用表单或URL传入参数，暂不支持传文件。
适用场景：需同时给应用程序和浏览器JS使用的服务。


### 5. webservice协议

基于WebService的远程调用协议。 
连接个数：多连接 
连接方式：短连接 
传输协议：HTTP 
传输方式：同步传输 
序列化：SOAP文本序列化 
适用场景：系统集成，跨语言调用



## 二 序列化

序列化是将一个对象变成一个二进制流就是序列化， 反序列化是将二进制流转换成对象。

为什么要序列化？

1. 减小内存空间和网络传输的带宽
2. 分布式的可扩展性
3.  通用性，接口可共用。

Dubbo序列化支持`java`、`compactedjava`、`nativejava`、`fastjson`、`dubbo`、`fst`、`hessian2`、`kryo`，其中默认`hessian2`。其中java、compactedjava、nativejava属于原生java的序列化。

dubbo序列化：阿里尚未开发成熟的高效java序列化实现，阿里不建议在生产环境使用它。

`hessian2`序列化：hessian是一种跨语言的高效二进制序列化方式。但这里实际不是原生的hessian2序列化，而是阿里修改过的，它是dubbo RPC默认启用的序列化方式。
`json`序列化：目前有两种实现，一种是采用的阿里的fastjson库，另一种是采用dubbo中自己实现的简单json库，但其实现都不是特别成熟，而且json这种文本序列化性能一般不如上面两种二进制序列化。
java序列化：主要是采用JDK自带的Java序列化实现，性能很不理想。

dubbo序列化主要由Serialization(序列化策略)、DataInput(反序列化，二进制->对象)、DataOutput（序列化，对象->二进制流） 来进行数据的序列化与反序列化。其关系类图为：

# 18、本地存根和本地伪装

##　本地存根(Stub)

典型的 RPC 调用客户端是依赖并且只依赖接口编程来进行远程调用的。在真正发起远程调用之前，用户往往需要做一些预处理的工作，比如提前校验参数。在拿到返回调用结果之后，用户可能需要缓存结果，或者是在调用失败的时候构造容错数据，而不是简单的抛出异常。

这个时候，用户可以编写出类似以下的代码来处理上面提出的这些场景：

```java
try {
    preProcess();
    return service.invoke(...);
} catch (Throwable e) {
    return mockValue;
} finally {
    postProcess();
}
```

类似的，用户也可以通过面向切面编程 *AOP* 的高级技巧来解决上面的诉求，比如通过 *Spring AOP* 的方式可以通过类似下面的这段配置来完成。使用 *AOP* 的技巧相比上面的代码来说，避免了容错处理等与业务无关的代码对业务代码的侵入，使得业务处理主逻辑更简洁。

```xml
<bean id="demo-service-stub" class="org.apache.dubbo.demo.DemoServiceStub"/>
<bean id="demo-service-mock" class="org.apache.dubbo.demo.DemoServiceMock"/>
<aop:config>
    <aop:aspect id="stub" ref="demo-service-stub">
        <aop:pointcut id="stubPointcut" expression="execution(* org.apache.dubbo.samples.DemoService+.*(..))"/>
        <aop:before method="preProcess" pointcut-ref="stubPointcut"/>
        <aop:after-returning method="postProcess" pointcut-ref="stubPointcut"/>
    </aop:aspect>
    <aop:aspect id="mock" ref="demo-service-mock">
        <aop:pointcut id="mockPointcut" expression="execution(* org.apache.dubbo.samples.DemoService+.*(..))"/>
        <aop:after-throwing method="mock" pointcut-ref="mockPointcut"/>
    </aop:aspect>
</aop:config>
```

为了进一步的方便用户做 Dubbo 开发，框架提出了本地存根 *Stub* 和本地伪装 *Mock* 的概念。通过约定大于配置的理念，进一步的简化了配置，使用起来更加方便，并且不依赖额外的 *AOP* 框架就达到了 *AOP* 的效果。

**本地存根的工作方式与 *AOP* 的 around advice 类似，而本地伪装的工作方式等同于 *AOP* 中的 after-throwing advice，也就是说，只有当远程调用发生 *exception* 的时候才会执行本地伪装**。本地存根和本地伪装的工作流程如下图所示：

 ![dubbo-mock-stub-flow](./images/99/1.png) 



要和框架在一起工作，本地存根的实现需要遵循一些与框架事先做出的约定：

1. 首先本地存根 *Stub* 是服务接口的一个实现
2. 本地存根的实现需要提供一个拷贝构造方法，方便框架将远程调用的 *Proxy* 对象注入进来
3. 同样的，本地存根需要提供服务接口中所有方法的实现。在本例中，需要实现 *sayHello* 方法
4. 在真正发起远程调用之前，用户可以在本地执行一些操作。在本例中，在日志中记录传入的参数
5. 通过框架传入的 *Proxy* 对象真正发起远程调用
6. 在远程调用结束后，也可以加入本地代码的执行。在本例中，在日志中记录远程调用的返回结果
7. 如果发生错误的时候，也可以做一些错误恢复的动作。在本例中，在日志中记录异常。当然，如果提供了本地伪装的话，*catch* 中的逻辑是可以省略掉的

其中步骤 4、步骤 6、和步骤 7 共同构建了等同于面向切面编程中的概念，分别对应于 **before**、**after-returning**、以及 **after-throwing**。

一个示例Demo如下：

```java
/**
 * @author luo.yongqian
 */
// 1、实现接口
public class DemoServiceStub implements DemoService {
    //被代理的服务接口<具体实现是Proxy0>
    private DemoService demoService;
    
	// 2、有一个拷贝构造方法函数，在构造函数中将Proxy0实例赋值，完成初始化
    public DemoServiceStub(DemoService demoService) {
        this.demoService = demoService;

    }
	//3、实现接口方法
    @Override
    public String sayHello(String name) throws Throwable {
        //实现前置拦截器效果
        System.out.println("before inoker...");
        //调用服务
        String res = demoService.sayHello(name);
        //实现后置拦截器效果
        System.out.println("receive response from providre: " + name);
        return res;
    }

    @Override
    public String sayHelloMock(int num) throws Throwable {
        System.out.println("before inoker...");
        String res = demoService.sayHelloMock(num);
        System.out.println("receive response from providre: " + num);
        return res;
    }

}
```

*DemoServiceStub* 运行在客户端，要使用本地存根的话，还需要在 *stub-consumer.xml* 中配置属性 *stub*。可以简单的通过指定 *stub="true"* 来告诉 Dubbo 框架使用本地存根，这个时候，本地存根的包名需要和服务接口的包名一致，类名必须在服务接口的类名后加上 **Stub** 的后缀。例如，当服务接口名是 *org.apache.dubbo.samples.stub.api.DemoService* 时，本地存根的全类名应该是 *org.apache.dubbo.samples.stub.api.DemoServiceStub*。

```xml
<dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.samples.stub.api.DemoService" stub="true"/>
```

如果不希望使用默认的命名规则，也可以直接通过 *stub* 属性来指定本地存根的全类名。

```xml
<dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.samples.stub.api.DemoService" stub="org.apache.dubbo.samples.stub.impl.DemoStub"/>
```

启动服务端 *StubProvider* 后，再运行客户端 *StubConsumer*，可以通过观察客户端的日志来验证本地存根的运行结果。

```bash
[09/04/19 11:52:21:021 CST] main  INFO api.DemoServiceStub: before execute remote service, parameter: dubbo
[09/04/19 11:52:21:021 CST] main  INFO api.DemoServiceStub: after execute remote service, result: greeting dubbo
[09/04/19 11:52:21:021 CST] main  INFO stub.StubConsumer: result: greeting dubbo
```



## 本地伪装(Mock)

1. Mock 是 Stub 的一个子集，便于服务提供方在客户端执行容错逻辑，因经常需要在出现 RpcException (比如网络失败，超时等)时进行容错，而在出现业务异常(比如登录用户名密码错误)时不需要容错，如果用 Stub，可能就需要捕获并依赖 RpcException 类，而用 Mock 就可以不依赖 RpcException，因为**它的约定就是只有出现 RpcException 时才执行**。 [↩︎](http://dubbo.apache.org/zh-cn/docs/user/demos/local-mock.html#fnref1)
2. 在 interface 旁放一个 Mock 实现，它实现 BarService 接口，并有一个无参构造函数 [↩︎](http://dubbo.apache.org/zh-cn/docs/user/demos/local-mock.html#fnref2)
3. 代码示例：

```java
//1、实现接口
public class DemoServiceMock implements DemoService {
    //2、默认的无参构造函数(可以显示编写也可以使用java默认)，但必须要有无参构造函数，否则会报错导致系统无法启动。
    //3、实现接口中的所有方法
    @Override
    public String sayHello(String name) throws Throwable {
        System.out.println("start mock ...");
        return "系统繁忙(Mock返回)";
    }
    
    @Override
    public String sayHelloMock(int num) throws Throwable {
        System.out.println("start mock ...");
        return "系统繁忙(Mock返回)";
    }
}
```



# 19 Dubbo能否脱离Spring独立运行？

不能。Dubbo对Spring是强依赖，但必须依赖的仅仅是SpringFrameWork的核心框架(**最小依赖**)：

- com.alibaba:dubbo:2.6.1  

  -  commons-logging:commons-logging:1.2  
  - org.springframework:**spring-beans**:5.1.4.RELEASE  
    - org.springframework:spring-core:5.1.4.RELEASE  
  - org.springframework:**spring-context**:5.1.4.RELEASE  
    - org.springframework:spring-aop:5.1.4.RELEASE  
    - org.springframework:spring-beans:5.1.4.RELEASE  
    - org.springframework:spring-core:5.1.4.RELEASE  
    - org.springframework:spring-expression:5.1.4.RELEASE  
  - org.springframework:**spring-aop**:5.1.4.RELEASE  
    - org.springframework:spring-beans:5.1.4.RELEASE  
  - org.springframework:**spring-core**:5.1.4.RELEASE  
    - org.springframework:spring-core:5.1.4.RELEASE  
    - org.springframework:spring-jcl:5.1.4.RELEASE  
  - org.springframework:**spring-expression**:5.1.4.RELEASE  
    - org.springframework:spring-core:5.1.4.RELEASE  
  - org.javassist:javassist:3.20.0-GA  
  - org.jboss.netty:netty:3.2.5.Final  
  - org.objenesis:objenesis:2.2 

  

但Dubbo可以脱离tomcat等servlet容器独立运行。在`2.6.1`版本下，最小依导入依赖如下：

```xml
<dependencies>
        <!-- alibaba demo -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>dubbo</artifactId>
            <version>2.6.1</version>
        </dependency>
        <!-- http://curator.apache.org/index.html
         Apache Curator是Apache ZooKeeper的Java / JVM客户端库，Apache ZooKeeper是一种分布式协调服务。它包括一个高级API框架和实用程序，使Apache ZooKeeper更容易和更可靠。
         -->
        <dependency>
            <groupId>org.apache.curator</groupId>
            <artifactId>curator-framework</artifactId>
            <version>${curator_version}</version>
        </dependency>
    </dependencies>
```

# 20、默认的Filter有哪些？如果Provider和Service都配置了相同的Filter会不会覆盖？

Filter链构造在类`com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper`中完成。

- 默认的Filter如下列表
  - filters = {ArrayList@2255}  size = 8
    -  0 = {EchoFilter@2263} 
    -  1 = {ClassLoaderFilter@2264} 
    -  2 = {GenericFilter@2265} 
    -  3 = {ContextFilter@2266} 
    -  4 = {TraceFilter@2267} 
    -  5 = {TimeoutFilter@2268} 
    -  6 = {MonitorFilter@2269} 
    -  7 = {ExceptionFilter@2270} 
- 不会覆盖，而是出现两次，调用两次。例如在配置中加入如下配置：

```xml
<!-- dubbo provider: 提供者全局配置-->
<dubbo:provider delay="0" filter="providerFilterDemo"></dubbo:provider>
<!-- declare the service interface to be exported -->
<dubbo:service
    protocol="p1"
    id="a"
    interface="com.alibaba.dubbo.demo.DemoService"
    ref="demoService"
    version="0.0.2"
    group="a"
    filter="providerFilterDemo"
/>
```

此时`Filter`列表为：

- filters = {ArrayList@2255}  size = 10
  -  0 = {EchoFilter@2263} 
  -  1 = {ClassLoaderFilter@2264} 
  -  2 = {GenericFilter@2265} 
  -  3 = {ContextFilter@2266} 
  -  4 = {TraceFilter@2267} 
  -  5 = {TimeoutFilter@2268} 
  -  6 = {MonitorFilter@2269} 
  -  7 = {ExceptionFilter@2270} 
  - **8 = {ProviderFilterDemo@2256} **
  -  **9 = {ProviderFilterDemo@2256} **

服务提供方和服务消费方调用过程拦截，Dubbo 本身的大多功能均基于此扩展点实现，每次远程方法执行，该拦截都会被执行，请注意对性能的影响。

约定：

- 用户自定义 filter 默认在内置 filter 之后。
- 特殊值 `default`，表示缺省扩展点插入的位置。比如：`filter="xxx,default,yyy"`，表示 `xxx` 在缺省 filter 之前，`yyy` 在缺省 filter 之后。
- 特殊符号 `-`，表示剔除。比如：`filter="-foo1"`，剔除添加缺省扩展点 `foo1`。比如：`filter="-default"`，剔除添加所有缺省扩展点。
- provider 和 service 同时配置的 filter 时，累加所有 filter，而不是覆盖。比如：`` 和 ``，则 `xxx`,`yyy`,`aaa`,`bbb` 均会生效。如果要覆盖，需配置：``

# 21、Dubbo为什么要自己实现Container？具体实现有哪些?

服务容器(`Container`)是一个`standalone`的启动程序，因为后台服务不需要Tomcat或JBoss等Web容器的功能，如果硬要用Web容器去加载服务提供方，增加复杂性，也浪费资源。
服务容器只是一个简单的Main方法，并加载一个简单的Spring容器，用于暴露服务。
服务容器的加载内容可以扩展，内置了spring, jetty, log4j等加载，可通过Container扩展点进行扩展，参见：`Container`。

- Spring Container

 这个容器是必须的，不能少的。如果少了就不能解析Dubbo配置文件，因此也不对对外提供服务。

  自动加载META-INF/spring目录下的所有Spring配置。
  配置：(配在java命令-D参数或者dubbo.properties中)
    dubbo.spring.config=classpath*:META-INF/spring/*.xml ----配置spring配置加载位置

- Log4j Container

  自动配置log4j的配置，在多进程启动时，自动给日志文件按进程分目录。
  配置：(配在java命令-D参数或者dubbo.properties中)
    dubbo.log4j.file=/foo/bar.log ----配置日志文件路径
    dubbo.log4j.level=WARN ----配置日志级别
    dubbo.log4j.subdirectory=20880 ----配置日志子目录，用于多进程启动，避免冲突

- Logback Container

  与Log4j Container类似

## 容器启动

如：(缺省只加载spring)
java com.alibaba.dubbo.container.Main

或：(通过main函数参数传入要加载的容器)
java com.alibaba.dubbo.container.Main spring jetty log4j

或：(通过JVM启动参数传入要加载的容器)
java com.alibaba.dubbo.container.Main -Ddubbo.container=spring,jetty,log4j

或：(通过classpath下的dubbo.properties配置传入要加载的容器)
dubbo.properties
dubbo.container=spring,jetty,log4j

# Dubbo如何实现优雅停机？

基础java中的shutdown hook。在`AbstractConfig`类中实现：

```java
    /**
     * 添加系统关闭钩子，进行相关销毁操作
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                if (logger.isInfoEnabled()) {
                    logger.info("Run shutdown hook now.");
                }
                ProtocolConfig.destroyAll();
            }
        }, "DubboShutdownHook"));
    }

```

因此，如果想要实现优雅停，不要使用kill -9 命令，因为这会跳过钩子函数直接杀死进程。而应该使用kill默认或者kill -15 。

# 22、Dubbo多进程启动是什么？端口冲突怎么处理？

Dubbo多进程是指在一台服务器，一个Dubbo服务(应用/项目)启动多次(多个进程)，对外提供服务。

如果一个Dubbo服务启动多次，端口已经被占用了怎么办？很简单，Dubbo集成了Spring，因此可以通过Spring配置文件的点位符功能 ，在启动时指定端口的相关参数 ，对当前启动的监听端口进行指定。

例如：

配置文件：

```xml
< beans xmlns = "http://www.springframework.org/schema/beans"
     xmlns:xsi = "http://www.w3.org/2001/XMLSchema-instance"
     xmlns:dubbo = "http://repo.alibaba-inc.com/schema/dubbo"
     xsi:schemaLocation = "http://www.springframework.org/schema/beanshttp://www.springframework.org/schema/beans/spring-beans.xsdhttp://repo.alibaba-inc.com/schema/dubbohttp://repo.alibaba-inc.com/schema/dubbo/dubbo-component.xsd" >
     <!-- 使用Spring自带的占位符替换功能 -->
     < bean class = "org.springframework.beans.factory.config.PropertyPlaceholderConfigurer" >
         <!-- 系统-D参数覆盖 -->
         < property name = "systemPropertiesModeName" value = "SYSTEM_PROPERTIES_MODE_OVERRIDE" />
         <!-- 指定properties配置所在位置 -->
         < property name = "location" value = "classpath:xxx.properties" />
     </ bean >
 
     <!-- 使用${}引用配置项 -->
     < dubbo:provider port = "${dubbo.service.server.port}" />
 
</ beans >

```



```shell
`java -Ddubbo.service.server.port=20881`
```

# 23、Dubbo多进程启动，日志怎么处理？是不是混在一起？

Dubbo有一个模块是Container的子模块：

- dubbo-container-logback
- dubbo-container-log4j

在些模块中实现了多进程时对应多个日志文件。即不同的Dubbo进程有不同的日志文件，互不干扰。

# 参考资料

[Regitstry,route,cluster,loadbalance,directory关系汇总](https://www.cnblogs.com/notlate/p/10088829.html)

[SPI Adaptive]( https://segmentfault.com/a/1190000020384210 )

[序列化协议]( https://www.cnblogs.com/jameszheng/p/10271341.html )

[本地存根STUB与MOCK]( http://dubbo.apache.org/zh-cn/blog/dubbo-stub-mock.html )