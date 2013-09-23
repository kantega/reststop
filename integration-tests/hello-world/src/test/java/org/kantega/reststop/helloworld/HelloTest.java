package org.kantega.reststop.helloworld;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class HelloTest {

    @Test
    public void shouldSayHello() {
        assertEquals("Hello", new Hello("Hello").getMessage());
    }
}
