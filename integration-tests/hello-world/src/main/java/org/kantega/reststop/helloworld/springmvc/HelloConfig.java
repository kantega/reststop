package org.kantega.reststop.helloworld.springmvc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 *
 */
@Configuration

public class HelloConfig {

    @Bean
    public HelloController helloController() {return new HelloController();}
}
