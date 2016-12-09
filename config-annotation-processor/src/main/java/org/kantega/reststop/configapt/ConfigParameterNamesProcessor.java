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

package org.kantega.reststop.configapt;

import org.kantega.reststop.api.config.Config;
import org.kantega.reststop.classloaderutils.config.PluginConfigParam;
import org.kantega.reststop.classloaderutils.config.PluginConfigParams;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ErrorType;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/**
 *
 */
@SupportedAnnotationTypes("org.kantega.reststop.api.Plugin")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ConfigParameterNamesProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {

                List<? extends Element> enclosedElements = element.getEnclosedElements();

                PluginConfigParams params = new PluginConfigParams();

                for (Element enclosedElement : enclosedElements) {
                    if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {

                        ExecutableElement constructor = (ExecutableElement) enclosedElement;

                        List<? extends VariableElement> parameters = constructor.getParameters();

                        for (VariableElement parameter : parameters) {
                            if(parameter.asType() instanceof ErrorType) {
                                continue;
                            }
                            Config configAnnotation = parameter.getAnnotation(Config.class);
                            if(configAnnotation != null) {
                                PluginConfigParam param = new PluginConfigParam();
                                param.setType(parameter.asType().toString());
                                param.setDefaultValue(configAnnotation.defaultValue());
                                param.setDoc(configAnnotation.doc());
                                String name = configAnnotation.property();
                                if(name.equals("")) {
                                    name = parameter.getSimpleName().toString();
                                }
                                param.setParamName(name);
                                param.setRequired(configAnnotation.required());
                                params.add(param);
                            }
                        }
                    }
                }

                TypeElement clazzElem = (TypeElement) element;
                PackageElement packageElement = (PackageElement) clazzElem.getEnclosingElement();

                try {
                    FileObject configParams = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                            packageElement.getQualifiedName(),
                            clazzElem.getSimpleName() + ".config-params",
                            element);

                    try (OutputStream outputStream = configParams.openOutputStream()) {
                        JAXBContext.newInstance(PluginConfigParams.class).createMarshaller().marshal(params, outputStream);
                    }
                } catch (IOException | JAXBException e) {
                    throw new RuntimeException(e);
                }



            }
        }
        return false;
    }
}
