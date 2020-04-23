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
package com.alibaba.dubbo.demo.consumer;

import com.alibaba.dubbo.rpc.service.EchoService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.demo.DemoService;
import com.alibaba.dubbo.rpc.Protocol;

public class Consumer {

    public static void main(String[] args) {
        //Prevent to get IPV6 address,this way only work in debug mode
        //But you can pass use -Djava.net.preferIPv4Stack=true,then it work well whether in debug mode or not
        System.setProperty("java.net.preferIPv4Stack", "true");
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"META-INF/spring/dubbo-demo-consumer.xml"});
        context.start();
        DemoService demoService = (DemoService) context.getBean("demoService1"); // get remote service proxy
        DemoService demoService2 = (DemoService) context.getBean("demoService2"); // get remote service proxy
//		Protocol p = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
//		Protocol p1 = ExtensionLoader.getExtensionLoader(Protocol.class).getDefaultExtension();
//		Protocol p2 = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("xxxxxx");
        EchoService demoService3 = (EchoService)demoService2;
        demoService3.$echo("hello echo");

        while (true) {
            try {
                Thread.sleep(1000);
                String hello = demoService.sayHello("world"); // call remote method
                String hello2 = demoService2.sayHello("world"); // call remote method
                System.out.println(hello); // get result
                System.out.println(hello2); // get result

            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }


        }

    }
}
