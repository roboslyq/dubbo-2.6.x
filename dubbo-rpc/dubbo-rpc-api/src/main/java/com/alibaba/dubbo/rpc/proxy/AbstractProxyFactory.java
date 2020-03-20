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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.service.EchoService;

/**
 * AbstractProxyFactory
 */
public abstract class AbstractProxyFactory implements ProxyFactory {
    /**
     * 创建代理类《服务服务端和消费端》,示例DEMO如下<具体clase内容生成见<http://dubbo.apache.org/zh-cn/docs/source_code_guide/service-invoking-process.html>:
     *
     * public class proxy0 implements ClassGenerator.DC, EchoService, DemoService {
     *     // 方法数组
     *     public static Method[] methods;
     *     private InvocationHandler handler;
     *
     *     public proxy0(InvocationHandler invocationHandler) {
     *         this.handler = invocationHandler;
     *     }
     *
     *     public proxy0() {
     *     }
     *
     *     public String sayHello(String string) {
     *         // 将参数存储到 Object 数组中
     *         Object[] arrobject = new Object[]{string};
     *         // 调用 InvocationHandler 实现类的 invoke 方法得到调用结果
     *         Object object = this.handler.invoke(this, methods[0], arrobject);
     *         // 返回调用结果
     *         return (String)object;
     *     }
     *
     *  // 回声测试方法
     *   public Object $echo(Object object) {
     *          Object[] arrobject = new Object[]{object};
     *          Object object2 = this.handler.invoke(this, methods[1], arrobject);
     *          return object2;
     *      }
     *  }
     *
     * @param invoker
     * @param <T>
     * @return
     * @throws RpcException
     */
    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        Class<?>[] interfaces = null;
        String config = invoker.getUrl().getParameter("interfaces");
        if (config != null && config.length() > 0) {
            String[] types = Constants.COMMA_SPLIT_PATTERN.split(config);
            if (types != null && types.length > 0) {
                interfaces = new Class<?>[types.length + 2];
                interfaces[0] = invoker.getInterface();
                interfaces[1] = EchoService.class;
                for (int i = 0; i < types.length; i++) {
                    interfaces[i + 1] = ReflectUtils.forName(types[i]);
                }
            }
        }
        if (interfaces == null) {
            interfaces = new Class<?>[]{invoker.getInterface(), EchoService.class};
        }
        //===>核心入口
        return getProxy(invoker, interfaces);
    }

    /**
     * 具体实现待子类去完成：
     *      JavassistProxyFactory
     *      JdkProxyFactory
     * @param invoker
     * @param types
     * @param <T>
     * @return
     */
    public abstract <T> T getProxy(Invoker<T> invoker, Class<?>[] types);

}