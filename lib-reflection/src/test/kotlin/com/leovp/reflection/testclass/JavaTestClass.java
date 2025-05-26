package com.leovp.reflection.testclass;

import androidx.annotation.NonNull;
import kotlin.Suppress;

/**
 * Author: Michael Leo
 * Date: 2022/10/8 10:38
 */
@SuppressWarnings({"unused", "MemberVisibilityCanBePrivate"})
public class JavaTestClass {
    public static class NoArgClass {

        public static void print(String content) {
            System.out.println("NoArgClass print: " + content);
        }

        public static String say(String content) {
            return "NoArgClass say: " + content;
        }

        private static String privateMessage(String content) {
            return "PM: " + content;
        }

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

        @SuppressWarnings("FieldCanBeLocal")
        private int onlyPrivate = 1234;

        // ===== https://stackoverflow.com/a/14102192/1685062
//        private static final String NO_NAME = new String("No Name");
//        public static final String PUBLIC_NAME = new String("Public Name");
//        public static final Integer PUBLIC_STATIC_FINAL_INT = new Integer(10); // 10
        // ===== End

        private final String PRIVATE_FINAL = "Private Final";
        public final String PUBLIC_FINAL = "Public Final";

        public String openField = "Open Field";

        public String getSexInString() {
            return switch (sex) {
                case 'M' -> "Male";
                case 'F' -> "Female";
                default -> "NA";
            };
        }

        JavaPerson(String name, char sex) {
            this(name, sex, -1);
        }

        JavaPerson(String name, char sex, int age) {
            this.name = name;
            this.sex = sex;
            this.age = age;
        }

        private int changeOnlyPrivate(int newVal) {
            onlyPrivate = newVal;
            return onlyPrivate;
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
