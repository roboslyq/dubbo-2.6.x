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
package com.alibaba.dubbo.container;

import com.alibaba.dubbo.common.extension.SPI;

/**
 * Container. (SPI, Singleton, ThreadSafe)
 * 1、Dubbo的Container是一个独立的容器，因为服务通常不需要Tomcat/JBoss等Web容器的特性，没必要用Web容器去加载服务。
 * 2、服务容器只是一个简单的Main方法，并加载一个简单的Spring容器，用于暴露服务。
 *    com.alibaba.dubbo.container.Main 是服务启动的主类
 * 3、Container接口有只有 start()  stop()他的实现类。 具体实现类有
 *          SpringContainer：Spring容器， 自动加载META-INF/spring目录下的所有Spring配置。最为关键的容器。
 *          Log4jContainer： 实现Log4j相关加载及配置，在多进程启动时，自动给日志文件按进程分目录。
 *          JavaConfigContainer
 *          LogbackContainer。
 * 4、当然你也可以自定义容器
 *    官网：http://dubbo.io/Developer+Guide.htm#DeveloperGuide-ContainerSPI
 *
 * 5、容器启动
 *
 * 如：(缺省只加载spring)
 *      java com.alibaba.dubbo.container.Main
 * 或：(通过main函数参数传入要加载的容器)
 *      java com.alibaba.dubbo.container.Main spring jetty log4j
 * 或：(通过JVM启动参数传入要加载的容器)
 *      java com.alibaba.dubbo.container.Main -Ddubbo.container=spring,jetty,log4j
 * 或：(通过classpath下的dubbo.properties配置传入要加载的容器)
 *      dubbo.properties
 *      dubbo.container=spring,jetty,log4j
 */
@SPI("spring")
public interface Container {

    /**
     * Dubbo容器启动
     * start.
     */
    void start();

    /**
     * Dubbo容器停止
     * stop.
     */
    void stop();

}