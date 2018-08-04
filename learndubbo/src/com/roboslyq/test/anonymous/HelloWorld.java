package com.roboslyq.test.anonymous;

public class HelloWorld {
    public static void main(String[] args) {
        //4.匿名内部类
        //主要是针对那些不能直接创建对象的抽象类和接口而来的
        Student stu=new Student();
        System.out.println(stu.getClass());//class com.aowin.noname.Student
        //4.1.通过实体类创建匿名内部类对象
        //相当于创建该类的一个子类对象
        Person p=new Person(){
            public void eat(){
                System.out.println("吃八元套餐");
            }
        };
        p.eat();
        System.out.println(p.getClass());//class com.aowin.noname.Test$1//系统自动为子类命名Test$1
        
        Dog dog=new Dog();
        dog.bark();
        //4.2.通过抽象类创建匿名内部类对象
        //相当于定义了该抽象类的一个子类对象,并重写了抽象类中所有的抽象方法
        Animal an=new Animal(){
            public void bark(){
                System.out.println("汪汪汪...");
            }
        };
        an.bark();
        //返回的是包名加类名
        System.out.println(an.getClass());//class com.aowin.noname.Test$2
        
        //4.3.通过接口创建匿名内部类对象
        //相当于定义了该接口的一个实现类对象,并重写了接口中所有的抽象方法
        Sportable s=new Sportable(){
            public void sport(){
                System.out.println("打篮球");
            }
        };
        s.sport();
        System.out.println(s.getClass());//class com.aowin.noname.Test$3
        
    }
}
//实体类
class Person{
    public void eat(){
        System.out.println("吃饭");
    }
}
class Student extends Person{
    public void eat(){
        System.out.println("吃八元套餐");
    }
}
//抽象类
abstract class Animal{
    public abstract void bark();
}
class Dog extends Animal{
    public void bark() {
        System.out.println("汪汪...");
    }
}
//接口
interface Sportable{
    public abstract void sport();
}
