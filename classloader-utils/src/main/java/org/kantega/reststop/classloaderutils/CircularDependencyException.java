package org.kantega.reststop.classloaderutils;

/**
 *
 */
public class CircularDependencyException extends RuntimeException {
    public CircularDependencyException(String message) {
        super(message);
    }
}
