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

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
@SupportedAnnotationTypes("org.kantega.reststop.api.Config")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ConfigFieldProcessor extends AbstractProcessor {


    private Set<TypeMirror> boxes = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        for(TypeKind kind : TypeKind.values()) {
            if(kind.isPrimitive()) {
                PrimitiveType primitiveType = processingEnv.getTypeUtils().getPrimitiveType(kind);
                boxes.add(processingEnv.getTypeUtils().boxedClass(primitiveType).asType());
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for(Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                TypeMirror type = element.asType();
                if(! isPrimitive(type) && ! isString(type) ) {
                    processingEnv.getMessager()
                            .printMessage(Diagnostic.Kind.ERROR,
                                    "@Config annotated field must be a primitive, a boxed primitive or a java.lang.String"
                                    , element);
                }
            }
        }
        return false;
    }

    private boolean isString(TypeMirror type) {
        return processingEnv.getTypeUtils().isSameType(type, processingEnv.getElementUtils().getTypeElement("java.lang.String").asType());
    }

    private boolean isPrimitive(TypeMirror type) {
        if(type.getKind().isPrimitive()) {
            return true;
        }
        for (TypeMirror box : boxes) {
            if(processingEnv.getTypeUtils().isSameType(type, box)) {
                return true;
            }
        }
        return false;
    }
}
