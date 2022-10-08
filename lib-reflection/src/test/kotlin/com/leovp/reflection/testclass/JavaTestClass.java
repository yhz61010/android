package com.leovp.reflection.testclass;

import androidx.annotation.NonNull;

/**
 * Author: Michael Leo
 * Date: 2022/10/8 10:38
 */
public class JavaTestClass {
    public static class NoArgClass {

        private Integer getFixedCode() {
            return 10086;
        }

        private int secret;

        public int getSecret() {
            return secret;
        }

        public void setSecret(int secret) {
            this.secret = secret;
        }

        @NonNull
        @Override
        public String toString() {
            return "Got a NoArgClass.";
        }
    }

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

        JavaPerson(String name, char sex) {
            this(name, sex, -1);
        }

        JavaPerson(String name, char sex, int age) {
            this.name = name;
            this.sex = sex;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public char getSex() {
            return sex;
        }

        public void setSex(char sex) {
            this.sex = sex;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @NonNull
        @Override
        public String toString() {
            return "{name: \"" + name + "\", sex: \"" + sex + "\", age: " + age + "}";
        }
    }
}
