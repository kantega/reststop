package org.kantega.reststop.classloaderutils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class PluginInfoTest {
    private final PluginInfo c;
    private final PluginInfo a;
    private final PluginInfo b;
    private final List<PluginInfo> infos;

    public PluginInfoTest() {
        a = new PluginInfo();
        a.setGroupId("com.example");
        a.setArtifactId("a");
        a.setVersion("1.0");


        b = new PluginInfo();
        b.setGroupId("com.example");
        b.setArtifactId("b");
        b.setVersion("1.0");


        c = new PluginInfo();
        c.setGroupId("com.example");
        c.setArtifactId("c");
        c.setVersion("1.0");

        infos = new ArrayList<>();

        infos.add(a);
        infos.add(b);
        infos.add(c);
    }

    @Test
    public void shouldSortByDependencies() {

        // c depends on b
        c.addDependsOn(b);
        // b depends on a
        b.addDependsOn(a);

        for(int i = 0; i < 200; i++) {
            Collections.shuffle(infos);
        }

        List<PluginInfo> sorted = PluginInfo.resolveStartupOrder(infos);

        assertThat(a, is(sorted.get(0)));
        assertThat(b, is(sorted.get(1)));
        assertThat(c, is(sorted.get(2)));

    }

    @Test
    public void treeShouldSortByDependencies() {

        // a depends on b
        c.addDependsOn(b);
        // a depends on c
        b.addDependsOn(a);

        for(int i = 0; i < 200; i++) {
            Collections.shuffle(infos);
        }

        List<PluginInfo> sorted = PluginInfo.resolveStartupOrder(infos);

        assertThat(a, is(sorted.get(0)));
    }

    @Test(expected = CircularDependencyException.class)
    public void shouldDetectCircularDependency() {

        // c depends on b
        c.addDependsOn(b);
        // b depends on a
        b.addDependsOn(a);
        // a depends on c
        a.addDependsOn(c);



        PluginInfo.resolveStartupOrder(infos);
    }
}
