package org.kantega.reststop.core;

import java.lang.reflect.Parameter;
import java.util.Optional;

public interface Injector<T> {

    Optional<T> create(Parameter parameter);
}
