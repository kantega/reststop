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

    @Test
    public void shouldSortByDependencies() {
        List<PluginInfo> infos = new ArrayList<>();

        PluginInfo a = new PluginInfo();
        a.setGroupId("com.example");
        a.setArtifactId("a");
        a.setVersion("1.0");


        PluginInfo b = new PluginInfo();
        b.setGroupId("com.example");
        b.setArtifactId("b");
        b.setVersion("1.0");


        PluginInfo c = new PluginInfo();
        c.setGroupId("com.example");
        c.setArtifactId("c");
        c.setVersion("1.0");


        infos.add(a);
        infos.add(b);
        infos.add(c);

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
}
