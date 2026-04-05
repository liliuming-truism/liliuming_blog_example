package top.truism.blog.jdk14;

public class RecordApp {

    public static void main(String[] args) {
        PersonRecord personRecord = new PersonRecord("Truism", 18);
        System.out.println(personRecord.name());
        System.out.println(personRecord.age());
        System.out.println(personRecord);
        System.out.println(personRecord.isAdult());

    }

}
