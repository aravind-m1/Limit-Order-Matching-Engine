package com.matchforge.testharness;

import java.util.Objects;

public final class Assertions {
    private Assertions() {}

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void assertEqualsObj(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void assertNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }
}
