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
package com.alibaba.dubbo.registry.integration;

import static com.alibaba.dubbo.common.Constants.ACCEPT_FOREIGN_IP;
import static com.alibaba.dubbo.common.Constants.QOS_ENABLE;
import static com.alibaba.dubbo.common.Constants.QOS_PORT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.RegistryFactory;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.registry.support.ProviderConsumerRegTable;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.Configurator;
import com.alibaba.dubbo.rpc.protocol.InvokerWrapper;

/**
 * RegistryProtocol
 *
 */
public class RegistryProtocol implements Protocol {

    private final static Logger logger = LoggerFactory.getLogger(RegistryProtocol.class);
    private static RegistryProtocol INSTANCE;
    private final Map<URL, NotifyListener> overrideListeners = new ConcurrentHashMap<URL, NotifyListener>();
    //To solve the problem of RMI repeated exposure port conflicts, the services that have been exposed are no longer exposed.
    //providerurl <--> exporter
    private final Map<String, ExporterChangeableWrapper<?>> bounds = new ConcurrentHashMap<String, ExporterChangeableWrapper<?>>();
    private Cluster cluster;
    private Protocol protocol;
    /**
     * 自适应扩展点
     */
    private RegistryFactory registryFactory;
    private ProxyFactory proxyFactory;

    public RegistryProtocol() {
        INSTANCE = this;
    }

    public static RegistryProtocol getRegistryProtocol() {
        if (INSTANCE == null) {
            ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(Constants.REGISTRY_PROTOCOL); // load
        }
        return INSTANCE;
    }

    //Filter the parameters that do not need to be output in url(Starting with .)
    private static String[] getFilteredKeys(URL url) {
        Map<String, String> params = url.getParameters();
        if (params != null && !params.isEmpty()) {
            List<String> filteredKeys = new ArrayList<String>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getKey().startsWith(Constants.HIDE_KEY_PREFIX)) {
                    filteredKeys.add(entry.getKey());
                }
            }
            return filteredKeys.toArray(new String[filteredKeys.size()]);
        } else {
            return new String[]{};
        }
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    //手动赋值的扩展点
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setRegistryFactory(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    public void setProxyFactory(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    public int getDefaultPort() {
        return 9090;
    }

    public Map<URL, NotifyListener> getOverrideListeners() {
        return overrideListeners;
    }

    public void register(URL registryUrl, URL registedProviderUrl) {
        Registry registry = registryFactory.getRegistry(registryUrl);
        registry.register(registedProviderUrl);
    }
    /**
     * 服务端服务发布流程：将invoker转换成Exporter
     *  入口为ServiceConfig.doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) 中的
    	Exporter<?> exporter = protocol.export(wrapperInvoker);触发。
         主要做如下一些操作：
         1、调用 doLocalExport 导出服务<即启动服务，让服务可用>
         2、向注册中心注册服务
         3、向注册中心进行订阅 override 数据
         4、创建并返回 DestroyableExporter
     */

    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        /**
         * ======>export invoker（本地发布实际上即启动服务，最终调用Netty启动监听端口）
         */
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);

        /**
         * originInvoker中包含invoker属性，其中Invoker结构为：
         * URL=registry://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&export=dubbo%3A%2F%2F192.168.0.101%3A20880%2Fcom.alibaba.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddemo-provider%26bind.ip%3D192.168.0.101%26bind.port%3D20880%26dubbo%3D2.0.0%26generic%3Dfalse%26group%3Da%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D13256%26qos.port%3D22222%26revision%3D0.0.2%26side%3Dprovider%26timestamp%3D1530024694398%26version%3D0.0.2&pid=13256&qos.port=22222&registry=zookeeper&timestamp=1530024694379
         * 然后通过getRegistryUrl(originInvoker)将协议转换为具体的注册中心的协议，比如zookeeper等
         * registryUrl = zookeeper://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&export=dubbo%3A%2F%2F192.168.0.101%3A20880%2Fcom.alibaba.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddemo-provider%26bind.ip%3D192.168.0.101%26bind.port%3D20880%26dubbo%3D2.0.0%26generic%3Dfalse%26group%3Da%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D7828%26qos.port%3D22222%26revision%3D0.0.2%26side%3Dprovider%26timestamp%3D1530024800397%26version%3D0.0.2&pid=7828&qos.port=22222&timestamp=1530024800380
         */
        URL registryUrl = getRegistryUrl(originInvoker);

        // 根据 URL 加载 Registry 实现类，比如 ZookeeperRegistry
        //若使用zookeeper注册中心，registry对象为ZookeeperRegistry,此处已经与zookeeper取得连接
        //registry=zookeeper://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=7828&qos.port=22222&timestamp=1530024800380
        final Registry registry = getRegistry(originInvoker);

        // 获取已注册的服务提供者 URL，比如：
        //registedProviderUrl=dubbo://192.168.0.101:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&dubbo=2.0.0&generic=false&group=a&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello&pid=7828&revision=0.0.2&side=provider&timestamp=1530024800397&version=0.0.2
        final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);

        // 获取 register 参数
        //to judge to delay publish whether or not
        boolean register = registedProviderUrl.getParameter("register", true);

        // 向服务提供者与消费者注册表中注册服务提供者
        ProviderConsumerRegTable.registerProvider(originInvoker, registryUrl, registedProviderUrl);

        //如果是注册中心，则将服务注册到注册中心
        if (register) {
            // 向注册中心注册服务
            register(registryUrl, registedProviderUrl);
            ProviderConsumerRegTable.getProviderWrapper(originInvoker).setReg(true);
        }
        //订阅orverride数据
        // 提供者订阅时，会影响同一JVM即暴露服务。又引用同一服务的场景
        // Subscribe the override data
        // provider://172.17.48.52:20880/com.alibaba.dubbo.demo.DemoService?category=configurators&check=false&anyhost=true&application=demo-provider&dubbo=2.0.2&generic=false&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call the same service. Because the subscribed is cached key with the name of the service, it causes the subscription information to cover.
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);

        // 创建监听器
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        // 向注册中心进行订阅 override 数据
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
        //Ensure that a new exporter instance is returned every time export
        // 创建并返回 DestroyableExporter
        return new DestroyableExporter<T>(exporter, originInvoker, overrideSubscribeUrl, registedProviderUrl);
    }
//    启动服务端（当前应用部署所在服务器）监听
//    invoker = {JavassistProxyFactory$1@2391} "interface com.alibaba.dubbo.demo.DemoService -> registry://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&export=dubbo%3A%2F%2F192.168.1.108%3A20880%2Fcom.alibaba.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddemo-provider%26bind.ip%3D192.168.1.108%26bind.port%3D20880%26dubbo%3D2.0.0%26generic%3Dfalse%26group%3Da%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D11808%26qos.port%3D22222%26revision%3D0.0.2%26side%3Dprovider%26timestamp%3D1583557556949%26version%3D0.0.2&pid=11808&qos.port=22222&registry=zookeeper&timestamp=1583557556936"
//    metadata = {ServiceBean@2011} "<dubbo:service path="com.alibaba.dubbo.demo.DemoService" ref="com.alibaba.dubbo.demo.provider.DemoServiceImpl@1c852c0f" generic="false" uniqueServiceName="a/com.alibaba.dubbo.demo.DemoService:0.0.2" exported="true" unexported="false" interface="com.alibaba.dubbo.demo.DemoService" version="0.0.2" group="a" id="a" />"
    @SuppressWarnings("unchecked")
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker) {
        String key = getCacheKey(originInvoker);
        ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
        if (exporter == null) {
        	//双重检查锁机制
            synchronized (bounds) {
            	//bounds为map集合，是一个缓存操作
                exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
                if (exporter == null) {
                	/**
                	 * getProviderUrl(originInvoker)=dubbo://192.168.0.101:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&bind.ip=192.168.0.101&bind.port=20880&dubbo=2.0.0&generic=false&group=a&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello&pid=13888&qos.port=22222&revision=0.0.2&side=provider&timestamp=1530020915218&version=0.0.2
                	    所以此处会将协议头从registry转换为dubbo
                	 */
                	final Invoker<?> invokerDelegete = new InvokerDelegete<T>(originInvoker, getProviderUrl(originInvoker));
                    /**
                     * 根据上面的invokerDelegete,所以此处本应为dubboProtol。但dubboProtol符合包装(filterwrapper及listenerWrapper规则)，
                     * 所以此处是一个包装类。
                	       所以此处包装结构为：ProtocolFilterWrapper(ProtocolListenerWrapper(DubboProtocol))
                	   所以代码走向为ProtocolFilterWrapper -->ProtocolListenerWrapper -->DubboProtocol, 最终的出口为dubboProtocol中的export
                	       通过包装将此处变为链式调用
                     */
                    exporter =  new ExporterChangeableWrapper<T>((Exporter<T>) protocol.export(invokerDelegete), originInvoker);
                    bounds.put(key, exporter);
                }
            }
        }
        return exporter;
    }

    /**
     * Reexport the invoker of the modified url
     *
     * @param originInvoker
     * @param newInvokerUrl
     */
    @SuppressWarnings("unchecked")
    private <T> void doChangeLocalExport(final Invoker<T> originInvoker, URL newInvokerUrl) {
        String key = getCacheKey(originInvoker);
        final ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
        if (exporter == null) {
            logger.warn(new IllegalStateException("error state, exporter should not be null"));
        } else {
            final Invoker<T> invokerDelegete = new InvokerDelegete<T>(originInvoker, newInvokerUrl);
            exporter.setExporter(protocol.export(invokerDelegete));
        }
    }

    /**
     * Get an instance of registry based on the address of invoker
     * 创建注册中心《以 Zookeeper 注册中心为例进行分析》
     * @param originInvoker
     * @return
     */
    private Registry getRegistry(final Invoker<?> originInvoker) {
        URL registryUrl = getRegistryUrl(originInvoker);
        return registryFactory.getRegistry(registryUrl);
    }

    private URL getRegistryUrl(Invoker<?> originInvoker) {
        URL registryUrl = originInvoker.getUrl();
        if (Constants.REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
            String protocol = registryUrl.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_DIRECTORY);
            registryUrl = registryUrl.setProtocol(protocol).removeParameter(Constants.REGISTRY_KEY);
        }
        return registryUrl;
    }


    /**
     * Return the url that is registered to the registry and filter the url parameter once
     *
     * @param originInvoker
     * @return
     */
    private URL getRegistedProviderUrl(final Invoker<?> originInvoker) {
        URL providerUrl = getProviderUrl(originInvoker);
        //The address you see at the registry
        final URL registedProviderUrl = providerUrl.removeParameters(getFilteredKeys(providerUrl))
                .removeParameter(Constants.MONITOR_KEY)
                .removeParameter(Constants.BIND_IP_KEY)
                .removeParameter(Constants.BIND_PORT_KEY)
                .removeParameter(QOS_ENABLE)
                .removeParameter(QOS_PORT)
                .removeParameter(ACCEPT_FOREIGN_IP);
        return registedProviderUrl;
    }

    private URL getSubscribedOverrideUrl(URL registedProviderUrl) {
        return registedProviderUrl.setProtocol(Constants.PROVIDER_PROTOCOL)
                .addParameters(Constants.CATEGORY_KEY, Constants.CONFIGURATORS_CATEGORY,
                        Constants.CHECK_KEY, String.valueOf(false));
    }

    /**
     * Get the address of the providerUrl through the url of the invoker
     *
     * @param origininvoker
     * @return
     */
    private URL getProviderUrl(final Invoker<?> origininvoker) {
        String export = origininvoker.getUrl().getParameterAndDecoded(Constants.EXPORT_KEY);
        if (export == null || export.length() == 0) {
            throw new IllegalArgumentException("The registry export url is null! registry: " + origininvoker.getUrl());
        }

        URL providerUrl = URL.valueOf(export);
        return providerUrl;
    }

    /**
     * Get the key cached in bounds by invoker
     *
     * @param originInvoker
     * @return
     */
    private String getCacheKey(final Invoker<?> originInvoker) {
        URL providerUrl = getProviderUrl(originInvoker);
        String key = providerUrl.removeParameters("dynamic", "enabled").toFullString();
        return key;
    }

    /**
     * 消费端订阅服务入口：将URL转换成Invoker
     * @param type Service class
     * @param url  URL address for the remote service
     * @param <T>
     * @return
     * @throws RpcException
     */
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        /*
         * 剔除Registry协议，得到具体的注册中心协议，如zookeeper://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService
         *      ?application=demo-consumer
         *      &backup=39.104.184.69:2181,39.104.184.69:2181
         *      &dubbo=2.0.0
         *      &pid=1428
         *      &qos.port=33333
         *      &refer=application%3Ddemo-consumer%26check%3Dfalse%26dubbo%3D2.0.0%26group%3Db%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D1428%26qos.port%3D33333%26register.ip%3D192.168.1.108%26revision%3D0.0.1%26side%3Dconsumer%26timestamp%3D1583846516103%26version%3D0.0.1&timestamp=1583846516163
         */
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        /*
         * 获取注册中心
         * 1、以此处发起注册中心连接,此处getRegistry方法属于AbstractRegistryFactory类
         * 2、创建监听，更新订阅者
         */
        Registry registry = registryFactory.getRegistry(url);
        //如果type为注册中心(正常业务服务不会进入此分支)
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((Constants.COMMA_SPLIT_PATTERN.split(group)).length > 1
                    || "*".equals(group)) {
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        //获取Invoker
        return doRefer(cluster, registry, type, url);
    }

    private Cluster getMergeableCluster() {
        return ExtensionLoader.getExtensionLoader(Cluster.class).getExtension("mergeable");
    }

    /**
     *
     * @param cluster   Cluster$Adaptive
     * @param registry  如果使用zk,则是ZookeeperRegistry,其相关参数为：zookeeper://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=1428&qos.port=33333&timestamp=1583846516163
     * @param type  interface com.alibaba.dubbo.demo.DemoService
     * @param url   zookeeper://47.93.201.88:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-consumer&backup=39.104.184.69:2181,39.104.184.69:2181&dubbo=2.0.0&pid=1428&qos.port=33333&refer=application%3Ddemo-consumer%26check%3Dfalse%26dubbo%3D2.0.0%26group%3Db%26interface%3Dcom.alibaba.dubbo.demo.DemoService%26methods%3DsayHello%26pid%3D1428%26qos.port%3D33333%26register.ip%3D192.168.1.108%26revision%3D0.0.1%26side%3Dconsumer%26timestamp%3D1583846516103%26version%3D0.0.1&timestamp=1583846516163
     * @param <T>
     * @return
     */
    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        /**
         * 根据class type 和 url 创建 Directory服务。
         */
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        //设置Directory的注册中心：ZookeeperRegistry等，具体注册中心由实际情况而定
        directory.setRegistry(registry);
        //protocol是一个Adaptive
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
        /*
         * subscribeUrl = consumer://192.168.1.108/com.alibaba.dubbo.demo.DemoService
         *                  ?application=demo-consumer
         *                  &check=false
         *                  &dubbo=2.0.0
         *                  &group=b
         *                  &interface=com.alibaba.dubbo.demo.DemoService
         *                  &methods=sayHello
         *                  &pid=10208
         *                  &qos.port=33333
         *                  &revision=0.0.1
         *                  &side=consumer
         *                  &timestamp=1583940691483&
         *                  version=0.0.1
         */
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, parameters.remove(Constants.REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
            /*
             * 向注册中心注册Consumer信息,默认实现是ZookeeperRegistry
             */
            registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY,
                    Constants.CHECK_KEY, String.valueOf(false)));
        }
        /*
         * 核心方法==>订阅服务,向注册中心发起订阅，并将URL转换成Invoker
         */
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY,
                Constants.PROVIDERS_CATEGORY
                        + "," + Constants.CONFIGURATORS_CATEGORY
                        + "," + Constants.ROUTERS_CATEGORY));
        /*
         * 通过Cluster将Directory包装成各种门面Invoker,比如FailfastClusterInvoker,FailoverClusterInvoker
         * ,FailSafeClusterInvoker等各种门面Invoker.
         */
        Invoker invoker = cluster.join(directory);
        ProviderConsumerRegTable.registerConsuemr(invoker, url, subscribeUrl, directory);
        return invoker;
    }

    public void destroy() {
        List<Exporter<?>> exporters = new ArrayList<Exporter<?>>(bounds.values());
        for (Exporter<?> exporter : exporters) {
            exporter.unexport();
        }
        bounds.clear();
    }

    public static class InvokerDelegete<T> extends InvokerWrapper<T> {
        private final Invoker<T> invoker;

        /**
         * @param invoker
         * @param url     invoker.getUrl return this value
         */
        public InvokerDelegete(Invoker<T> invoker, URL url) {
            super(invoker, url);
            this.invoker = invoker;
        }

        public Invoker<T> getInvoker() {
            if (invoker instanceof InvokerDelegete) {
                return ((InvokerDelegete<T>) invoker).getInvoker();
            } else {
                return invoker;
            }
        }
    }

    /**
     * Reexport: the exporter destroy problem in protocol
     * 1.Ensure that the exporter returned by registryprotocol can be normal destroyed
     * 2.No need to re-register to the registry after notify
     * 3.The invoker passed by the export method , would better to be the invoker of exporter
     */
    private class OverrideListener implements NotifyListener {

        private final URL subscribeUrl;
        private final Invoker originInvoker;

        public OverrideListener(URL subscribeUrl, Invoker originalInvoker) {
            this.subscribeUrl = subscribeUrl;
            this.originInvoker = originalInvoker;
        }

        /**
         * @param urls The list of registered information , is always not empty, The meaning is the same as the return value of {@link com.alibaba.dubbo.registry.RegistryService#lookup(URL)}.
         */
        public synchronized void notify(List<URL> urls) {
            logger.debug("original override urls: " + urls);
            List<URL> matchedUrls = getMatchedUrls(urls, subscribeUrl);
            logger.debug("subscribe url: " + subscribeUrl + ", override urls: " + matchedUrls);
            // No matching results
            if (matchedUrls.isEmpty()) {
                return;
            }

            List<Configurator> configurators = RegistryDirectory.toConfigurators(matchedUrls);

            final Invoker<?> invoker;
            if (originInvoker instanceof InvokerDelegete) {
                invoker = ((InvokerDelegete<?>) originInvoker).getInvoker();
            } else {
                invoker = originInvoker;
            }
            //The origin invoker
            URL originUrl = RegistryProtocol.this.getProviderUrl(invoker);
            String key = getCacheKey(originInvoker);
            ExporterChangeableWrapper<?> exporter = bounds.get(key);
            if (exporter == null) {
                logger.warn(new IllegalStateException("error state, exporter should not be null"));
                return;
            }
            //The current, may have been merged many times
            URL currentUrl = exporter.getInvoker().getUrl();
            //Merged with this configuration
            URL newUrl = getConfigedInvokerUrl(configurators, originUrl);
            if (!currentUrl.equals(newUrl)) {
                RegistryProtocol.this.doChangeLocalExport(originInvoker, newUrl);
                logger.info("exported provider url changed, origin url: " + originUrl + ", old export url: " + currentUrl + ", new export url: " + newUrl);
            }
        }

        private List<URL> getMatchedUrls(List<URL> configuratorUrls, URL currentSubscribe) {
            List<URL> result = new ArrayList<URL>();
            for (URL url : configuratorUrls) {
                URL overrideUrl = url;
                // Compatible with the old version
                if (url.getParameter(Constants.CATEGORY_KEY) == null
                        && Constants.OVERRIDE_PROTOCOL.equals(url.getProtocol())) {
                    overrideUrl = url.addParameter(Constants.CATEGORY_KEY, Constants.CONFIGURATORS_CATEGORY);
                }

                // Check whether url is to be applied to the current service
                if (UrlUtils.isMatch(currentSubscribe, overrideUrl)) {
                    result.add(url);
                }
            }
            return result;
        }

        //Merge the urls of configurators
        private URL getConfigedInvokerUrl(List<Configurator> configurators, URL url) {
            for (Configurator configurator : configurators) {
                url = configurator.configure(url);
            }
            return url;
        }
    }

    /**
     * exporter proxy, establish the corresponding relationship between the returned exporter and the exporter exported by the protocol, and can modify the relationship at the time of override.
     *
     * @param <T>
     */
    private class ExporterChangeableWrapper<T> implements Exporter<T> {

        private final Invoker<T> originInvoker;
        private Exporter<T> exporter;

        public ExporterChangeableWrapper(Exporter<T> exporter, Invoker<T> originInvoker) {
            this.exporter = exporter;
            this.originInvoker = originInvoker;
        }

        public Invoker<T> getOriginInvoker() {
            return originInvoker;
        }

        public Invoker<T> getInvoker() {
            return exporter.getInvoker();
        }

        public void setExporter(Exporter<T> exporter) {
            this.exporter = exporter;
        }

        public void unexport() {
            String key = getCacheKey(this.originInvoker);
            bounds.remove(key);
            exporter.unexport();
        }
    }

    static private class DestroyableExporter<T> implements Exporter<T> {

        public static final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("Exporter-Unexport", true));

        private Exporter<T> exporter;
        private Invoker<T> originInvoker;
        private URL subscribeUrl;
        private URL registerUrl;

        public DestroyableExporter(Exporter<T> exporter, Invoker<T> originInvoker, URL subscribeUrl, URL registerUrl) {
            this.exporter = exporter;
            this.originInvoker = originInvoker;
            this.subscribeUrl = subscribeUrl;
            this.registerUrl = registerUrl;
        }

        public Invoker<T> getInvoker() {
            return exporter.getInvoker();
        }

        public void unexport() {
            Registry registry = RegistryProtocol.INSTANCE.getRegistry(originInvoker);
            try {
                registry.unregister(registerUrl);
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }
            try {
                NotifyListener listener = RegistryProtocol.INSTANCE.overrideListeners.remove(subscribeUrl);
                registry.unsubscribe(subscribeUrl, listener);
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        int timeout = ConfigUtils.getServerShutdownTimeout();
                        if (timeout > 0) {
                            logger.info("Waiting " + timeout + "ms for registry to notify all consumers before unexport. Usually, this is called when you use dubbo API");
                            Thread.sleep(timeout);
                        }
                        exporter.unexport();
                    } catch (Throwable t) {
                        logger.warn(t.getMessage(), t);
                    }
                }
            });
        }
    }
}