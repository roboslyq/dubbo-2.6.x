# 

## DubboNameSpaceHandler
- DubboBeanDefinitionParser
- AnnotationBeanDefinitionParser
- ApplicationConfig
- ModuleConfig
- RegistryConfig
- MonitorConfig
- ProviderConfig
- ConsumerConfig
- ProtocolConfig
- ServiceBean
- ReferenceBean

## dubbo-common

- ExtensionLoader.java
- Proxy.java

## dubbo-config-api

- ApplicationModel.java
- AbstractInterfaceConfig.java
- ReferenceConfig.java
- ServiceConfig.java
- ProviderConfig.java
- ReferenceConfig.java

## dubbo-config-spring

- ServiceBean.java
- ReferenceBean.java

## dubbo-registry-api

- RegistryProtocol.java
- RegistryDirectory.java
- AbstractRegistry.java
- AbstractRegistryFactory.java
- FailbackRegistry.java

##  dubbo-registry-zookeeper

- ZookeeperRegistry.java
- ZookeeperRegistryFactory.java

## dubbo-remoting-api

- Exchanger.java
- AbstractServer.java
- Transporters.java
- JavassistProxyFactory.java
- AbstractProxyFactory.java
- InvokerInvocationHandler.java

## dubbo-rpc-default

- DubboProtocol.java

## dubbo-remoting-netty

- NettyServer.java
- NettyTransporter.java

## dubbo-rpc-api

- `ProtocolFilterWrapper.java`

> 基于扩展点自适应机制，所有的 `Protocol` 扩展点都会自动套上 `Wrapper` 类。
>
> 基于 `ProtocolFilterWrapper` 类，将所有 `Filter` 组装成链，在链的最后一节调用真实的引用。



- `ProtocolListenerWrapper.java`

> 基于扩展点自适应机制，所有的 `Protocol` 扩展点都会自动套上 `Wrapper` 类。
>
> 基于 `ProtocolListenerWrapper` 类，将所有 `InvokerListener` 和 `ExporterListener` 组装集合，在暴露和引用前后，进行回调。
>
> 包括监控在内，所有附加功能，全部通过 `Filter` 拦截实现。

## dubbo-rpc-default

- DubboProtocol.java

## dubbo-cluster

AbstractClusterInvoker.java
AvailableCluster.java
BroadcastClusterInvoker.java
FailbackClusterInvoker.java
Cluster.java
Directory.java