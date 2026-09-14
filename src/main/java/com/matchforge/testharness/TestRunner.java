package com.matchforge.testharness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal reflection-based test runner, standing in for JUnit (which
 * would normally be pulled in via Maven/Gradle in a networked dev
 * environment). Scans a test class for methods annotated @Test,
 * instantiates it fresh per method, and reports pass/fail. This is a
 * deliberately contained, justified use of reflection: discovering test
 * methods at runtime instead of hand-maintaining a list of them.
 */
public final class TestRunner {

    private TestRunner() {}

    public static boolean runAll(Class<?>... testClasses) {
        int total = 0, passed = 0;
        List<String> failures = new ArrayList<>();

        for (Class<?> clazz : testClasses) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) continue;
                total++;
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    m.setAccessible(true);
                    m.invoke(instance);
                    passed++;
                    System.out.println("PASS  " + clazz.getSimpleName() + "#" + m.getName());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    failures.add(clazz.getSimpleName() + "#" + m.getName() + " -> " + cause);
                    System.out.println("FAIL  " + clazz.getSimpleName() + "#" + m.getName() + " -> " + cause);
                }
            }
        }

        System.out.println();
        System.out.println(passed + "/" + total + " tests passed");
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            failures.forEach(f -> System.out.println("  " + f));
        }
        return failures.isEmpty();
    }
}
