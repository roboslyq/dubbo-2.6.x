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
package com.alibaba.dubbo.config;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.model.ApplicationModel;
import com.alibaba.dubbo.config.model.ConsumerModel;
import com.alibaba.dubbo.config.support.Parameter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.StaticContext;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.directory.StaticDirectory;
import com.alibaba.dubbo.rpc.cluster.support.AvailableCluster;
import com.alibaba.dubbo.rpc.cluster.support.ClusterUtils;
import com.alibaba.dubbo.rpc.protocol.injvm.InjvmProtocol;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.dubbo.common.utils.NetUtils.isInvalidLocalHost;

/**
 * ReferenceConfig
 * 服务消费者引用服务配置。
 * 参数详细：https://dubbo.gitbooks.io/dubbo-user-book/references/xml/dubbo-reference.html
 * @export
 */
public class ReferenceConfig<T> extends AbstractReferenceConfig {

    private static final long serialVersionUID = -5864351140409987595L;
    /**
     *Dubbo的SPI机制， 一个自适应扩展扩展点，正常情况返回Protocol$Adaptive
     */
    private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    /**
     * Dubbo的SPI机制， 一个自适应扩展扩展点，正常情况返回Cluster$Adaptive
     */
    private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();
    /**
     * 自适应扩展点《META-INF/dubbo/internal/com.alibaba.dubbo.rpc.ProxyFactory》：
     * stub=com.alibaba.dubbo.rpc.proxy.wrapper.StubProxyFactoryWrapper
     * jdk=com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory
     * javassist=com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory
     *
     */
    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private final List<URL> urls = new ArrayList<URL>();
    // interface name
    private String interfaceName;
    private Class<?> interfaceClass;
    // client type
    private String client;
    // url for peer-to-peer invocation
    private String url;
    // method configs
    private List<MethodConfig> methods;
    // default config
    private ConsumerConfig consumer;
    private String protocol;
    // interface proxy reference
    // Service 对象
    private transient volatile T ref;
    private transient volatile Invoker<?> invoker;
    /**
     * 是否已经初始化应用服务，参见 {@link #init()} 方法(非配置)。
     */
    private transient volatile boolean initialized;
    /**
     * 是否已经销毁引用服务，参见 {@link #destroy()} 方法(非配置)。
     */
    private transient volatile boolean destroyed;
    @SuppressWarnings("unused")
    private final Object finalizerGuardian = new Object() {
        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            if (!ReferenceConfig.this.destroyed) {
                logger.warn("ReferenceConfig(" + url + ") is not DESTROYED when FINALIZE");

                /* don't destroy for now
                try {
                    ReferenceConfig.this.destroy();
                } catch (Throwable t) {
                        logger.warn("Unexpected err when destroy invoker of ReferenceConfig(" + url + ") in finalize method!", t);
                }
                */
            }
        }
    };

    public ReferenceConfig() {
    }

    public ReferenceConfig(Reference reference) {
        appendAnnotation(Reference.class, reference);
    }
    /**
     * 检查属性集合中的事件通知方法是否正确。
     * 因为，此时方法配置的是字符串，需要通过反射获得 Method ，并添加到 attributes 。其中，键为 {@link StaticContext#getKey(Map, String, String)} 方法获取。
     * @param method 方法配置对象
     * @param map 参数集合
     * @param attributes 属性集合
     */
    private static void checkAndConvertImplicitConfig(MethodConfig method, Map<String, String> map, Map<Object, Object> attributes) {
        //check config conflict
        if (Boolean.FALSE.equals(method.isReturn()) && (method.getOnreturn() != null || method.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been setted.");
        }
        //convert onreturn methodName to Method
        // onreturn：将方法名字符串转换成方法
        String onReturnMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_RETURN_METHOD_KEY);
        Object onReturnMethod = attributes.get(onReturnMethodKey);
        if (onReturnMethod != null && onReturnMethod instanceof String) {
            attributes.put(onReturnMethodKey, getMethodByName(method.getOnreturn().getClass(), onReturnMethod.toString()));
        }
        //onthrow：将方法名字符串转换成方法
        //convert onthrow methodName to Method
        String onThrowMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_THROW_METHOD_KEY);
        Object onThrowMethod = attributes.get(onThrowMethodKey);
        if (onThrowMethod != null && onThrowMethod instanceof String) {
            attributes.put(onThrowMethodKey, getMethodByName(method.getOnthrow().getClass(), onThrowMethod.toString()));
        }
        //convert oninvoke methodName to Method
        // oninvoke：将方法名字符串转换成方法
        String onInvokeMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_INVOKE_METHOD_KEY);
        Object onInvokeMethod = attributes.get(onInvokeMethodKey);
        if (onInvokeMethod != null && onInvokeMethod instanceof String) {
            attributes.put(onInvokeMethodKey, getMethodByName(method.getOninvoke().getClass(), onInvokeMethod.toString()));
        }
    }

    private static Method getMethodByName(Class<?> clazz, String methodName) {
        try {
            return ReflectUtils.findMethodByMethodName(clazz, methodName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public URL toUrl() {
        return urls == null || urls.isEmpty() ? null : urls.iterator().next();
    }

    public List<URL> toUrls() {
        return urls;
    }
    //方法放口，可由ReferenceBean进行触发
    public synchronized T get() {
    	 // 已销毁，不可获得
        if (destroyed) {
            throw new IllegalStateException("Already destroyed!");
        }
        // 初始化==>核心入口
        if (ref == null) {
            init();
        }
        return ref;
    }

    public synchronized void destroy() {
        if (ref == null) {
            return;
        }
        if (destroyed) {
            return;
        }
        destroyed = true;
        try {
            invoker.destroy();
        } catch (Throwable t) {
            logger.warn("Unexpected err when destroy invoker of ReferenceConfig(" + url + ").", t);
        }
        invoker = null;
        ref = null;
    }
    //消费端初始化入口
    private void init() {
    	 // 已经初始化，直接返回
        if (initialized) {
            return;
        }
        // 校验接口名非空
        initialized = true;
        if (interfaceName == null || interfaceName.length() == 0) {
            throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
        }
        // get consumer's global configuration
        // 拼接属性配置（环境变量 + properties 属性）到 ConsumerConfig 对象
        checkDefault();
        // 拼接属性配置（环境变量 + properties 属性）到 ReferenceConfig 对象
        appendProperties(this);
        /*
         * 1、Generic具体意义请见`ServiceBean`中的注解。
         * 2、若设置当前<dubbo:reference>设置了`generic` 属性，或者未设置但 <dubbo:consumer>设置了generic属性。
          */
        if (getGeneric() == null && getConsumer() != null) {//【TODO 8002】，不只 true / false ，还有 bean 、 nativejava
            setGeneric(getConsumer().getGeneric());
        }
        // 泛化接口的实现《加载相关的接口》
        if (ProtocolUtils.isGeneric(getGeneric())) {
            interfaceClass = GenericService.class;
        } else { // 普通接口的实现
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            // 校验接口和方法
            checkInterfaceAndMethods(interfaceClass, methods);
        }
        /*
         * 1、直连提供者，参见文档《直连提供者》https://dubbo.gitbooks.io/dubbo-user-book/demos/explicit-target.html
         * 2、当线上需求服务是peer-to-peer(点对点)时，可以采用直连模式。
         * 3、直连模式一共有三种：
         *      (1)<dubbo:reference>中直接配置url属性，此时可以绕过注册中心。比如<dubbo:reference id="xxxService" interface="com.alibaba.xxx.XxxService" url="dubbo://localhost:20890"/>
         *     （2）在JVM启动参数中加入-D参数映射服务地址,key为服务名，value为服务提供者url。java -Dcom.alibaba.xxx.XxxService=dubbo://localhost:20890
         *      (3) 使用配置文件resolve.properties作映射，此种情况针对点对点服务较多时。默认文件名为dubbo-resolve.properties。
         *          然后在映射文件xxx.properties中加入：(key为服务名，value为服务提供者url) com.alibaba.xxx.XxxService=dubbo://localhost:20890
         */
        // 【直连提供者】第一优先级，通过 -D 参数指定 ，例如 java -Dcom.alibaba.xxx.XxxService=dubbo://localhost:20890
        String resolve = System.getProperty(interfaceName);
        String resolveFile = null;
        // 【直连提供者】第二优先级，通过文件映射，例如 com.alibaba.xxx.XxxService=dubbo://localhost:20890
        if (resolve == null || resolve.length() == 0) {
            // 默认先加载，`${user.home}/dubbo-resolve.properties` 文件 ，无需配置
            resolveFile = System.getProperty("dubbo.resolve.file");
            if (resolveFile == null || resolveFile.length() == 0) {
                File userResolveFile = new File(new File(System.getProperty("user.home")), "dubbo-resolve.properties");
                if (userResolveFile.exists()) {
                    resolveFile = userResolveFile.getAbsolutePath();
                }
            }
            // 存在 resolveFile ，则进行文件读取加载。
            if (resolveFile != null && resolveFile.length() > 0) {
                Properties properties = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(resolveFile));
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalStateException("Unload " + resolveFile + ", cause: " + e.getMessage(), e);
                } finally {
                    try {
                        if (null != fis) fis.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
                resolve = properties.getProperty(interfaceName);
            }
        }
        // 设置直连提供者的 url
        if (resolve != null && resolve.length() > 0) {
            url = resolve;
            if (logger.isWarnEnabled()) {
                if (resolveFile != null && resolveFile.length() > 0) {
                    logger.warn("Using default dubbo resolve file " + resolveFile + " replace " + interfaceName + "" + resolve + " to p2p invoke remote service.");
                } else {
                    logger.warn("Using -D" + interfaceName + "=" + resolve + " to p2p invoke remote service.");
                }
            }
        }
        // 从通用配置ConsumerConfig 对象中，读取 application、module、registries、monitor 配置对象。
        if (consumer != null) {
            if (application == null) {
                application = consumer.getApplication();
            }
            if (module == null) {
                module = consumer.getModule();
            }
            if (registries == null) {
                registries = consumer.getRegistries();
            }
            if (monitor == null) {
                monitor = consumer.getMonitor();
            }
        }
        // 从 ModuleConfig 对象中，读取 registries、monitor 配置对象。
        if (module != null) {
            if (registries == null) {
                registries = module.getRegistries();
            }
            if (monitor == null) {
                monitor = module.getMonitor();
            }
        }
        // 从 ApplicationConfig 对象中，读取 registries、monitor 配置对象。
        if (application != null) {
            if (registries == null) {
                registries = application.getRegistries();
            }
            if (monitor == null) {
                monitor = application.getMonitor();
            }
        }
        // 校验 ApplicationConfig 配置。
        checkApplication();
        // 校验 Stub 和 Mock 相关的配置
        checkStubAndMock(interfaceClass);
        // 将 `side`，`dubbo`，`timestamp`，`pid` 参数，添加到 `map` 集合中。
        Map<String, String> map = new HashMap<String, String>();
        Map<Object, Object> attributes = new HashMap<Object, Object>();
        map.put(Constants.SIDE_KEY, Constants.CONSUMER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        // methods、revision、interface
        if (!isGeneric()) {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put("revision", revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("NO method found in service interface " + interfaceClass.getName());
                map.put("methods", Constants.ANY_VALUE);
            } else {
                map.put("methods", StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        // 将各种配置对象，添加到 `map` 集合中。
        map.put(Constants.INTERFACE_KEY, interfaceName);
        appendParameters(map, application);
        appendParameters(map, module);
        appendParameters(map, consumer, Constants.DEFAULT_KEY);
        appendParameters(map, this);
        // 获得服务键，作为前缀
        String prefix = StringUtils.getServiceKey(map);
        // 将 MethodConfig 对象数组，添加到 `map` 集合中。
        if (methods != null && !methods.isEmpty()) {
            for (MethodConfig method : methods) {
                // 将 MethodConfig 对象，添加到 `map` 集合中。
                appendParameters(map, method, method.getName());
                // 当 配置了 `MethodConfig.retry = false` 时，强制禁用重试
                String retryKey = method.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(method.getName() + ".retries", "0");
                    }
                }
                // 将带有 @Parameter(attribute = true) 配置对象的属性，添加到参数集合。参见《事件通知》http://dubbo.io/books/dubbo-user-book/demos/events-notify.html
                appendAttributes(attributes, method, prefix + "." + method.getName());
                // 检查属性集合中的事件通知方法是否正确。若正确，进行转换。
                checkAndConvertImplicitConfig(method, map, attributes);
            }
        }
        // 以系统环境变量( DUBBO_IP_TO_REGISTRY ) 作为服务注册地址，参见 https://github.com/dubbo/dubbo-docker-sample 项目。
        String hostToRegistry = ConfigUtils.getSystemProperty(Constants.DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry == null || hostToRegistry.length() == 0) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + Constants.DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(Constants.REGISTER_IP_KEY, hostToRegistry);
        // 添加到 StaticContext 进行缓存
        //attributes are stored by system context.
        StaticContext.getSystemContext().putAll(attributes);

        /*
         * 创建reference具体代理类
         */
        ref = createProxy(map);

        //ConsumerModel是对ReferenceConfig配置和代理类proxy的完整的包装。
        ConsumerModel consumerModel = new ConsumerModel(getUniqueServiceName(), this, ref, interfaceClass.getMethods());
        // 将当前的consumerModel加载到ApplicationModel中的consumedServices缓存中：consumedServices是一个Map
        ApplicationModel.initConsumerModel(getUniqueServiceName(), consumerModel);
    }


    /**
     *核心方法：创建reference具体代理类
     * 1、从注册中心拉取信息，解析出Invoker
     * 2、如果有多个服务提供者，通过Cluster#join()包装得到最终一个Invoker
     * 3、使用ProxyFactory#getProxy(invoker)得到Invoker代理类，供具体的业务代码调用。
     */
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private T createProxy(Map<String, String> map) {
    	/* 创建tmpUrl,其具体内容大概如下：
    	 *   temp://localhost?
    	 *   application=demo-consumer
    	 *   &check=false
    	 *   &dubbo=2.0.0
    	 *   &interface=com.alibaba.dubbo.demo.DemoService
    	 *   &methods=sayHello
    	 *   &pid=2508
    	 *   &qos.port=33333
    	 *   &register.ip=192.168.0.100
    	 *   &side=consumer
    	 *   &timestamp=1523286869360
    	 */
        URL tmpUrl = new URL("temp", "localhost", 0, map);
        //判断是否是当前JVM调用<本地服务引用>
        final boolean isJvmRefer;
        if (isInjvm() == null) {
            if (url != null && url.length() > 0) { // if a url is specified, don't do local reference
                isJvmRefer = false;
            } else if (InjvmProtocol.getInjvmProtocol().isInjvmRefer(tmpUrl)) {
                // by default, reference local service if there is
                isJvmRefer = true;
            } else {
                isJvmRefer = false;
            }
        } else {
            isJvmRefer = isInjvm().booleanValue();
        }
        //根据不同的protocol获取相应的Invoker
        if (isJvmRefer) {
            URL url = new URL(Constants.LOCAL_PROTOCOL, NetUtils.LOCALHOST, 0, interfaceClass.getName()).addParameters(map);
            invoker = refprotocol.refer(interfaceClass, url);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        } else {//从注册中心获取消费者需要初始化信息
        	//对URL进行相关处理
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = Constants.SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (url.getPath() == null || url.getPath().length() == 0) {
                            url = url.setPath(interfaceName);
                        }
                        if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                            urls.add(url.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else {
                // assemble URL from register center's configuration
                List<URL> us = loadRegistries(false);
                if (us != null && !us.isEmpty()) {
                    for (URL u : us) {
                        URL monitorUrl = loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(Constants.MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls == null || urls.isEmpty()) {
                    throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }
            /*
             *单机或者集群的服务引用的核心方法： refprotocol.refer()
             */
            //单机片版，非集群
            if (urls.size() == 1) {
            	//interfaceClass ="interface com.alibaba.dubbo.demo.DemoService"
            	//urls.get(0)=[registry://224.5.6.7:1234/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&dubbo=2.0.0&pid=15628&qos.port=33333&refer=application%3Ddemo-consumer%26check%3Dfalse%26dubbo%3D2.0.0%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D15628%26qos.port%3D33333%26register.ip%3D192.168.0.100%26side%3Dconsumer%26timestamp%3D1523288101657&registry=multicast&timestamp=1523288101705]
                invoker = refprotocol.refer(interfaceClass, urls.get(0));
            } else {

                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    /*
                     * 1、多机版，多个服务提供者，需要做Cluster集群处理
                     * 2、消费端启动的核心流程，通过具体的Protocol{比如：RegistryProtocol}将URL转换成对应的Invoker。
                     * 3、转换成Invoker之后，将其加入到invokers数组中
                     */
                    invokers.add(
                            refprotocol.refer(interfaceClass, url) //服务引用，返回一个Invoker
                    );
                    if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                        registryURL = url; // use last registry url
                    }
                }
                /*
                 * cluster为一个自适应扩展点，将多个原始的Invoker包装为一个Invoker，包装后的Invoker类型有如下实现：
                 *      AvailableCluster
                 *      BroadcastCluster
                 *      FailbackCluster
                 *      FailfastCluster
                 *      FailoverCluster
                 *      FailsafeCluster
                 *      ForkingCluster
                 *      MergeableCluster
                 *      MockClusterWrapper
                 * 可以通过包装后的Invoker实现LoadBalance功能 。
                 */
                if (registryURL != null) { // registry url is available
                    // use AvailableCluster only when register's cluster is available
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else { // not a registry url
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        }

        Boolean c = check;
        if (c == null && consumer != null) {
            c = consumer.isCheck();
        }
        if (c == null) {
            c = true; // default true
        }
        if (c && !invoker.isAvailable()) {
            throw new IllegalStateException("Failed to check the status of the service " + interfaceName + ". No provider available for the service " + (group == null ? "" : group + "/") + interfaceName + (version == null ? "" : ":" + version) + " from the url " + invoker.getUrl() + " to the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }
        // create service proxy
        //创建服务代理
        return (T) proxyFactory.getProxy(invoker);
    }
    /**
     * 校验 ConsumerConfig 配置。
     * 实际上，会拼接属性配置（环境变量 + properties 属性）到 ConsumerConfig 对象。
     */
    private void checkDefault() {
        if (consumer == null) {
            consumer = new ConsumerConfig();
        }
        appendProperties(consumer);
    }

    public Class<?> getInterfaceClass() {
        if (interfaceClass != null) {
            return interfaceClass;
        }
        if (isGeneric()
                || (getConsumer() != null && getConsumer().isGeneric())) {
            return GenericService.class;
        }
        try {
            if (interfaceName != null && interfaceName.length() > 0) {
                this.interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            }
        } catch (ClassNotFoundException t) {
            throw new IllegalStateException(t.getMessage(), t);
        }
        return interfaceClass;
    }

    /**
     * @param interfaceClass
     * @see #setInterface(Class)
     * @deprecated
     */
    @Deprecated
    public void setInterfaceClass(Class<?> interfaceClass) {
        setInterface(interfaceClass);
    }

    public String getInterface() {
        return interfaceName;
    }

    public void setInterface(Class<?> interfaceClass) {
        if (interfaceClass != null && !interfaceClass.isInterface()) {
            throw new IllegalStateException("The interface class " + interfaceClass + " is not a interface!");
        }
        this.interfaceClass = interfaceClass;
        setInterface(interfaceClass == null ? null : interfaceClass.getName());
    }

    public void setInterface(String interfaceName) {
        this.interfaceName = interfaceName;
        if (id == null || id.length() == 0) {
            id = interfaceName;
        }
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        checkName("client", client);
        this.client = client;
    }

    @Parameter(excluded = true)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<MethodConfig> getMethods() {
        return methods;
    }

    @SuppressWarnings("unchecked")
    public void setMethods(List<? extends MethodConfig> methods) {
        this.methods = (List<MethodConfig>) methods;
    }

    public ConsumerConfig getConsumer() {
        return consumer;
    }

    public void setConsumer(ConsumerConfig consumer) {
        this.consumer = consumer;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    // just for test
    Invoker<?> getInvoker() {
        return invoker;
    }

    @Parameter(excluded = true)
    public String getUniqueServiceName() {
        StringBuilder buf = new StringBuilder();
        if (group != null && group.length() > 0) {
            buf.append(group).append("/");
        }
        buf.append(interfaceName);
        if (version != null && version.length() > 0) {
            buf.append(":").append(version);
        }
        return buf.toString();
    }

}
