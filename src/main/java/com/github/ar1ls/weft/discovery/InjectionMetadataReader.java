package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.InjectionPoint;
import com.github.ar1ls.weft.definition.TypeRef;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Executable;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class InjectionMetadataReader {

    public List<DependencyDescriptor> readParameters(@NonNull Executable executable) {
        var dependencies = new ArrayList<DependencyDescriptor>();
        var parameters = executable.getParameters();
        for (var index = 0; index < parameters.length; index++) {
            var parameter = parameters[index];
            var point = new InjectionPoint(executable.toGenericString() + " parameter " + index);
            var type = parameter.getParameterizedType();
            var kind = DependencyKind.DIRECT;
            if (parameter.getType() == Provider.class) {
                if (!(type instanceof ParameterizedType providerType)
                        || !(providerType.getActualTypeArguments()[0] instanceof Class<?>)) {
                    throw new DefinitionException("Provider requires a concrete class argument at " + point.description());
                }
                type = providerType.getActualTypeArguments()[0];
                kind = DependencyKind.PROVIDER;
            }
            var named = parameter.getAnnotation(Named.class);
            var name = named == null ? "" : named.value();
            dependencies.add(new DependencyDescriptor(TypeRef.of(type), name, kind, point));
        }
        return List.copyOf(dependencies);
    }

}
