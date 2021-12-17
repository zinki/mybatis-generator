package com.z.boot.utils;

import cn.hutool.json.JSONUtil;
import javafx.util.Pair;
import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * toString格式反序列化类
 * @author kevin
 */
public class ToStringUtils {

    /**
     * toString格式反序列化
     */
    public static <T> T toObject(Class<T> clazz, String toString) throws Exception {
        if (Objects.isNull(clazz) || Objects.isNull(toString) || StringUtils.isEmpty(toString)) {
            return clazz == String.class ? (T) toString : null;
        } else if (TypeValueUtils.isBasicType(clazz)) {
            return (T) TypeValueUtils.basicTypeValue(clazz, toString.trim());
        }

        toString = TokenUtils.cleanClassPrefix(clazz, toString.trim());
        toString = StringUtils.removeStart(toString, "(").trim();
        toString = StringUtils.removeEnd(toString, ")").trim();
        toString = StringUtils.removeStart(toString, "{").trim();
        toString = StringUtils.removeEnd(toString, "}").trim();

        String token;
        T result = clazz.newInstance();
        while (StringUtils.isNotEmpty(toString) && StringUtils.isNotEmpty(token = TokenUtils.splitToken(toString))) {
            toString = StringUtils.removeStart(StringUtils.removeStart(toString, token).trim(), ",").trim();

            // 解析k/v格式的属性名/值
            Pair<String, String> keyValue = TokenUtils.parseToken(token);
            Field field = FieldUtils.getField(clazz, keyValue.getKey(), true);
            Object value = TypeValueUtils.buildTypeValue(field, keyValue.getValue());
            FieldUtils.writeField(field, result, value, true);
        }
        return result;
    }

    /**
     * 字符串解析类
     */
    static class TokenUtils {

        /**
         * 清除类名前缀字符串
         */
        static String cleanClassPrefix(Class clazz, String toString) {
            String simpleName = clazz.getSimpleName();
            if (clazz.getName().contains("$")) {
                // 内部类需要按照内部类名字格式
                String rowSimpleName = StringUtils.substringAfterLast(clazz.getName(), ".");
                simpleName = StringUtils.replace(rowSimpleName, "$", ".");
            }
            return toString.startsWith(simpleName) ?
                    StringUtils.removeStart(toString, simpleName).trim() : toString;
        }

        /**
         * 获取第一个token，注意: toString不再包括最外层的()
         */
        private final static Map<Character, Character> tokenMap = new HashMap<>();
        static {
            tokenMap.put(')', '(');
            tokenMap.put('}', '{');
            tokenMap.put(']', '[');
        }

        static String splitToken(String toString) {
            if (StringUtils.isBlank(toString)) {
                return toString;
            }

            int bracketNum = 0;
            Stack<Character> stack = new Stack<>();
            for (int i = 0; i < toString.length(); i++) {
                Character c = toString.charAt(i);
                if (tokenMap.containsValue(c)) {
                    stack.push(c);
                } else if (tokenMap.containsKey(c) && Objects.equals(stack.peek(), tokenMap.get(c))) {
                    stack.pop();
                } else if ((c == ',') && stack.isEmpty()) {
                    return toString.substring(0, i);
                }
            }
            if (stack.isEmpty()) {
                return toString;
            }
            throw new RuntimeException("splitFirstToken error, bracketNum=" + bracketNum + ", toString=" + toString);
        }

        /**
         * 从token解析出字段名，及对应值
         */
        static Pair<String, String> parseToken(String token) {
            assert Objects.nonNull(token) && token.contains("=");
            int pos = token.indexOf("=");
            return new javafx.util.Pair<>(token.substring(0, pos), token.substring(pos + 1));
        }
    }

    /**
     * 对象构建类
     */
    static class TypeValueUtils {

        static Set<Class> BASIC_TYPE = Stream.of(
                char.class, Character.class,
                boolean.class, Boolean.class,
                short.class, Short.class,
                int.class, Integer.class,
                float.class, Float.class,
                double.class, Double.class,
                long.class, Long.class,
                String.class).collect(Collectors.toSet());

        /**
         * Filed类型是否为基础类型
         */
        static boolean isBasicType(Class clazz) {
            return BASIC_TYPE.contains(clazz);
        }

        static Object buildTypeValue(Field field, String value) throws Exception {
            if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value)) {
                return field.getType() == String.class ? value : null;
            }

            Class clazz = field.getType();
            if (isBasicType(clazz)) {
                return basicTypeValue(field.getGenericType(), value);
            } else if (field.getGenericType() == Date.class) {
                return new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", new Locale("us")).parse(value);
            } else if (clazz.isArray() || clazz.isAssignableFrom(Array.class)) {
                return arrayTypeValue(field.getType().getComponentType(), value);
            } else if (clazz.isAssignableFrom(List.class)) {
                return listTypeValue(field, value);
            } else if (clazz.isAssignableFrom(Map.class)) {
                return mapTypeValue(field, value);
            } else {
                return toObject(clazz, value);
            }
        }

        static Object basicTypeValue(Type type, String value) {
            if (type == Character.class || type == char.class) {
                return value.charAt(0);
            } else if (type == Boolean.class || type == boolean.class) {
                return Boolean.valueOf(value);
            } else if (type == Short.class || type == short.class) {
                return Short.valueOf(value);
            } else if (type == Integer.class || type == int.class) {
                return Integer.valueOf(value);
            } else if (type == Float.class || type == float.class) {
                return Float.valueOf(value);
            } else if (type == Double.class || type == double.class) {
                return Double.valueOf(value);
            } else if (type == Long.class || type == long.class) {
                return Long.valueOf(value);
            } else if (type == String.class) {
                return value;
            }
            throw new RuntimeException("basicTypeValue error, type=" + type + ", value=" + value);
        }

        static Object listTypeValue(Field field, String fieldValue) throws Exception {
            fieldValue = StringUtils.removeStart(fieldValue, "[").trim();
            fieldValue = StringUtils.removeEnd(fieldValue, "]").trim();

            String token;
            List<Object> result = new ArrayList<>();
            while (StringUtils.isNotEmpty(fieldValue) && StringUtils.isNotEmpty(token = TokenUtils.splitToken(fieldValue))) {
                fieldValue = StringUtils.removeStart(StringUtils.removeStart(fieldValue, token).trim(), ",").trim();
                result.add(toObject((Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0], token));
            }
            return result;
        }

        static <T> T[] arrayTypeValue(Class<?> componentType, String fieldValue) throws Exception {
            fieldValue = StringUtils.removeStart(fieldValue, "[").trim();
            fieldValue = StringUtils.removeEnd(fieldValue, "]").trim();

            String token;
            T[] result = newArray(componentType, fieldValue);
            for (int i = 0; StringUtils.isNotEmpty(fieldValue) && StringUtils.isNotEmpty(token = TokenUtils.splitToken(fieldValue)); i++) {
                fieldValue = StringUtils.removeStart(StringUtils.removeStart(fieldValue, token).trim(), ",").trim();
                result[i] = (T) toObject(componentType, token);
            }
            return result;
        }

        private static <T> T[] newArray(Class<?> componentType, String fieldValue) {
            String token;
            int lengh = 0;
            while (StringUtils.isNotEmpty(fieldValue) && StringUtils.isNotEmpty(token = TokenUtils.splitToken(fieldValue))) {
                fieldValue = StringUtils.removeStart(StringUtils.removeStart(fieldValue, token).trim(), ",").trim();
                lengh++;
            }

            return (T[]) Array.newInstance(componentType, lengh);
        }

        static Map mapTypeValue(Field field, String toString) throws Exception {
            toString = StringUtils.removeStart(toString, "{").trim();
            toString = StringUtils.removeEnd(toString, "}").trim();

            String token;
            Map result = new HashMap(16);
            while (StringUtils.isNotEmpty(token = TokenUtils.splitToken(toString))) {
                toString = StringUtils.removeStart(StringUtils.removeStart(toString, token).trim(), ",").trim();
                assert token.contains("=");
                String fieldName = StringUtils.substringBefore(token, "=").trim();
                String fieldValue = StringUtils.substringAfter(token, "=").trim();

                result.put(basicTypeValue(((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0], fieldName),
                        toObject((Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1], fieldValue));

            }
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        Object request = ToStringUtils.toObject(Object.class,"");
        System.out.println(JSONUtil.toJsonStr(request));
    }
}