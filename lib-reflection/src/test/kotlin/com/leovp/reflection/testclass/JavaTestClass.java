package com.leovp.reflection.testclass;

import androidx.annotation.NonNull;

/**
 * Author: Michael Leo
 * Date: 2022/10/8 10:38
 */
public class JavaTestClass {
    public static class JavaPerson {
        private String name;
        private char sex;
        private int age;

        public String getSexInString() {
            switch (sex) {
                case 'M':
                    return "Male";
                case 'F':
                    return "Female";
                default:
                    return "NA";
            }
        }

        JavaPerson(String name, char sex, int age) {
            this.name = name;
            this.sex = sex;
            this.age = age;
        }

        @NonNull
        @Override
        public String toString() {
            return "{name: \"" + name + "\", sex: \"" + sex + "\", age: " + age + "}";
        }
    }
}
