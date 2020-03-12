## dubbo-common

- ExtensionLoader
- Proxy

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

- Exchanger
- AbstractServer
- Transporters
- JavassistProxyFactory
- AbstractProxyFactory
- InvokerInvocationHandler

## dubbo-rpc-default

- DubboProtocol

## dubbo-remoting-netty

- NettyServer
- NettyTransporter

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

## dubbo-rpc-default

- DubboProtocol

## dubbo-cluster

- AbstractClusterInvoker
- AvailableCluster
- BroadcastClusterInvoker
- FailbackClusterInvoker
- Cluster
- Directory