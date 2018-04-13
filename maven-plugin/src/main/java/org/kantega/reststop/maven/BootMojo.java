/*
 * Copyright 2018 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.maven;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.kantega.reststop.bootstrap.Bootstrap;

import java.io.Console;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static java.util.Objects.nonNull;

/**
 *
 */
@Mojo(name = "boot",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDirectInvocation = true,
        requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PACKAGE)

public class BootMojo extends StartBootMojo {

    private CountDownLatch shutdownLatch = new CountDownLatch(1);

    @Override
    protected void preExecute() {
        registerBuildSystem();
    }

    @Override
    public List<Plugin> getPlugins() {
        List<Plugin> plugins = super.getPlugins();

        addDevelopmentPlugins(plugins);

        return plugins;
    }

    @Override
    protected void postExecute(List<Bootstrap> bootstraps) {
        Console systemConsole = System.console();
        if (nonNull(systemConsole)) {
            getLog().info("Reststop has started. Type CTRL-D to shut down");

            new Thread(() -> {

                Reader reader = systemConsole.reader();
                int i = 0;
                try {
                    do {
                        i = reader.read();
                    } while (i != -1);

                    getLog().info("Reststop is shutting down..");
                    Collections.reverse(bootstraps);
                    for (Bootstrap bootstrap : bootstraps) {
                        bootstrap.shutdown();
                    }
                    getLog().info("Reststop shutdown completed");
                    shutdownLatch.countDown();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
