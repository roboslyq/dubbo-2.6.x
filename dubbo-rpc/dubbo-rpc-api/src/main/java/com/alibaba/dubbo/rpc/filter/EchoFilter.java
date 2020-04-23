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
package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;

/**
 * EchoInvokerFilter
 * 1、服务端的拦截器
 * 2、回声测试拦截器，如果消费者调用的是回声测试方法，则在此拦截器直接返回。不需要走后面具体的服务。可以做探测使用
 * 3、在提供者端，在解析报文，实际调用invoker之前，通过Filter拦截链中的EchoFilter判断方法是否为echo，如果是echo，
 *    不进行invoker调用，走EchoFilter内部的调用逻辑。
 */
@Activate(group = Constants.PROVIDER, order = -110000)
public class EchoFilter implements Filter {

    public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
        //如果方法名称为$echo并且参数不为空，并且只有一个参数，这三个条件均满足，我们认为是回声测试方法。
        if (inv.getMethodName().equals(Constants.$ECHO) && inv.getArguments() != null && inv.getArguments().length == 1)
            return new RpcResult(inv.getArguments()[0]);
        return invoker.invoke(inv);
    }

}