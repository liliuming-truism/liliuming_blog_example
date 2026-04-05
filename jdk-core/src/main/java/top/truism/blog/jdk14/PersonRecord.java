package top.truism.blog.jdk14;

public record PersonRecord(String name, int age) implements Comparable<PersonRecord>{
    public static final int ADULT_AGE = 18;
    // 自定义构造函数
    public PersonRecord(String name, int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age must be positive");
        }
        this.name = name;
        this.age = age;
    }

    public boolean isAdult() {
        return age >= 18;
    }

    public String getFullInfo() {
        return String.format("%s (%d years old)", name, age);
    }

    public static PersonRecord createChild(String name) {
        return new PersonRecord(name, 0);
    }

    @Override
    public int compareTo(PersonRecord o) {
        return Integer.compare(age, o.age);
    }
}
