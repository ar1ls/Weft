package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@UtilityClass
public class DefaultValueConverter {

    public Object convert(String key, String value, Type type) {
        if (type instanceof Class<?> declared) {
            return convert(key, value, declared);
        }
        requireCollection(key, type);
        if (type instanceof ParameterizedType parameterized) {
            var raw = parameterized.getRawType();
            var arguments = parameterized.getActualTypeArguments();
            if (raw == List.class || raw == Set.class) {
                var values = new ArrayList<>();
                if (!value.isEmpty()) {
                    for (var part : value.split(",", -1)) {
                        values.add(convert(key, part.strip(), arguments[0]));
                    }
                }
                return raw == List.class ? List.copyOf(values) : Collections.unmodifiableSet(new LinkedHashSet<>(values));
            }
            if (raw == Map.class && arguments[0] == String.class) {
                var values = new LinkedHashMap<String, Object>();
                if (!value.isEmpty()) {
                    for (var part : value.split(",", -1)) {
                        var separator = part.indexOf('=');
                        if (separator <= 0) {
                            throw new PropertyBindingException("Expected key=value entries for property '" + key + "'");
                        }
                        var entryKey = part.substring(0, separator).strip();
                        if (entryKey.isEmpty() || values.putIfAbsent(entryKey, convert(key, part.substring(separator + 1).strip(), arguments[1])) != null) {
                            throw new PropertyBindingException("Invalid or duplicate map key for property '" + key + "'");
                        }
                    }
                }
                return Collections.unmodifiableMap(values);
            }
        }
        throw new PropertyBindingException("Unsupported property type for '" + key + "': " + type.getTypeName());
    }

    private void requireCollection(String key, Type type) {
        if (type instanceof ParameterizedType parameterized) {
            var raw = parameterized.getRawType();
            var arguments = parameterized.getActualTypeArguments();
            if ((raw == List.class || raw == Set.class) && arguments.length == 1 && arguments[0] instanceof Class<?> element) {
                requireSupported(key, element);
                return;
            }
            if (raw == Map.class && arguments.length == 2 && arguments[0] == String.class && arguments[1] instanceof Class<?> element) {
                requireSupported(key, element);
                return;
            }
        }
        throw new PropertyBindingException("Unsupported property type for '" + key + "': " + type.getTypeName());
    }

    public Object convert(@NonNull String key, @NonNull String value, @NonNull Class<?> type) {
        requireSupported(key, type);
        try {
            if (type == String.class) {
                return value;
            }
            if (type == char.class || type == Character.class) {
                if (value.length() != 1) {
                    throw new IllegalArgumentException();
                }
                return value.charAt(0);
            }
            var normalized = value.strip();
            if (type == boolean.class || type == Boolean.class) {
                if (!normalized.equalsIgnoreCase("true") && !normalized.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException();
                }
                return Boolean.parseBoolean(normalized);
            }
            if (type == byte.class || type == Byte.class) {
                return Byte.parseByte(normalized);
            }
            if (type == short.class || type == Short.class) {
                return Short.parseShort(normalized);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(normalized);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(normalized);
            }
            if (type == float.class || type == Float.class) {
                var result = Float.parseFloat(normalized);
                if (!Float.isFinite(result)) {
                    throw new IllegalArgumentException();
                }
                return result;
            }
            if (type == double.class || type == Double.class) {
                var result = Double.parseDouble(normalized);
                if (!Double.isFinite(result)) {
                    throw new IllegalArgumentException();
                }
                return result;
            }
            if (type.isEnum()) {
                for (var constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(normalized)) {
                        return constant;
                    }
                }
                throw new IllegalArgumentException();
            }
            if (type == Duration.class) {
                return Duration.parse(normalized);
            }
            return Path.of(value);
        } catch (RuntimeException e) {
            throw new PropertyBindingException("Invalid property '" + key + "'; expected " + type.getTypeName());
        }
    }

    public void requireSupported(String key, Class<?> type) {
        if (type != String.class && type != char.class && type != Character.class
                && type != boolean.class && type != Boolean.class && type != byte.class && type != Byte.class
                && type != short.class && type != Short.class && type != int.class && type != Integer.class
                && type != long.class && type != Long.class && type != float.class && type != Float.class
                && type != double.class && type != Double.class && !type.isEnum()
                && type != Duration.class && type != Path.class) {
            throw new PropertyBindingException("Unsupported property type for '" + key + "': " + type.getTypeName());
        }
    }

}
