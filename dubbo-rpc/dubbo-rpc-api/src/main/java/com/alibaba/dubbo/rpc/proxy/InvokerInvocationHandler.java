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
package com.alibaba.dubbo.rpc.proxy;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 1、消费者发起接口调用<Proxy0代理类中>,触发此方法调用InvokerHandler：Invoker代理类，此处的Invoke()方法会调用真正的Invoker实现
 * 2、具体初始化详情见{@link ProxyFactory#getProxy },具体实现为{@link JavassistProxyFactory} 和{@link JdkProxyFactory}
 *    其中，默认实现是{@link JavassistProxyFactory}
 */
public class InvokerInvocationHandler implements InvocationHandler {

    //此变量类型为MockClusterInvoker 内部封装了服务降级逻辑。
    private final Invoker<?> invoker;

    /**
     *
     * @param handler
     */
    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    /**
     *
     * @param proxy 被代理对象: 此对象是用javaassit、asm等字节码生成的代理类，比如 Proxy0等
     *              仅作为一个入参，完全没有使用。因为dubbo远程调用仅需要类名，方法名及参数等信息。
     *              完全不需要代理类。
     * @param method 调用方法
     * @param args  调用参数
     * @return
     * @throws Throwable
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 拦截定义在 Object 类中的方法（未被子类重写），比如 wait/notify
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        // 如果 toString、hashCode 和 equals 等方法被子类重写了，这里也直接调用
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        /*
         * 1、此inovker实例为：MockClusterInvoker
         * 2、将 method 和 args 封装到 RpcInvocation 中，并执行后续的调用创建上下文Invocation
         */
        return invoker.invoke(new RpcInvocation(method, args)
            ).recreate();
    }

}