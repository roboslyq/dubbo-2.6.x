# 1、配置中心

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





## 2、暴露服务

### dubbo-common

- ExtensionLoader.java

### dubbo-config-api

- ApplicationModel.java

- AbstractInterfaceConfig.java
- ReferenceConfig.java
- ServiceConfig.java

### dubbo-config-spring

- ServiceBean.java

### dubbo-registry-api

- RegistryProtocol.java

### dubbo-remoting-api

- Exchanger.java
- AbstractServer.java
- Transporters.java

### dubbo-remoting-netty

- NettyServer.java
- NettyTransporter.java

### dubbo-rpc-api

- ProtocolFilterWrapper.java
- ProtocolListenerWrapper.java

### dubbo-rpc-default

- DubboProtocol.java