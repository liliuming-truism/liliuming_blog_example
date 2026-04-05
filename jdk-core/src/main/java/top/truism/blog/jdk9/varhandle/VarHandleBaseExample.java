package top.truism.blog.jdk9.varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleBaseExample {

    private int value;
    private static final VarHandle VALUE_HANDLE;

    private static volatile String sharedValue = "initial";
    private static final VarHandle SHARED_VALUE_HANDLE;

    private int[] array = new int[10];
    private static final VarHandle ARRAY_HANDLE =
        MethodHandles.arrayElementVarHandle(int[].class);

    static {
        try {
            VALUE_HANDLE = MethodHandles.lookup()
                .findVarHandle(VarHandleBaseExample.class, "value", int.class);

            SHARED_VALUE_HANDLE = MethodHandles.lookup()
                .findStaticVarHandle(VarHandleBaseExample.class, "sharedValue", String.class);


        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) {
        VarHandleBaseExample varHandleBaseExample = new VarHandleBaseExample();

        varHandleBaseExample.plainAccess();

        varHandleBaseExample.volatileAccess();

        System.out.println(varHandleBaseExample.compareAndExchange(36, 42));
        System.out.println(varHandleBaseExample.compareAndExchange(40, 42));


    }

    private void plainAccess() {
        System.out.println("=========Plaint Access=========");
        VALUE_HANDLE.set(this, 42);
        System.out.println("Plain mode value: " + (int) VALUE_HANDLE.get(this));

        ARRAY_HANDLE.set(array, 0, value);

        System.out.println("Plain mode array: " + (int) ARRAY_HANDLE.get(array, 0));;
    }

    private void volatileAccess() {
        System.out.println("=========Volatile Access=========");
        VALUE_HANDLE.setVolatile(this, 36);
        System.out.println("Volatile mode value: " + (int) VALUE_HANDLE.getVolatile(this));

        ARRAY_HANDLE.setVolatile(array, 0, value);
        System.out.println("Volatile mode array: " + (int) ARRAY_HANDLE.getVolatile(array, 0));
    }

    private int compareAndExchange(int expected, int updated) {
        return (int) VALUE_HANDLE.compareAndExchange(this, expected, updated);
    }
}
