package org.kantega.reststop.helloworld;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class HelloTest {

    @Test
    public void shouldSayHello() {
        assertThat("Hello", is(new Hello("Hello").getMessage()));
    }
}
