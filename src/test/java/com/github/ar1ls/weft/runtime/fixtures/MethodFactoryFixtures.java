package com.github.ar1ls.weft.runtime.fixtures;

public class MethodFactoryFixtures {

    public ConstructorDependentBean dependent(SimpleBean dependency) {
        return new ConstructorDependentBean(dependency);
    }

    public static SimpleBean simple() {
        return new SimpleBean();
    }

    public static SimpleBean nullBean() {
        return null;
    }

    public static SimpleBean throwingBean() {
        throw new IllegalStateException("Method factory probe");
    }

    public void noReturn() {
    }

    public int primitive() {
        return 1;
    }

    private SimpleBean hidden() {
        return new SimpleBean();
    }

}
