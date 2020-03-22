## dubbo-common

- ExtensionLoader

> ```
> DUBBO SPI 机制的实现
> 
> 一、ExtensionLoader:
> 	拓展加载器,Dubbo使用的扩展点获取： 
> 1、来源  Dubbo 的扩展点加载从 JDK 标准的 SPI (Service Provider Interface) 扩展点发现机制加强而来。  Dubbo 改进了 JDK 标准的 SPI 的以下问题：      
> 	(1)JDK 标准的 SPI 会一次性实例化扩展点所有实现，如果有扩展实现初始化很耗时，但如果没用上也加载，会很浪费资源。      
> 	(2)如果扩展点加载失败，连扩展点的名称都拿不到了。比如：JDK 标准的 ScriptEngine，通过 getName() 获取脚本类型的名称， 但如果 RubyScriptEngine 因为所依赖的 jruby.jar 不存在，导致 RubyScriptEngine 类加载失败，这个失败原因被吃掉了，和 ruby 对应不起来， 当用户执行 ruby 脚本时，会报不支持 ruby，而不是真正失败的原因。      
> 	(3)增加了对扩展点 IoC 和 AOP 的支持，一个扩展点可以直接 setter 注入其它扩展点。 
> 2、相关特性      
> 	(1)扩展点自动装配（IOC）   
> 		加载扩展点时，自动注入依赖的扩展点。加载扩展点时，扩展点实现类的成员如果为其它扩展点类型，     ExtensionLoader 在会自动注入依赖的扩展点。ExtensionLoader 通过扫描扩展点实现类的所有setter 方法来判定其成员。 即 ExtensionLoader 会执行扩展点的拼装操作。      
> 	(2)扩展点自动包装(AOP)          
> 		自动包装扩展点的 Wrapper 类。ExtensionLoader 在加载扩展点时，如果加载到的扩展点有拷贝构造函数，则判定为扩展点 Wrapper 类。
> 		Wrapper类内容：          
> 		package com.alibaba.xxx;          
> 		import com.alibaba.dubbo.rpc.Protocol;          
> 		public class XxxProtocolWrapper implemenets Protocol {              
> 			Protocol impl;              
> 			public XxxProtocol(Protocol protocol) { impl = protocol; }                  		 // 接口方法做一个操作后，再调用extension的方法                  
> 			public void refer() {                  
> 			//... 一些操作                  
> 			impl.refer();                  
> 			// ... 一些操作              
> 			}              
> 			// ...          
> 		}          
> 		Wrapper 类同样实现了扩展点接口，但是 Wrapper 不是扩展点的真正实现。它的用途主要是用于从 ExtensionLoader 返回扩展点时，包装在真正的扩展点实现外。即从 ExtensionLoader 中返回的实际上是 Wrapper 类的实例，Wrapper 持有了实际的扩展点实现类。 扩展点的 Wrapper 类可以有多个，也可以根据需要新增。通过 Wrapper 类可以把所有扩展点公共逻辑移至 Wrapper 中。新加的 Wrapper 在所有的扩展点上添加了逻辑，有些类似 AOP，即 Wrapper 代理了扩展点。          
> 		以下三个类就是一个简单的示例：          
> 		ProtocolListenerWrapper --> ProtocolFilterWrapper --> DubboProtocol      
> 	(3)扩展点自适应(@Adaptive)          
> 		缺省获得的的扩展点是一个Adaptive Instance。ExtensionLoader 注入的依赖扩展点是一个 Adaptive 实例，直到扩展点方法执行时才决定调用是一个扩展点实现。 Dubbo 使用 URL 对象（包含了Key-Value）传递配置信息。          
> 		扩展点方法调用会有URL参数（或是参数有URL成员）。这样依赖的扩展点也可以从URL拿到配置信息，所有的扩展点自己定好配置的Key后，配置信息从URL上从最外层传入。URL在配置传递上即是一条总线。在 Dubbo 的 ExtensionLoader 的扩展点类对应的 Adaptive 实现是在加载扩展点里动态生成。指定提取的 URL 的 Key 通过 @Adaptive 注解在接口方法上提供。 
> 		下面是 Dubbo 的 Transporter 扩展点的代码：         
> 		public interface Transporter {             
> 		
> 			@Adaptive({"server", "transport"})             
> 			Server bind(URL url, ChannelHandler handler) throws RemotingException;             
> 			@Adaptive({"client", "transport"})             
> 			Client connect(URL url, ChannelHandler handler) throws RemotingException;         }      
> 			
> 	(4)扩展点自动激活(@Activate )          
> 		对于集合类扩展点，比如：Filter, InvokerListener, ExportListener, TelnetHandler, StatusChecker 等，可以同时加载多个实现。此时，可以用自动激活来简化配置，如：         
> 		import com.alibaba.dubbo.common.extension.Activate;         
> 		import com.alibaba.dubbo.rpc.Filter;         
> 		@Activate // 无条件自动激活         
> 		public class XxxFilter implements Filter {
>         // ...         
>         }         
>         或：         
>         import com.alibaba.dubbo.common.extension.Activate;         
>         import com.alibaba.dubbo.rpc.Filter;       
>         // 当配置了xxx参数，并且参数为有效值时激活，比如配了cache="lru"，自动激活CacheFilter。   
>         @Activate("xxx") 
>         public class XxxFilter implements Filter {
>         // ...         
>         }         
>         或：         
>         import com.alibaba.dubbo.common.extension.Activate;         
>         import com.alibaba.dubbo.rpc.Filter;   
>         // 只对提供方激活，group可选"provider"或"consumer"
>         @Activate(group = "provider", value = "xxx")          
>         public class XxxFilter implements Filter {
>         // ...         
>         }         
>         1. 注意：这里的配置文件是放在你自己的 jar 包内，不是 dubbo 本身的 jar 包内，Dubbo 会全 ClassPath 扫描所有 jar 包内同名的这个文件，然后进行合并 ↩         
>         2. 注意：扩展点使用单一实例加载（请确保扩展实现的线程安全性），缓存在 ExtensionLoader 中    	
>        (5)另外，该类同时是 ExtensionLoader 的管理容器，例如 {@link #EXTENSION_INSTANCES} 、{@link #EXTENSION_INSTANCES} 属性。        
> ```

- Proxy
- UrlUtils.java
- Hessian2ObjectOutput.java
- Hessian2Serialization.java
- ObjectOutput.java
- Serialization.java

## dubbo-config-api

- ApplicationModel
- AbstractInterfaceConfig
- ReferenceConfig
- ServiceConfig
- ProviderConfig
- ReferenceConfig
- ApplicationConfig
- ModuleConfig
- RegistryConfig
- MonitorConfig
- ProviderConfig
- ConsumerConfig
- ProtocolConfig

## dubbo-config-spring

- ServiceBean
- ReferenceBean
- DubboNameSpaceHandler

- DubboBeanDefinitionParser
- AnnotationBeanDefinitionParser

## dubbo-registry-api

- RegistryProtocol
- RegistryDirectory
- AbstractRegistry
- AbstractRegistryFactory
- FailbackRegistry

##  dubbo-registry-zookeeper

- ZookeeperRegistry
- ZookeeperRegistryFactory

## dubbo-remoting-api

- HeaderExchangeChannel.java
- HeaderExchangeClient.java
- DefaultFuture.java
- Exchanger
- AbstractServer
- Transporters
- JavassistProxyFactory
- AbstractProxyFactory
- InvokerInvocationHandler
- HeaderExchanger.java
- HeaderExchangeServer.java
- Exchangers.java
- ChannelHandlers.java
- AbstractClient.java
- AbstractServer.java
  ChannelHandler.java
- Transporters.java
- ExchangeCodec.java
- AbstractClient.java
- AbstractCodec.java
- CodecSupport.java

## dubbo-rpc-default

- DubboProtocol
- DubboCodec.java
- DubboInvoker.java
- DubboProtocol.java
- ReferenceCountExchangeClient.java

## dubbo-remoting-netty

- NettyServer
- NettyTransporter
- NettyChannel.java
- NettyClient.java
- NettyClient.java
- NettyTransporter.java
- NettyCodecAdapter.java

## dubbo-rpc-api

- `ProtocolFilterWrapper`

> 基于扩展点自适应机制，所有的 `Protocol` 扩展点都会自动套上 `Wrapper` 类。
>
> 基于 `ProtocolFilterWrapper` 类，将所有 `Filter` 组装成链，在链的最后一节调用真实的引用。



- `ProtocolListenerWrapper`

> 基于扩展点自适应机制，所有的 `Protocol` 扩展点都会自动套上 `Wrapper` 类。
>
> 基于 `ProtocolListenerWrapper` 类，将所有 `InvokerListener` 和 `ExporterListener` 组装集合，在暴露和引用前后，进行回调。
>
> 包括监控在内，所有附加功能，全部通过 `Filter` 拦截实现。



- RpcStatus.java
- JavassistProxyFactory.java
- AbstractProxyFactory.java
- InvokerInvocationHandler.java
- RpcUtils.java
- ListenerInvokerWrapper.java
- AbstractInvoker.java
- InvokerWrapper.java
- ProtocolFilterWrapper.java
- RpcInvocation.java

> 

## dubbo-rpc-default

- DubboProtocol
- CallbackServiceCodec.java
- DubboCodec.java
- DubboProtocol.java

## dubbo-cluster

- AbstractClusterInvoker
- AvailableCluster
- BroadcastClusterInvoker
- FailbackClusterInvoker
- Cluster
- Directory
- AbstractLoadBalance.java
-  LeastActiveLoadBalance.java
- RandomLoadBalance.java
- ConditionRouter.java
- MockClusterInvoker.java
- MockClusterWrapper.java
- AbstractClusterInvoker.java
- FailoverClusterInvoker.java
- Router.java



## dubbo-container-api

Container
Main

