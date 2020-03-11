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
package com.alibaba.dubbo.rpc.cluster;

import com.alibaba.dubbo.common.Node;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Directory. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Directory_service">Directory Service</a>
 * Directory目录服务，即对Invoker的包装。
 * 1 、集群目录服务Directory， 代表多个Invoker, 可以看成List<Invoker>,它的值可能是动态变化的比如注册中心推送变更。
 *     集群选择调用服务时通过目录服务找到所有服务
 *
 * 2、常见实现有如下两种：
 * （1）StaticDirectory: 静态目录服务， 它的所有Invoker通过构造函数传入， 服务消费方引用服务的时候，
 *                      服务对多注册中心的引用，将Invokers集合直接传入 StaticDirectory构造器，再由Cluster伪装成一个Invoker；
 *                      StaticDirectory的list方法直接返回所有invoker集合；
 *
 * （2）RegistryDirectory: 注册目录服务， 它的Invoker集合是从注册中心获取的， 它实现了NotifyListener接口实现了回调接口notify(List<Url>)
 *
 * 通俗的来说，就是一个缓存和更新缓存的过程
 * @see com.alibaba.dubbo.rpc.cluster.Cluster#join(Directory)
 */
public interface Directory<T> extends Node {

    /**
     * get service type.
     * 获取服务接口类型
     * @return service type.
     */
    Class<T> getInterface();

    /**
     * list invokers.
     * 获取指定服务xxxxxService所有可用的Invoker信息(N个注册中心，每个注册中心一个当前服务)
     * @return invokers
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

}