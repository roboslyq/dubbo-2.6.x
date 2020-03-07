# 05-DUBBO之服务端启动流程

## 暴露服务时序

首先来看官网发布的服务提供方暴露服务的蓝色初始化链，时序图如下：

![/dev-guide/images/dubbo-export.jpg](./images/03/5.jpg)

官网提供的时序比较粗，只有个大概方向。我自己根据官网的时序图作了一更详细的。但总体流程是一样的。

![2](./images/05/2.png)



## 1、入口NameSpaceHandler与DubboBeanDefinitionParser

`NameSpaceHandler`和`DubboBeanDefinitionParser`是Spring标准扩展实现。可以复用此特性与Spring框架实现整合。

其中，服务提供者在`NameSpaceHandler`中如下：

```java
public class DubboNamespaceHandler extends NamespaceHandlerSupport {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }

}
```

请注意上面的**ServiceBean.class**,这就是服务提供者的配置抽象，此处是服务提供者的启动时入口所在。



## 2、ServiceBean

### 2.1 总体的Config继承体系

![/dev-guide/images/dubbo-export.jpg](./images/05/1.png)

在Dubbo体系中，有一个总的配置抽象类`AbstractConfig`，所有配置类均继承于此类。ServiceBean是针对具体每一个Bean的配置。每一个配置对应一个ServiceBean。



### 2.2 ServiceBean继承体系

![/dev-guide/images/dubbo-export.jpg](./images/05/1_1.jpg)

可见，`ServiceBean`继承于`ServiceConfig`。Dubbo可以脱离Spring框架独立存在，此时作用就是`ServiceConfig`。为了集成到Spring框架中，才有了`ServiceBean`实现类。

## Protocol自适应

在`ServiceConfig`中有一个方法获取Protocol类，此类是动态生成的`自适应类`。生成类型是Protocol$Adaptive

```java
    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

```

`Protocol$Adaptive`源码如下：

```java
package com.alibaba.dubbo.demo.consumer;

/**
 * 此类通过断点生成，在ExtensionLoader.createAdaptiveExtensionClass()方法中的code变量
 */
//package com.alibaba.dubbo.rpc;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
//类名XXXX$Adaptive，如Protocol$Adaptive
public class Protocol$Adaptive implements com.alibaba.dubbo.rpc.Protocol {
	public void destroy() {
		throw new UnsupportedOperationException(
				"method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
	}

	public int getDefaultPort() {
		throw new UnsupportedOperationException(
				"method public abstract int com.alibaba.dubbo.rpc.Protocol.getDefaultPort() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
	}
	//Adaptive方法，自适应生成
	public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1)
			throws com.alibaba.dubbo.rpc.RpcException {
		if (arg1 == null)
			throw new IllegalArgumentException("url == null");
		com.alibaba.dubbo.common.URL url = arg1;
		//默认dubbo协议，自适应
		String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
		if (extName == null)
			throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
					+ url.toString() + ") use keys([protocol])");
		//根据extName获取具体的扩展点。防止if代码，并且方便客户端随意扩展
		com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader
				.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
		return extension.refer(arg0, arg1);
	}

	public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0)
			throws com.alibaba.dubbo.rpc.RpcException {
		if (arg0 == null)
			throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
		if (arg0.getUrl() == null)
			throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
		com.alibaba.dubbo.common.URL url = arg0.getUrl();
		String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
		if (extName == null)
			throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
					+ url.toString() + ") use keys([protocol])");
		com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader
				.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
		return extension.export(arg0);
	}
}
```



# 参考资料

https://www.jianshu.com/p/7f3871492c71
https://mp.weixin.qq.com/s/J1yUqFPN6Cf9M01W0paYcA
http://dubbo.apache.org/zh-cn/docs/source_code_guide/export-service.html