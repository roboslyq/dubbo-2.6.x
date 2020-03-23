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
package com.alibaba.dubbo.common;

/**
 * Node. (API/SPI, Prototype, ThreadSafe)
 *
 * 1、Dubbo中的一个核心抽象叫“URL”,URL基本封装了所有信息。详情可以参考{@link URL}
 * 2、既然URL如此重要，因此需要一个通用接口，来获取URL信息。此接口便是Node。
 * 3、目前有4种类型，实现了Node接口
 *  1) Registry
 *  2) Monitor
 *  3) Directory
 *  4) Invoker
 */
public interface Node {

    /**
     * get url.
     * 获取URL
     *
     * @return url.
     */
    URL getUrl();

    /**
     * is available.
     * 是否可用
     * @return available.
     */
    boolean isAvailable();

    /**
     * destroy.
     * 销毁
     */
    void destroy();

}