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
 * 2、服务容器只是一个简单的Main方法，并加载一个简单的Spring容器，用于暴露服务。com.alibaba.dubbo.container.Main 是服务启动的主类
 * 3、Container接口有只有 start()  stop()他的实现类。实现类有SpringContainer、Log4jContainer、JettyContainer、JavaConfigContainer、LogbackContainer。
 * 4、当然你也可以自定义容器
 * 官网：http://dubbo.io/Developer+Guide.htm#DeveloperGuide-ContainerSPI
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