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
package com.alibaba.dubbo.common.serialize;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serialization. (SPI, Singleton, ThreadSafe)
 * Dubbo序列化实现，默认是hession2
 * 1、序列化和反序列化的定义：
 *    (1)Java序列化就是指把Java对象转换为字节序列或JSON或XML等过程。
 *       Java反序列化就是指把字节序列或JSON或XMl等恢复为Java对象的过程。
 *    (2)序列化最重要的作用：在传递和保存对象时.保证对象的完整性和可传递性。对象转换为有序字节流,以便在网络上传输或者保存在本地文件中。
 *       反序列化的最重要的作用：根据字节流中保存的对象状态及描述信息，通过反序列化重建对象。
 * 总结：核心作用就是对象状态的保存和重建。（整个过程核心点就是字节流中所保存的对象状态及描述信息）
 *
 * 2、序列化相关抽象
 *    (1)ObjectOutput/DataOutput : 序列化数据
 *    (2)ObjectInput/DataInput：反序列化数据
 */
@SPI("hessian2")
public interface Serialization {

    /**
     * get content type id
     * 内容类型ID
     * @return content type id
     */
    byte getContentTypeId();

    /**
     * get content type
     * 内容类型
     * @return content type
     */
    String getContentType();

    /**
     * create serializer
     * 序列化字节流
     * @param url
     * @param output
     * @return serializer
     * @throws IOException
     */
    @Adaptive
    ObjectOutput serialize(URL url, OutputStream output) throws IOException;

    /**
     * create deserializer
     * 反序列化：根据输入流解析为ObjectInput对象
     * @param url
     * @param input
     * @return deserializer
     * @throws IOException
     */
    @Adaptive
    ObjectInput deserialize(URL url, InputStream input) throws IOException;

}