package org.kantega.reststop.helloworld.wicket;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

import java.util.Date;

/**
 *
 */
public class HelloPage extends WebPage {
    public HelloPage() {
        add(new Label("time", new Date().toString()));
    }
}
