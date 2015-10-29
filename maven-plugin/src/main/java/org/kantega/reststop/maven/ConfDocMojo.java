/*
 * Copyright 2015 Kantega AS
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@Mojo(name = "conf-doc", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ConfDocMojo extends AbstractReststopMojo {


    @Parameter(defaultValue = "${project.build.directory}/example.conf")

    private File generateExampleFile;

    @Parameter(required = true)
    private String applicationName;

    @Parameter(defaultValue =  "${basedir}/src/config")
    private File configDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            JAXBContext context = JAXBContext.newInstance(PluginConfigs.class);

            generateExampleFile.getParentFile().mkdirs();


            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(generateExampleFile), "iso-8859-1"))) {


                for (Plugin plugin : getPlugins()) {

                    File pluginFile = resolveArtifactFile(plugin.getCoords());

                    PluginConfigs configs = readPluginConfigs(pluginFile, context);


                    if (configs != null && hasProps(configs)) {




                        getLog().info("Found plugin " + plugin.getCoords());

                        for (PluginConfig config : configs) {
                            getLog().info("\tFound plugin class " + config.getClassName());
                            if (!config.getParams().isEmpty()) {
                                out.println("## " + plugin.getArtifactId() + " : "+ config.getClassName());
                                out.println("#");
                                out.println("");

                                for (PluginConfigParam param : config.getParams()) {
                                    getLog().info("\t\tFound plugin param " + param.getParamName());
                                    if (hasDefaultValue(param)) {
                                        out.print("# ");
                                    }
                                    out.print(param.getParamName());
                                    out.print("=");
                                    if (hasDefaultValue(param)) {
                                        out.print(param.getDefaultValue());
                                    }
                                    out.println();
                                    out.println();

                                }

                            }
                            out.println();
                            out.println();
                        }
                    }
                }
            }
        } catch (IOException | JAXBException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private boolean hasProps(PluginConfigs configs) {
        for (PluginConfig config : configs) {
            if(!config.getParams().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDefaultValue(PluginConfigParam param) {
        return param.getDefaultValue() != null && !"".equals(param.getDefaultValue());
    }

    private PluginConfigs readPluginConfigs(File pluginFile, JAXBContext context) throws JAXBException, IOException {
        try (JarFile jarFile = new JarFile(pluginFile)) {
            ZipEntry entry = jarFile.getEntry("META-INF/services/ReststopPlugin/config-params.xml");
            if (entry != null) {
                try (InputStream is = jarFile.getInputStream(entry)) {
                    return (PluginConfigs) context.createUnmarshaller().unmarshal(is);
                }

            }
        }
        return null;
    }
}
