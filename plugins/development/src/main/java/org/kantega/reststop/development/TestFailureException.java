package org.kantega.reststop.development;

import org.junit.runner.notification.Failure;

import java.util.List;

/**
 *
 */
public class TestFailureException extends RuntimeException {
    private final List<Failure> failures;

    public TestFailureException(List<Failure> failures) {
        this.failures = failures;
    }

    public List<Failure> getFailures() {
        return failures;
    }
}
