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
package com.alibaba.dubbo.config.spring.schema;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.*;
import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.config.spring.ServiceBean;
import com.alibaba.dubbo.rpc.Protocol;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.*;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AbstractBeanDefinitionParser
 * DUBBO配置文件解析，详情见DubboNamespaceHandler
 * 目前支持：
 * <dubbo:application/>-应用名称，每一个应用都有一个标识可以用来记录应用依赖关系
 * <dubbo:module/>-可选参数，用于配置当前模块信息，可选
 * <dubbo:registry/>-注册中心
 * <dubbo:monitor/>-监控中心用于配置连接监控中心相关信息，可选
 * <dubbo:provider/>-可选参数，当 ProtocolConfig 和 ServiceConfig 某属性没有配置时，采用此缺省值，可选
 * <dubbo:consumer/>-可选参数，当 ReferenceConfig 某属性没有配置时，采用此缺省值，可选
 * <dubbo:protocol/>-协议
 * <dubbo:service/>-服务提供者
 * <dubbo:reference/>-服务消费者
 * <dubbo:annotation/>-可选参数
 * 
 * @export
 */
public class DubboBeanDefinitionParser implements BeanDefinitionParser {

    private static final Logger logger = LoggerFactory.getLogger(DubboBeanDefinitionParser.class);
    private static final Pattern GROUP_AND_VERION = Pattern.compile("^[\\-.0-9_a-zA-Z]+(\\:[\\-.0-9_a-zA-Z]+)?$");
    private static int count = 0;
    /**
     * Bean 对象的类
     */
    private final Class<?> beanClass;
    /**
     * 是否需要 Bean 的 `id` 属性
     */
    private final boolean required;

    /**
     * 构造函数
     * @param beanClass 指定对应的配置类
     * @param required 是否必须
     */
    public DubboBeanDefinitionParser(Class<?> beanClass, boolean required) {
        this.beanClass = beanClass;
        this.required = required;
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required) {
        // RootBeanDefinition: spring bean的扩展点
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        /*
         * beanClass即为DubboNamespaceHandler中注册的9大配置类
         * ApplicationConfig.class
         * ModuleConfig.class
         * ServieBean.class
         * ReferenceBean.class
         * ProtocolConfig.class
         * RegistryConfig.class
         * MonitorConfig.class
         * ProviderConfig.class
         * ConsumerConfig.class
         */
        //设置Bean的Class类型，即Bean类型，最终会在SpringIOC容器中，创建一个当前类型的Bean。
        beanDefinition.setBeanClass(beanClass);
        beanDefinition.setLazyInit(false);
        String id = element.getAttribute("id");
        //<dubbo:reference id="demoService" check="false" interface="com.alibaba.dubbo.demo.DemoService"/>
        //如果配置文件中的id为空，并且是必须输入的，则从name属性中获取
        if ((id == null || id.length() == 0) && required) {
            String generatedBeanName = element.getAttribute("name");
            //如果name属性也是空的
            if (generatedBeanName == null || generatedBeanName.length() == 0) {
            	//如果是协议Protocol标签，则默认为dubbo
                if (ProtocolConfig.class.equals(beanClass)) {
                    generatedBeanName = "dubbo";
                } else {
                	//否则默认为interface名称
                    generatedBeanName = element.getAttribute("interface");
                }
            }
            //如果generatedBeanName还是空的，则使用类名并且赋值到id
            if (generatedBeanName == null || generatedBeanName.length() == 0) {
                generatedBeanName = beanClass.getName();
            }
            id = generatedBeanName;
            //如果有多个相同的bean,则使用计数器添拼接，得到不同的类
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = generatedBeanName + (counter++);
            }
        }
        if (id != null && id.length() > 0) {
        	//单例bean,如果已经初始化则抛出异常
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            // 添加到 Spring 的注册表
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
            // 设置 Bean 的 `id` 属性值
            beanDefinition.getPropertyValues().addPropertyValue("id", id);
        }
        //协议标签初始化
        // 需要满足第 220 至 233 行。
        // 例如：【顺序要这样】
        // <dubbo:service interface="com.alibaba.dubbo.demo.DemoService" protocol="dubbo" ref="demoService"/>
        // <dubbo:protocol id="dubbo" name="dubbo" port="20880"/>
        if (ProtocolConfig.class.equals(beanClass)) {
            for (String name : parserContext.getRegistry().getBeanDefinitionNames()) {
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(name);
                PropertyValue property = definition.getPropertyValues().getPropertyValue("protocol");
                if (property != null) {
                    Object value = property.getValue();
                    if (value instanceof ProtocolConfig && id.equals(((ProtocolConfig) value).getName())) {
                        definition.getPropertyValues().addPropertyValue("protocol", new RuntimeBeanReference(id));
                    }
                }
            }
        } 
        //处理 `<dubbo:service />` 的属性 `class`
        else if (ServiceBean.class.equals(beanClass)) {
            // 处理 `class` 属性。例如  <dubbo:service id="sa" interface="com.alibaba.dubbo.demo.DemoService" class="com.alibaba.dubbo.demo.provider.DemoServiceImpl" >
            String className = element.getAttribute("class");
            if (className != null && className.length() > 0) {
                // 创建 Service 的 RootBeanDefinition 对象。相当于内嵌了 <bean class="com.alibaba.dubbo.demo.provider.DemoServiceImpl" />
                RootBeanDefinition classDefinition = new RootBeanDefinition();
                classDefinition.setBeanClass(ReflectUtils.forName(className));
                classDefinition.setLazyInit(false);
                // 解析 Service Bean 对象的属性
                parseProperties(element.getChildNodes(), classDefinition);
                // 设置 `<dubbo:service ref="" />` 属性
                beanDefinition.getPropertyValues().addPropertyValue("ref", new BeanDefinitionHolder(classDefinition, id + "Impl"));
            }
        } 
        //解析 `<dubbo:provider />` 的内嵌子元素 `<dubbo:service />`
        else if (ProviderConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ServiceBean.class, true, "service", "provider", id, beanDefinition);
        } 
        //解析 `<dubbo:consumer />` 的内嵌子元素 `<dubbo:reference />`
        else if (ConsumerConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ReferenceBean.class, false, "reference", "consumer", id, beanDefinition);
        }
        Set<String> props = new HashSet<String>();
        ManagedMap parameters = null;
        // 循环 Bean 对象的 setting 方法，将属性添加到 Bean 对象的属性赋值
        for (Method setter : beanClass.getMethods()) {
            String name = setter.getName();
            if (name.length() > 3 && name.startsWith("set")
                    && Modifier.isPublic(setter.getModifiers())
                    && setter.getParameterTypes().length == 1) {
                Class<?> type = setter.getParameterTypes()[0];
                // 添加 `props`
                String property = StringUtils.camelToSplitName(name.substring(3, 4).toLowerCase() + name.substring(4), "-");
                props.add(property);
                // getting && public && 属性值类型统一
                Method getter = null;
                try {
                    getter = beanClass.getMethod("get" + name.substring(3), new Class<?>[0]);
                } catch (NoSuchMethodException e) {
                    try {
                        getter = beanClass.getMethod("is" + name.substring(3), new Class<?>[0]);
                    } catch (NoSuchMethodException e2) {
                    }
                }
                if (getter == null
                        || !Modifier.isPublic(getter.getModifiers())
                        || !type.equals(getter.getReturnType())) {
                    continue;
                }
                // 解析 `<dubbo:parameters />`
                if ("parameters".equals(property)) {
                    parameters = parseParameters(element.getChildNodes(), beanDefinition);
                // 解析 `<dubbo:method />`
                } else if ("methods".equals(property)) {
                    parseMethods(id, element.getChildNodes(), beanDefinition, parserContext);
                // 解析 `<dubbo:argument />`
                } else if ("arguments".equals(property)) {
                    parseArguments(id, element.getChildNodes(), beanDefinition, parserContext);
                } else {
                    String value = element.getAttribute(property);
                    if (value != null) {
                        value = value.trim();
                        if (value.length() > 0) {
                        	// 不想注册到注册中心的情况，即 `registry=N/A` 。
                            if ("registry".equals(property) && RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(value)) {
                                RegistryConfig registryConfig = new RegistryConfig();
                                registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
                                beanDefinition.getPropertyValues().addPropertyValue(property, registryConfig);
                            // 多注册中心的情况
                            } else if ("registry".equals(property) && value.indexOf(',') != -1) {
                                parseMultiRef("registries", value, beanDefinition, parserContext);
                            // 多服务提供者的情况
                            } else if ("provider".equals(property) && value.indexOf(',') != -1) {
                                parseMultiRef("providers", value, beanDefinition, parserContext);
                             // 多协议的情况
                            } else if ("protocol".equals(property) && value.indexOf(',') != -1) {
                                parseMultiRef("protocols", value, beanDefinition, parserContext);
                            } else {
                                Object reference;
                                // 处理属性类型为基本属性的情况
                                if (isPrimitive(type)) {
                                	 // 兼容性处理
                                    if ("async".equals(property) && "false".equals(value)
                                            || "timeout".equals(property) && "0".equals(value)
                                            || "delay".equals(property) && "0".equals(value)
                                            || "version".equals(property) && "0.0.0".equals(value)
                                            || "stat".equals(property) && "-1".equals(value)
                                            || "reliable".equals(property) && "false".equals(value)) {
                                        // backward compatibility for the default value in old version's xsd
                                        value = null;
                                    }
                                    reference = value;
                                // 处理在 `<dubbo:provider />` 或者 `<dubbo:service />` 上定义了 `protocol` 属性的 兼容性。
                                } else if ("protocol".equals(property)
                                        && ExtensionLoader.getExtensionLoader(Protocol.class).hasExtension(value)
                                        && (!parserContext.getRegistry().containsBeanDefinition(value)
                                        || !ProtocolConfig.class.getName().equals(parserContext.getRegistry().getBeanDefinition(value).getBeanClassName()))) {
                                    // 目前，`<dubbo:provider protocol="" />` 推荐独立成 `<dubbo:protocol />`
                                	if ("dubbo:provider".equals(element.getTagName())) {
                                        logger.warn("Recommended replace <dubbo:provider protocol=\"" + value + "\" ... /> to <dubbo:protocol name=\"" + value + "\" ... />");
                                    }
                                    // backward compatibility
                                    ProtocolConfig protocol = new ProtocolConfig();
                                    protocol.setName(value);
                                    reference = protocol;
                                    // 处理 `onreturn` 属性
                                } else if ("onreturn".equals(property)) {
                                    // 按照 `.` 拆分
                                    int index = value.lastIndexOf(".");
                                    String returnRef = value.substring(0, index);
                                    String returnMethod = value.substring(index + 1);
                                    // 创建 RuntimeBeanReference ，指向回调的对象
                                    reference = new RuntimeBeanReference(returnRef);
                                    // 设置 `onreturnMethod` 到 BeanDefinition 的属性值
                                    beanDefinition.getPropertyValues().addPropertyValue("onreturnMethod", returnMethod);
                                    // 处理 `onthrow` 属性
                                } else if ("onthrow".equals(property)) {
                                    int index = value.lastIndexOf(".");
                                    String throwRef = value.substring(0, index);
                                    String throwMethod = value.substring(index + 1);
                                    reference = new RuntimeBeanReference(throwRef);
                                    beanDefinition.getPropertyValues().addPropertyValue("onthrowMethod", throwMethod);
                                } else if ("oninvoke".equals(property)) {
                                    // 按照 `.` 拆分
                                    int index = value.lastIndexOf(".");
                                    String invokeRef = value.substring(0, index);
                                    String invokeRefMethod = value.substring(index + 1);
                                    // 创建 RuntimeBeanReference ，指向回调的对象
                                    reference = new RuntimeBeanReference(invokeRef);
                                    // 设置 `onthrowMethod` 到 BeanDefinition 的属性值
                                    beanDefinition.getPropertyValues().addPropertyValue("oninvokeMethod", invokeRefMethod);
                                    // 通用解析
                                }else {
                                    // 指向的 Service 的 Bean 对象，必须是单例
                                    if ("ref".equals(property) && parserContext.getRegistry().containsBeanDefinition(value)) {
                                        BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                                        if (!refBean.isSingleton()) {
                                            throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                                        }
                                    }
                                    // 创建 RuntimeBeanReference ，指向 Service 的 Bean 对象
                                    reference = new RuntimeBeanReference(value);
                                }
                                // 设置 BeanDefinition 的属性值
                                beanDefinition.getPropertyValues().addPropertyValue(property, reference);
                            }
                        }
                    }
                }
            }
        }
        // 将 XML 元素，未在上面遍历到的属性，添加到 `parameters` 集合中。目前测试下来，不存在这样的情况。
        NamedNodeMap attributes = element.getAttributes();
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            Node node = attributes.item(i);
            String name = node.getLocalName();
            if (!props.contains(name)) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String value = node.getNodeValue();
                parameters.put(name, new TypedStringValue(value, String.class));
            }
        }
        if (parameters != null) {
            beanDefinition.getPropertyValues().addPropertyValue("parameters", parameters);
        }
        return beanDefinition;
    }

    private static boolean isPrimitive(Class<?> cls) {
        return cls.isPrimitive() || cls == Boolean.class || cls == Byte.class
                || cls == Character.class || cls == Short.class || cls == Integer.class
                || cls == Long.class || cls == Float.class || cls == Double.class
                || cls == String.class || cls == Date.class || cls == Class.class;
    }

    /**
     * 解析多指向的情况，例如多注册中心，多协议等等。
     *
     * @param property 属性
     * @param value 值
     * @param beanDefinition Bean 定义对象
     * @param parserContext Spring 解析上下文
     */
    @SuppressWarnings("unchecked")
    private static void parseMultiRef(String property, String value, RootBeanDefinition beanDefinition,
                                      ParserContext parserContext) {
        String[] values = value.split("\\s*[,]+\\s*");
        ManagedList list = null;
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            if (v != null && v.length() > 0) {
                if (list == null) {
                    list = new ManagedList();
                }
                list.add(new RuntimeBeanReference(v));
            }
        }
        beanDefinition.getPropertyValues().addPropertyValue(property, list);
    }
    /**
     * 解析内嵌的指向的子 XML 元素
     *
     * @param element 父 XML 元素
     * @param parserContext Spring 解析上下文
     * @param beanClass 内嵌解析子元素的 Bean 的类
     * @param required 是否需要 Bean 的 `id` 属性
     * @param tag 标签
     * @param property 父 Bean 对象在子元素中的属性名
     * @param ref 指向
     * @param beanDefinition 父 Bean 定义对象
     */
    private static void parseNested(Element element, ParserContext parserContext, Class<?> beanClass, boolean required, String tag, String property, String ref, BeanDefinition beanDefinition) {
        NodeList nodeList = element.getChildNodes();
        if (nodeList != null && nodeList.getLength() > 0) {
            boolean first = true;
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element) {
                    if (tag.equals(node.getNodeName())
                            || tag.equals(node.getLocalName())) {
                        if (first) {
                            first = false;
                            String isDefault = element.getAttribute("default");
                            if (isDefault == null || isDefault.length() == 0) {
                                beanDefinition.getPropertyValues().addPropertyValue("default", "false");
                            }
                        }
                        BeanDefinition subDefinition = parse((Element) node, parserContext, beanClass, required);
                        if (subDefinition != null && ref != null && ref.length() > 0) {
                            subDefinition.getPropertyValues().addPropertyValue(property, new RuntimeBeanReference(ref));
                        }
                    }
                }
            }
        }
    }
    /**
     * 解析 <dubbo:service class="" /> 情况下，内涵的 `<property />` 的赋值。
     *
     * @param nodeList 子元素数组
     * @param beanDefinition Bean 定义对象
     */
    private static void parseProperties(NodeList nodeList, RootBeanDefinition beanDefinition) {
        if (nodeList != null && nodeList.getLength() > 0) {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element) {
                    if ("property".equals(node.getNodeName())
                            || "property".equals(node.getLocalName())) {
                        String name = ((Element) node).getAttribute("name");
                        if (name != null && name.length() > 0) {
                            String value = ((Element) node).getAttribute("value");
                            String ref = ((Element) node).getAttribute("ref");
                            if (value != null && value.length() > 0) {
                                beanDefinition.getPropertyValues().addPropertyValue(name, value);
                            } else if (ref != null && ref.length() > 0) {
                                beanDefinition.getPropertyValues().addPropertyValue(name, new RuntimeBeanReference(ref));
                            } else {
                                throw new UnsupportedOperationException("Unsupported <property name=\"" + name + "\"> sub tag, Only supported <property name=\"" + name + "\" ref=\"...\" /> or <property name=\"" + name + "\" value=\"...\" />");
                            }
                        }
                    }
                }
            }
        }
    }
    /**
     * 解析 `<dubbo:parameter />`
     *
     * @param nodeList 子元素节点数组
     * @param beanDefinition Bean 定义对象
     * @return 参数集合
     */
    @SuppressWarnings("unchecked")
    private static ManagedMap parseParameters(NodeList nodeList, RootBeanDefinition beanDefinition) {
        if (nodeList != null && nodeList.getLength() > 0) {
            ManagedMap parameters = null;
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element) {
                    if ("parameter".equals(node.getNodeName())
                            || "parameter".equals(node.getLocalName())) {
                        if (parameters == null) {
                            parameters = new ManagedMap();
                        }
                        String key = ((Element) node).getAttribute("key");
                        String value = ((Element) node).getAttribute("value");
                        boolean hide = "true".equals(((Element) node).getAttribute("hide"));
                        if (hide) {
                            key = Constants.HIDE_KEY_PREFIX + key;
                        }
                        parameters.put(key, new TypedStringValue(value, String.class));
                    }
                }
            }
            return parameters;
        }
        return null;
    }
    /**
     * 解析 `<dubbo:method />`
     *
     * @param id Bean 的 `id` 属性。
     * @param nodeList 子元素节点数组
     * @param beanDefinition Bean 定义对象
     * @param parserContext 解析上下文
     */
    @SuppressWarnings("unchecked")
    private static void parseMethods(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                     ParserContext parserContext) {
        if (nodeList != null && nodeList.getLength() > 0) {
            ManagedList methods = null;// 解析的方法数组
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    if ("method".equals(node.getNodeName()) || "method".equals(node.getLocalName())) {
                        String methodName = element.getAttribute("name");
                        if (methodName == null || methodName.length() == 0) {// 这三行，判断值解析 `<dubbo:method />`
                            throw new IllegalStateException("<dubbo:method> name attribute == null");
                        }
                        // 方法名不能为空
                        if (methods == null) {
                            methods = new ManagedList();
                        }
                        // 解析 `<dubbo:method />`，创建 BeanDefinition 对象
                        BeanDefinition methodBeanDefinition = parse(((Element) node),
                                parserContext, MethodConfig.class, false);
                        String name = id + "." + methodName;
                        BeanDefinitionHolder methodBeanDefinitionHolder = new BeanDefinitionHolder(
                                methodBeanDefinition, name);
                        // 添加到 `methods` 中
                        methods.add(methodBeanDefinitionHolder);
                    }
                }
            }
            if (methods != null) {
                beanDefinition.getPropertyValues().addPropertyValue("methods", methods);
            }
        }
    }
    /**
     * 解析 `<dubbo:argument />`
     *
     * @param id Bean 的 `id` 属性。
     * @param nodeList 子元素节点数组
     * @param beanDefinition Bean 定义对象
     * @param parserContext 解析上下文
     */
    @SuppressWarnings("unchecked")
    private static void parseArguments(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                       ParserContext parserContext) {
        if (nodeList != null && nodeList.getLength() > 0) {
            ManagedList arguments = null;// 解析的参数数组
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    if ("argument".equals(node.getNodeName()) || "argument".equals(node.getLocalName())) {// 这三行，判断值解析 `<dubbo:argument />`
                        String argumentIndex = element.getAttribute("index");
                        if (arguments == null) {
                            arguments = new ManagedList();
                        }
                        // 解析 `<dubbo:argument />`，创建 BeanDefinition 对象
                        BeanDefinition argumentBeanDefinition = parse(((Element) node),
                                parserContext, ArgumentConfig.class, false);
                        // 添加到 `arguments` 中
                        String name = id + "." + argumentIndex;
                        BeanDefinitionHolder argumentBeanDefinitionHolder = new BeanDefinitionHolder(
                                argumentBeanDefinition, name);
                        arguments.add(argumentBeanDefinitionHolder);
                    }
                }
            }
            if (arguments != null) {
                beanDefinition.getPropertyValues().addPropertyValue("arguments", arguments);
            }
        }
    }

    public BeanDefinition parse(Element element, ParserContext parserContext) {
        System.out.println("DubboBeanDefinitionParser被调用次数:" + ++count);
        printElement(element);
        return parse(element, parserContext, beanClass, required);
    }

    private void printElement(Element ele){
        Document document = ele.getOwnerDocument();
        DOMImplementationLS domImplLS = (DOMImplementationLS) document
                .getImplementation();
        LSSerializer serializer = domImplLS.createLSSerializer();
        String str = serializer.writeToString(ele);
        System.out.println("当前Parser解析的Element: " + str );
    }
}
