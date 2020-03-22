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
package com.alibaba.dubbo.common.serialize.support.hessian;

import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.alibaba.dubbo.common.serialize.ObjectOutput;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Hessian2 Object output.
 * 默认的序列化协议：hession2
 */
public class Hessian2ObjectOutput implements ObjectOutput {
    private final Hessian2Output mH2o;

    /**
     * 构造函数，初始化 Hessian2Output
     * @param os
     */
    public Hessian2ObjectOutput(OutputStream os) {
        mH2o = new Hessian2Output(os);
        mH2o.setSerializerFactory(Hessian2SerializerFactory.SERIALIZER_FACTORY);
    }

    /**
     * 写boolean
     * @param v value.
     * @throws IOException
     */
    public void writeBool(boolean v) throws IOException {
        mH2o.writeBoolean(v);
    }

    /**
     * 写byte
     * @param v value.
     * @throws IOException
     */
    public void writeByte(byte v) throws IOException {
        mH2o.writeInt(v);
    }

    /**
     * 写short
     * @param v value.
     * @throws IOException
     */
    public void writeShort(short v) throws IOException {
        mH2o.writeInt(v);
    }

    /**
     * 写int
     * @param v value.
     * @throws IOException
     */
    public void writeInt(int v) throws IOException {
        mH2o.writeInt(v);
    }

    /**
     * 写Long
     * @param v value.
     * @throws IOException
     */
    public void writeLong(long v) throws IOException {
        mH2o.writeLong(v);
    }

    /**
     * 写Float
     * @param v value.
     * @throws IOException
     */
    public void writeFloat(float v) throws IOException {
        mH2o.writeDouble(v);
    }

    /**
     * 写Double
     * @param v value.
     * @throws IOException
     */
    public void writeDouble(double v) throws IOException {
        mH2o.writeDouble(v);
    }

    /**
     * 写byte[]数组，默认全部
     * @param b
     * @throws IOException
     */
    public void writeBytes(byte[] b) throws IOException {
        mH2o.writeBytes(b);
    }

    /**
     * 写byte[]数据，指定起始和结束位置
     * @param b
     * @param off offset.
     * @param len length.
     * @throws IOException
     */
    public void writeBytes(byte[] b, int off, int len) throws IOException {
        mH2o.writeBytes(b, off, len);
    }

    /**
     * 写String
     * @param v value.
     * @throws IOException
     */
    public void writeUTF(String v) throws IOException {
        mH2o.writeString(v);
    }

    /**
     * 写对象Object
     * @param obj object.
     * @throws IOException
     */
    public void writeObject(Object obj) throws IOException {
        mH2o.writeObject(obj);
    }

    /**
     * 刷新缓存
     * @throws IOException
     */
    public void flushBuffer() throws IOException {
        mH2o.flushBuffer();
    }
}