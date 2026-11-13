package com.github.ar1ls.weft.definition;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import lombok.experimental.UtilityClass;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@UtilityClass
public class Types {

    public Class<?> raw(Type type) {
        if (type instanceof Class<?> value) {
            return value;
        }
        if (type instanceof ParameterizedType value) {
            return (Class<?>) value.getRawType();
        }
        if (type instanceof GenericArrayType value) {
            return Array.newInstance(raw(value.getGenericComponentType()), 0).getClass();
        }
        throw new DefinitionException("Unresolved type: " + type.getTypeName());
    }

    public void validate(Type type) {
        if (type instanceof Class<?> value && !value.isPrimitive()) {
            return;
        }
        if (type instanceof GenericArrayType array) {
            validate(array.getGenericComponentType());
            return;
        }
        if (type instanceof ParameterizedType value && value.getRawType() instanceof Class<?> declared
                && declared.getTypeParameters().length == value.getActualTypeArguments().length) {
            for (var argument : value.getActualTypeArguments()) {
                validate(argument);
            }
            return;
        }
        throw new DefinitionException("A resolved reference type is required: " + type.getTypeName());
    }

    public boolean matches(Type requested, Type provided) {
        var requestedClass = raw(requested);
        if (!requestedClass.isAssignableFrom(raw(provided))) {
            return false;
        }
        if (requested instanceof ParameterizedType expected) {
            var viewed = view(provided, requestedClass);
            if (!(viewed instanceof ParameterizedType actual)) {
                return false;
            }
            if (!Objects.equals(expected.getOwnerType(), actual.getOwnerType())) {
                return false;
            }
            var expectedArguments = expected.getActualTypeArguments();
            var actualArguments = actual.getActualTypeArguments();
            for (var index = 0; index < expectedArguments.length; index++) {
                if (!expectedArguments[index].equals(actualArguments[index])) {
                    return false;
                }
            }
        } else if (requested instanceof GenericArrayType expected) {
            var actual = provided instanceof GenericArrayType array ? array.getGenericComponentType() : raw(provided).getComponentType();
            return matches(expected.getGenericComponentType(), actual);
        }
        return true;
    }

    public Type member(Type context, Class<?> owner, Type member) {
        return substitute(member, bindings(view(context, owner)));
    }

    public Type view(Type type, Class<?> target) {
        var declared = raw(type);
        if (declared == target) {
            return type;
        }
        var bindings = bindings(type);
        for (var contract : declared.getGenericInterfaces()) {
            if (target.isAssignableFrom(raw(contract))) {
                return view(substitute(contract, bindings), target);
            }
        }
        var parent = declared.getGenericSuperclass();
        if (parent != null && target.isAssignableFrom(raw(parent))) {
            return view(substitute(parent, bindings), target);
        }
        return type;
    }

    private Map<TypeVariable<?>, Type> bindings(Type type) {
        var result = new HashMap<TypeVariable<?>, Type>();
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getOwnerType() != null) {
                result.putAll(bindings(parameterized.getOwnerType()));
            }
            var parameters = raw(type).getTypeParameters();
            var arguments = parameterized.getActualTypeArguments();
            for (var index = 0; index < parameters.length; index++) {
                result.put(parameters[index], arguments[index]);
            }
        }
        return result;
    }

    private Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            return bindings.getOrDefault(variable, variable);
        }
        if (type instanceof ParameterizedType parameterized) {
            var arguments = parameterized.getActualTypeArguments();
            for (var index = 0; index < arguments.length; index++) {
                arguments[index] = substitute(arguments[index], bindings);
            }
            var owner = parameterized.getOwnerType();
            return new ParameterizedTypeValue(parameterized.getRawType(), owner == null ? null : substitute(owner, bindings), arguments);
        }
        if (type instanceof GenericArrayType array) {
            var component = substitute(array.getGenericComponentType(), bindings);
            return component instanceof Class<?> declared ? Array.newInstance(declared, 0).getClass() : new GenericArrayTypeValue(component);
        }
        return type;
    }

}
