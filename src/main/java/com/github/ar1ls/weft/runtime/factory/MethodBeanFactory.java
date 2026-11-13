package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.InjectionPoint;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MethodBeanFactory implements BeanFactory {

    Method method;
    Class<?> ownerType;
    boolean staticMethod;

    @Getter(onMethod_ = @Override)
    @Accessors(fluent = true)
    List<DependencyDescriptor> dependencies;

    public MethodBeanFactory(@NonNull Method method, @NonNull Class<?> ownerType) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            throw new DefinitionException("Public concrete factory method required: " + method.toGenericString());
        }
        if (method.getReturnType().isPrimitive()) {
            throw new DefinitionException("Factory must return a reference type: " + method.toGenericString());
        }
        this.method = method;
        this.ownerType = ownerType;
        this.staticMethod = Modifier.isStatic(method.getModifiers());
        if (!this.staticMethod && !method.getDeclaringClass().isAssignableFrom(ownerType)) {
            throw new DefinitionException("Incompatible configuration owner for " + method.toGenericString());
        }

        var dependencies = this.createDependencies(method, ownerType);
        this.dependencies = List.copyOf(dependencies);
    }

    private @NonNull ArrayList<DependencyDescriptor> createDependencies(@NonNull Method method, @NonNull Class<?> ownerType) {
        var dependencies = new ArrayList<DependencyDescriptor>();
        if (!this.staticMethod) {
            dependencies.add(new DependencyDescriptor(TypeRef.of(ownerType), "", DependencyKind.DIRECT,
                    new InjectionPoint(method.toGenericString() + " configuration owner")));
        }
        dependencies.addAll(InjectionMetadataReader.readParameters(method, ownerType));
        return dependencies;
    }

    @Override
    public Object create(@NonNull Object[] arguments) throws ReflectiveOperationException {
        if (arguments.length != this.dependencies.size()) {
            throw new IllegalArgumentException("Expected " + this.dependencies.size()
                    + " factory arguments: " + this.method.toGenericString());
        }

        var offset = this.staticMethod ? 0 : 1;
        var owner = this.staticMethod ? null : arguments[0];
        if (!this.staticMethod && !this.ownerType.isInstance(owner)) {
            throw new IllegalArgumentException("Invalid configuration owner for " + this.method.toGenericString());
        }
        var methodArguments = Arrays.copyOfRange(arguments, offset, arguments.length);
        return this.method.invoke(owner, methodArguments);
    }

}
