package org.kantega.reststop.custom.api;

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.ReststopPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class DefaultCustomAppPlugin extends DefaultReststopPlugin implements CustomAppPlugin{

    private final List<String> greetings = new ArrayList<>();

    @Override
    public Collection<String> getGreetings() {
        return Collections.unmodifiableCollection(greetings);
    }

    public void addGreeting(String greeting) {
        greetings.add(greeting);
    }
}
