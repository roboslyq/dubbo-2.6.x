package com.alibaba.dubbo.demo.consumer;

import com.alibaba.dubbo.common.bytecode.ClassGenerator;
import com.alibaba.dubbo.demo.DemoService;
import com.alibaba.dubbo.rpc.service.EchoService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
/**
 * 消费者端的代理类源码(示例)
 * 当消费者调用demoService.sayHello("")时，实现调用下面的源码。
 */
public class Proxy0 implements ClassGenerator.DC, EchoService, DemoService {
    // 方法数组:此数据有排序，然后根据排序选择具体的方法。
    public static Method[] methods;

    // InvocationHanler，具体实现为InvokerInvocationHandler
    private InvocationHandler handler;

    public Proxy0(InvocationHandler invocationHandler) {
        this.handler = invocationHandler;
    }

    public Proxy0() {
    }

    public String sayHello(String string) throws Throwable {
        // 将参数存储到 Object 数组中
        Object[] arrobject = new Object[]{string};
        // 调用 InvocationHandler 实现类的 invoke 方法得到调用结果
        Object object = this.handler.invoke(this, methods[0], arrobject);
        // 返回调用结果
        return (String) object;
    }

    // 回声测试方法
    public Object $echo(Object object)   {
        Object[] arrobject = new Object[]{object};
        Object object2 = null;
        try {
            object2 = this.handler.invoke(this, methods[1], arrobject);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return object2;
    }
}