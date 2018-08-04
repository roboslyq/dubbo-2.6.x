package com.roboslyq.test.anonymous;

public class HelloWorld {
    public static void main(String[] args) {
        //4.�����ڲ���
        //��Ҫ�������Щ����ֱ�Ӵ�������ĳ�����ͽӿڶ�����
        Student stu=new Student();
        System.out.println(stu.getClass());//class com.aowin.noname.Student
        //4.1.ͨ��ʵ���ഴ�������ڲ������
        //�൱�ڴ��������һ���������
        Person p=new Person(){
            public void eat(){
                System.out.println("�԰�Ԫ�ײ�");
            }
        };
        p.eat();
        System.out.println(p.getClass());//class com.aowin.noname.Test$1//ϵͳ�Զ�Ϊ��������Test$1
        
        Dog dog=new Dog();
        dog.bark();
        //4.2.ͨ�������ഴ�������ڲ������
        //�൱�ڶ����˸ó������һ���������,����д�˳����������еĳ��󷽷�
        Animal an=new Animal(){
            public void bark(){
                System.out.println("������...");
            }
        };
        an.bark();
        //���ص��ǰ���������
        System.out.println(an.getClass());//class com.aowin.noname.Test$2
        
        //4.3.ͨ���ӿڴ��������ڲ������
        //�൱�ڶ����˸ýӿڵ�һ��ʵ�������,����д�˽ӿ������еĳ��󷽷�
        Sportable s=new Sportable(){
            public void sport(){
                System.out.println("������");
            }
        };
        s.sport();
        System.out.println(s.getClass());//class com.aowin.noname.Test$3
        
    }
}
//ʵ����
class Person{
    public void eat(){
        System.out.println("�Է�");
    }
}
class Student extends Person{
    public void eat(){
        System.out.println("�԰�Ԫ�ײ�");
    }
}
//������
abstract class Animal{
    public abstract void bark();
}
class Dog extends Animal{
    public void bark() {
        System.out.println("����...");
    }
}
//�ӿ�
interface Sportable{
    public abstract void sport();
}
