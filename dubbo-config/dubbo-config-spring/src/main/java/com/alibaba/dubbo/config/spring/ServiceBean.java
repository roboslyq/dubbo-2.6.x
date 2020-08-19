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
package com.alibaba.dubbo.config.spring;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一、ServiceFactoryBean
 *  (1)DUBBO集成spring的入口，会执行InitializingBean#afterPropertiesSet 和 ApplicationListener#onApplicationEvent。其中前者的优化级高于后者。
 *  (2)每初始化一个<dubbo:service>标签即会生成一个ServiceBean，执行一次afterPropertiesSet方法。
 *  (3)启动服务器的目的：将每一个服务注册到注册中并且开通一个监听服务用来接收客户端的请求。
 *  (4)服务发布过程会动态加载各种扩展点及字节码生成技术，所以要十分小心。需要通过断点来获取具体生成的字节码内容，才能明白流程怎么走。
 *
 * 二、当ServiceBean被调用时处理过程（以官方示例dubbo-demo项目中的DemoService为例，具体实现为DemoServiceImpl）
 * ChannelEventRunnable#run()
 *   —> DecodeHandler#received(Channel, Object)
 *     —> HeaderExchangeHandler#received(Channel, Object)
 *       —> HeaderExchangeHandler#handleRequest(ExchangeChannel, Request)
 *         —> DubboProtocol.requestHandler#reply(ExchangeChannel, Object)
 *           —> Filter#invoke(Invoker, Invocation)
 *             —> AbstractProxyInvoker#invoke(Invocation)
 *               —> Wrapper0#invokeMethod(Object, String, Class[], Object[])
 *                 —> DemoServiceImpl#sayHello(String)
 * @export
 */
public class ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean, ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>, BeanNameAware {

    private static final long serialVersionUID = 213195494150089726L;
    // 测试初始化次数
    private static int count = 0;
    private static transient ApplicationContext SPRING_CONTEXT;

    private final transient Service service;

    private transient ApplicationContext applicationContext;

    private transient String beanName;

    private transient boolean supportedApplicationListener;

    /**
     * 对应配置：
     *     <dubbo:service delay="0" protocol="p1" id="a" interface="com.alibaba.dubbo.demo.DemoService" ref="demoService" version="0.0.2" group="a"/>
     *     <dubbo:service delay="0"  protocol="p1" id="b" interface="com.alibaba.dubbo.demo.DemoService" ref="demoService1" version="0.0.1" group="b"/>
     * 每一个配置 <dubbo:service>对应一个ServiceBean,所以如果仅是上面的两个配置，ServiceBean会被实例化两次
     */
    public ServiceBean() {
        super();
        System.out.println("ServiceBean初始化次数： "+ ++count+", 当前初始化Service: ");
        this.service = null;
    }

    public ServiceBean(Service service) {
        super(service);
        this.service = service;
    }

    public static ApplicationContext getSpringContext() {
        return SPRING_CONTEXT;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        SpringExtensionFactory.addApplicationContext(applicationContext);
        if (applicationContext != null) {
            SPRING_CONTEXT = applicationContext;
            try {
                Method method = applicationContext.getClass().getMethod("addApplicationListener", new Class<?>[]{ApplicationListener.class}); // backward compatibility to spring 2.0.1
                method.invoke(applicationContext, new Object[]{this});
                supportedApplicationListener = true;
            } catch (Throwable t) {
                if (applicationContext instanceof AbstractApplicationContext) {
                    try {
                        Method method = AbstractApplicationContext.class.getDeclaredMethod("addListener", new Class<?>[]{ApplicationListener.class}); // backward compatibility to spring 2.0.1
                        if (!method.isAccessible()) {
                            method.setAccessible(true);
                        }
                        method.invoke(applicationContext, new Object[]{this});
                        supportedApplicationListener = true;
                    } catch (Throwable t2) {
                    }
                }
            }
        }
    }

    public void setBeanName(String name) {
        this.beanName = name;
    }

    /**
     * Gets associated {@link Service}
     *
     * @return associated {@link Service}
     */
    public Service getService() {
        return service;
    }

    /**
     * ========>如果延迟加载(默认延迟)，此方法则为入口
     */
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (isDelay()   //isDelay()  默认为true,需要手动设置为false
                && !isExported() // isExported()当前服务是否已经导出,正常情况为false.
                && !isUnexported() //是否配置了取消导出服务
                ) {
            if (logger.isInfoEnabled()) {
                logger.info("The service ready on spring started. service: " + getInterface());
            }
            //导出服务
            export();
        }
    }
    //bean是否延迟加截
    private boolean isDelay() {
        //若当前Service单独配置了delay,则以当前Service配置的Delay为准，否则继续判断Provider的公共配置：providerConfig
        Integer delay = getDelay();
        //获取Provider全局通用配置
        ProviderConfig provider = getProvider();
        if (delay == null && provider != null) {
            delay = provider.getDelay();
        }
        return supportedApplicationListener && (delay == null || delay == -1);
    }

    /**
     * afterPropertiesSet() 优先级高于 onApplicationEvent(ContextRefreshedEvent event)
     * @throws Exception
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public void afterPropertiesSet() throws Exception {
    	//刚启动时applicatoin为空
        //如果provider为空，说明dubbo:service标签未设置provider属性，如果一个dubbo:provider标签，则取该实例，
        //如果存在多个dubbo:provider配置则provider属性不能为空，否则抛出异常："Duplicate provider configs"。
        if (getProvider() == null) {
            Map<String, ProviderConfig> providerConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, ProviderConfig.class, false, false);
            if (providerConfigMap != null && providerConfigMap.size() > 0) {
                Map<String, ProtocolConfig> protocolConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, ProtocolConfig.class, false, false);
                if ((protocolConfigMap == null || protocolConfigMap.size() == 0)
                        && providerConfigMap.size() > 1) { // backward compatibility
                    List<ProviderConfig> providerConfigs = new ArrayList<ProviderConfig>();
                    for (ProviderConfig config : providerConfigMap.values()) {
                        if (config.isDefault() != null && config.isDefault().booleanValue()) {
                            providerConfigs.add(config);
                        }
                    }
                    if (!providerConfigs.isEmpty()) {
                        setProviders(providerConfigs);
                    }
                } else {
                    ProviderConfig providerConfig = null;
                    for (ProviderConfig config : providerConfigMap.values()) {
                        if (config.isDefault() == null || config.isDefault().booleanValue()) {
                            if (providerConfig != null) {
                                throw new IllegalStateException("Duplicate provider configs: " + providerConfig + " and " + config);
                            }
                            providerConfig = config;
                        }
                    }
                    if (providerConfig != null) {
                        setProvider(providerConfig);
                    }
                }
            }
        }
        //刚启动时applicatoin为空
        //如果application为空,则尝试从BeanFactory中查询dubbo:application实例
        if (getApplication() == null
                && (getProvider() == null || getProvider().getApplication() == null)) {
            Map<String, ApplicationConfig> applicationConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, ApplicationConfig.class, false, false);
            //如果存在多个dubbo:application配置，则抛出异常："Duplicate application configs"
            if (applicationConfigMap != null && applicationConfigMap.size() > 0) {
                ApplicationConfig applicationConfig = null;
                for (ApplicationConfig config : applicationConfigMap.values()) {
                    if (config.isDefault() == null || config.isDefault().booleanValue()) {
                        if (applicationConfig != null) {
                            throw new IllegalStateException("Duplicate application configs: " + applicationConfig + " and " + config);
                        }
                        applicationConfig = config;
                    }
                }
                if (applicationConfig != null) {
                    setApplication(applicationConfig);
                }
            }
        }
        //启动时module检查
        //如果ServiceBean的module为空，则尝试从BeanFactory中查询dubbo:module实例，
        if (getModule() == null
                && (getProvider() == null || getProvider().getModule() == null)) {
            Map<String, ModuleConfig> moduleConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, ModuleConfig.class, false, false);
            //如果存在多个dubbo:module，则抛出异常："Duplicate module configs: "
            if (moduleConfigMap != null && moduleConfigMap.size() > 0) {
                ModuleConfig moduleConfig = null;
                for (ModuleConfig config : moduleConfigMap.values()) {
                    if (config.isDefault() == null || config.isDefault().booleanValue()) {
                        if (moduleConfig != null) {
                            throw new IllegalStateException("Duplicate module configs: " + moduleConfig + " and " + config);
                        }
                        moduleConfig = config;
                    }
                }
                if (moduleConfig != null) {
                    setModule(moduleConfig);
                }
            }
        }
        //启动时注册中心检查
        //尝试从BeanFactory中加载所有的注册中心，注意ServiceBean的List< RegistryConfig> registries属性，为注册中心集合
        if ((getRegistries() == null || getRegistries().isEmpty())
                && (getProvider() == null || getProvider().getRegistries() == null || getProvider().getRegistries().isEmpty())
                && (getApplication() == null || getApplication().getRegistries() == null || getApplication().getRegistries().isEmpty())) {
            Map<String, RegistryConfig> registryConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, RegistryConfig.class, false, false);
            if (registryConfigMap != null && registryConfigMap.size() > 0) {
                //所有的注册中心集合
                List<RegistryConfig> registryConfigs = new ArrayList<RegistryConfig>();
                for (RegistryConfig config : registryConfigMap.values()) {
                    if (config.isDefault() == null || config.isDefault().booleanValue()) {
                        registryConfigs.add(config);
                    }
                }
                if (registryConfigs != null && !registryConfigs.isEmpty()) {
                    super.setRegistries(registryConfigs);
                }
            }
        }
        //启动时监控中心检查
        //尝试从BeanFacotry中加载一个监控中心，填充ServiceBean的MonitorConfig monitor属性，如果存在多个dubbo:monitor配置，则抛出"Duplicate monitor configs: "。

        if (getMonitor() == null
                && (getProvider() == null || getProvider().getMonitor() == null)
                && (getApplication() == null || getApplication().getMonitor() == null)) {
            Map<String, MonitorConfig> monitorConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, MonitorConfig.class, false, false);
            if (monitorConfigMap != null && monitorConfigMap.size() > 0) {
                MonitorConfig monitorConfig = null;
                for (MonitorConfig config : monitorConfigMap.values()) {
                    if (config.isDefault() == null || config.isDefault().booleanValue()) {
                        if (monitorConfig != null) {
                            throw new IllegalStateException("Duplicate monitor configs: " + monitorConfig + " and " + config);
                        }
                        monitorConfig = config;
                    }
                }
                if (monitorConfig != null) {
                    setMonitor(monitorConfig);
                }
            }
        }
        //启动时协议检查是否已经初始化
        //尝试从BeanFactory中加载所有的协议，注意：ServiceBean的List< ProtocolConfig> protocols是一个集合，也即一个服务可以通过多种协议暴露给消费者。

        if ((getProtocols() == null || getProtocols().isEmpty())
                && (getProvider() == null || getProvider().getProtocols() == null || getProvider().getProtocols().isEmpty())) {
            Map<String, ProtocolConfig> protocolConfigMap = applicationContext == null ? null : BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, ProtocolConfig.class, false, false);
            if (protocolConfigMap != null && protocolConfigMap.size() > 0) {
                List<ProtocolConfig> protocolConfigs = new ArrayList<ProtocolConfig>();
                for (ProtocolConfig config : protocolConfigMap.values()) {
                    if (config.isDefault() == null || config.isDefault().booleanValue()) {
                        protocolConfigs.add(config);
                    }
                }
                if (protocolConfigs != null && !protocolConfigs.isEmpty()) {
                    super.setProtocols(protocolConfigs);
                }
            }
        }
        //设置ServiceBean的path属性，path属性存放的是dubbo:service的beanName（dubbo:service id)
        if (getPath() == null || getPath().length() == 0) {
            if (beanName != null && beanName.length() > 0
                    && getInterface() != null && getInterface().length() > 0
                    && beanName.startsWith(getInterface())) {
                setPath(beanName);
            }
        }
//        如果为启用延迟暴露机制，则调用export暴露服务。首先看一下isDelay的实现，然后重点分析export的实现原理（服务暴露的整个实现原理）
        if (!isDelay()) {
            export();//核心的暴露服务方法入口
        }
    }

    public void destroy() throws Exception {
        // This will only be called for singleton scope bean, and expected to be called by spring shutdown hook when BeanFactory/ApplicationContext destroys.
        // We will guarantee dubbo related resources being released with dubbo shutdown hook.
        //unexport();
    }

    // merged from dubbox
    protected Class getServiceClass(T ref) {
        if (AopUtils.isAopProxy(ref)) {
            return AopUtils.getTargetClass(ref);
        }
        return super.getServiceClass(ref);
    }
}