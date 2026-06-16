package dev.deepdive.sandbox;

public class Person {

    public String name;
    private int age;
    private static String species = "Human";

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    private Person(String name) {
        this.name = name;
        this.age = 0;
    }

    public String greet() {
        return "안녕하세요, " + name + "입니다.";
    }

    private String secret() {
        return name + "의 비밀 나이: " + age;
    }

    private static String getSpecies() {
        return species;
    }
}
