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
package com.alibaba.dubbo.rpc.service;

/**
 * Echo service.
 *
 * 1、回声测试用于检测服务是否可用，回声测试按照正常请求流程执行，能够测试整个调用是否通畅，可用于监控。
 * 2、Dubbo中所有的服务都实现了一个EchoService，可以在Java程序中进行调用，测试服务是否正常。
 * 3、只有消费端，Reference，才会实现此接口，服务端不会实现此接口。
 * 4、服务端通过{@link com.alibaba.dubbo.rpc.filter.EchoFilter}。此接口直接模拟返回，不会继续调用后面的服务。
 * @export
 */
public interface EchoService {

    /**
     * echo test.
     * Dubbo 可以为服务echo属性，
     * @param message message.
     * @return message.
     */
    Object $echo(Object message);

}