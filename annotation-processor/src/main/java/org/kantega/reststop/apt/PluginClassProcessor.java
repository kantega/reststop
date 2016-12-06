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

package org.kantega.reststop.apt;

import org.kantega.reststop.api.Config;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.classloaderutils.config.PluginConfigParam;
import org.kantega.reststop.classloaderutils.config.PluginConfigParams;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@SupportedAnnotationTypes("org.kantega.reststop.api.Plugin")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PluginClassProcessor extends AbstractProcessor {

    Set<String> pluginClasses = new TreeSet<>();
    private File pluginsDescriptorFile;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/ReststopPlugin/simple.txt");
            pluginsDescriptorFile = new File(resource.toUri().getPath());
            String content = resource.getCharContent(true).toString();

            pluginClasses.addAll(Arrays.asList(content.split("\n")));
            pluginClasses.remove("");

        } catch (FileNotFoundException | NoSuchFileException e) {
            //Ignore
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {

                List<? extends Element> enclosedElements = element.getEnclosedElements();

                List<String> parameterNames = new ArrayList<>();

                Set<String> imports = new TreeSet<>();
                Set<String> exports = new TreeSet<>();

                PluginConfigParams params = new PluginConfigParams();

                for (Element enclosedElement : enclosedElements) {
                    if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {

                        ExecutableElement constructor = (ExecutableElement) enclosedElement;

                        List<? extends VariableElement> parameters = constructor.getParameters();

                        for (VariableElement parameter : parameters) {
                            if(parameter.asType() instanceof ErrorType) {
                                continue;
                            }
                            Name simpleName = parameter.getSimpleName();
                            parameterNames.add(simpleName.toString());
                            Config configAnnotation = parameter.getAnnotation(Config.class);
                            if(configAnnotation == null) {
                                if(isCollection(parameter.asType())) {
                                    DeclaredType type  = (DeclaredType) parameter.asType();
                                    DeclaredType typeArgument = (DeclaredType) type.getTypeArguments().get(0);

                                    if(typeArgument instanceof ErrorType) {
                                        continue;
                                    }
                                    if(isPluginExport(typeArgument)) {
                                        imports.add(typeArgument.getTypeArguments().get(0).toString());
                                    } else {
                                        imports.add(typeArgument.toString());
                                    }

                                } else {
                                    imports.add(parameter.asType().toString());
                                }
                            } else {
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

                    } else if(enclosedElement.getKind() == ElementKind.FIELD) {
                        if(enclosedElement.getAnnotation(Export.class) != null) {
                            if(enclosedElement.asType() instanceof ErrorType) {
                                continue;
                            }
                            if (isCollection(enclosedElement.asType())) {
                                TypeMirror typeArgument = ((DeclaredType) enclosedElement.asType()).getTypeArguments().get(0);
                                if(typeArgument instanceof ErrorType) {
                                    continue;
                                }
                                exports.add(typeArgument.toString());
                            } else {
                                exports.add(enclosedElement.asType().toString());
                            }
                        }
                    }
                }

                TypeElement clazzElem = (TypeElement) element;
                PackageElement packageElement = (PackageElement) clazzElem.getEnclosingElement();

                try {
                    FileObject parameterNamesFile = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                            packageElement.getQualifiedName(),
                            clazzElem.getSimpleName() + ".parameternames",
                            element);

                    try (Writer writer = parameterNamesFile.openWriter()) {
                        writer.append(parameterNames.stream().collect(Collectors.joining(",")));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    FileObject parameterNamesFile = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                            packageElement.getQualifiedName(),
                            clazzElem.getSimpleName() + ".imports",
                            element);

                    try (Writer writer = parameterNamesFile.openWriter()) {
                        writer.append(imports.stream().collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    FileObject parameterNamesFile = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                            packageElement.getQualifiedName(),
                            clazzElem.getSimpleName() + ".exports",
                            element);

                    try (Writer writer = parameterNamesFile.openWriter()) {
                        writer.append(exports.stream().collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

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

                pluginClasses.add(clazzElem.getQualifiedName().toString());


            }
            pluginsDescriptorFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(pluginsDescriptorFile), "utf-8")) {
                writer.append(pluginClasses.stream().collect(Collectors.joining("\n")));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private boolean isPluginExport(DeclaredType typeArgument) {
        return processingEnv.getTypeUtils().isSameType(
                processingEnv.getTypeUtils().erasure(typeArgument),
                processingEnv.getTypeUtils().erasure(processingEnv.getElementUtils().getTypeElement("org.kantega.reststop.api.PluginExport").asType()));
    }


    private boolean isCollection(TypeMirror t) {
        TypeMirror erasure = processingEnv.getTypeUtils().erasure(t);
        TypeMirror collectionErasure = processingEnv.getTypeUtils().erasure(processingEnv.getElementUtils().getTypeElement("java.util.Collection").asType());
        return processingEnv.getTypeUtils().isSameType(
                erasure,
                collectionErasure);
    }
}
