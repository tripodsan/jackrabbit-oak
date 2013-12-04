/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.util;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nonnull;
import javax.jcr.PropertyType;

import com.google.common.collect.Lists;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.BinaryPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.BooleanPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.DecimalPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.DoublePropertyState;
import org.apache.jackrabbit.oak.plugins.memory.EmptyPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.GenericPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.LongPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiBinaryPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiBooleanPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiDecimalPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiDoublePropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiGenericPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiLongPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MultiStringPropertyState;
import org.apache.jackrabbit.oak.plugins.memory.StringPropertyState;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * {@code PropertyBuilder} for building in memory {@code PropertyState} instances.
 * @param <T>
 */
public class PropertyBuilder<T> {
    private final Type<T> type;

    private String name;
    private boolean isArray;
    private List<T> values = Lists.newArrayList();

    /**
     * Create a new instance for building {@code PropertyState} instances
     * of the given {@code type}.
     * @param type  type of the {@code PropertyState} instances to be built.
     * @throws IllegalArgumentException if {@code type.isArray()} is {@code true}.
     */
    public PropertyBuilder(Type<T> type) {
        checkArgument(!type.isArray(), "type must not be array");
        this.type = type;
    }

    /**
     * Create a new instance for building scalar {@code PropertyState} instances
     * of the given {@code type}.
     * @param type  type of the {@code PropertyState} instances to be built.
     * @return {@code PropertyBuilder} for {@code type}
     */
    public static <T> PropertyBuilder scalar(Type<T> type) {
        return new PropertyBuilder<T>(type);
    }

    /**
     * Create a new instance for building array {@code PropertyState} instances
     * of the given {@code type}.
     * @param type  type of the {@code PropertyState} instances to be built.
     * @return {@code PropertyBuilder} for {@code type}
     */
    public static <T> PropertyBuilder array(Type<T> type) {
        return new PropertyBuilder<T>(type).setArray();
    }

    /**
     * Create a new instance for building scalar {@code PropertyState} instances
     * of the given {@code type}. The builder is initialised with the
     * given {@code name}.
     * Equivalent to
     * <pre>
     *     MemoryPropertyBuilder.create(type).setName(name);
     * </pre>
     * @param type  type of the {@code PropertyState} instances to be built.
     * @param name  initial name
     * @return {@code PropertyBuilder} for {@code type}
     */
    public static <T> PropertyBuilder scalar(Type<T> type, String name) {
        return scalar(type).setName(name);
    }

    /**
     * Create a new instance for building array {@code PropertyState} instances
     * of the given {@code type}. The builder is initialised with the
     * given {@code name}.
     * Equivalent to
     * <pre>
     *     MemoryPropertyBuilder.create(type).setName(name).setArray();
     * </pre>
     * @param type  type of the {@code PropertyState} instances to be built.
     * @param name  initial name
     * @return {@code PropertyBuilder} for {@code type}
     */
    public static <T> PropertyBuilder array(Type<T> type, String name) {
        return scalar(type).setName(name).setArray();
    }

    /**
     * Create a new instance for building {@code PropertyState} instances
     * of the given {@code type}. The builder is initialised with the name and
     * the values of {@code property}.
     * Equivalent to
     * <pre>
     *     PropertyBuilder.scalar(type).assignFrom(property);
     * </pre>
     *
     * @param type  type of the {@code PropertyState} instances to be built.
     * @param property  initial name and values
     * @return {@code PropertyBuilder} for {@code type}
     */
    public static <T> PropertyBuilder copy(Type<T> type, PropertyState property) {
        return scalar(type).assignFrom(property);
    }

    /**
     * Create a new {@code PropertyBuilder} from the specified parameters. Depending
     * on the {@code isArray} flag this corresponds to
     * <pre>{@link #array(org.apache.jackrabbit.oak.api.Type, String)}</pre> or
     * <pre>{@link #scalar(org.apache.jackrabbit.oak.api.Type, String)}</pre>.
     *
     *
     *
     * @param type type of the {@code PropertyState} instances to be built.
     * @param name the name of the state to be built.
     * @param isArray
     * @return {@code PropertyBuilder} for {@code type}.
     */
    public static <T> PropertyBuilder create(@Nonnull Type<T> type, @Nonnull String name, boolean isArray) {
        if (isArray) {
            return array(type, name);
        } else {
            return scalar(type, name);
        }
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return values.isEmpty() ? null : values.get(0);
    }

    @Nonnull
    public List<T> getValues() {
        return Lists.newArrayList(values);
    }

    public T getValue(int index) {
        return values.get(index);
    }

    public boolean hasValue(Object value) {
        return values.contains(value);
    }

    public int count() {
        return values.size();
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public PropertyState getPropertyState() {
        checkState(name != null, "Property has no name");
        checkState(isArray() || values.size() == 1, "Property has multiple values");

        if (values.isEmpty()) {
            return EmptyPropertyState.emptyProperty(name, Type.fromTag(type.tag(), true));
        } else if (isArray()) {
            switch (type.tag()) {
                case PropertyType.STRING:
                    return MultiStringPropertyState.stringProperty(name, (Iterable<String>) values);
                case PropertyType.BINARY:
                    return MultiBinaryPropertyState.binaryPropertyFromBlob(name, (Iterable<Blob>) values);
                case PropertyType.LONG:
                    return MultiLongPropertyState.createLongProperty(name, (Iterable<Long>) values);
                case PropertyType.DOUBLE:
                    return MultiDoublePropertyState.doubleProperty(name, (Iterable<Double>) values);
                case PropertyType.BOOLEAN:
                    return MultiBooleanPropertyState.booleanProperty(name, (Iterable<Boolean>) values);
                case PropertyType.DECIMAL:
                    return MultiDecimalPropertyState.decimalProperty(name, (Iterable<BigDecimal>) values);
                default:
                    return new MultiGenericPropertyState(name, (Iterable<String>) values, Type.fromTag(type.tag(), true));
            }
        } else {
            T value = values.get(0);
            switch (type.tag()) {
                case PropertyType.STRING:
                    return StringPropertyState.stringProperty(name, (String) value);
                case PropertyType.BINARY:
                    return BinaryPropertyState.binaryProperty(name, (Blob) value);
                case PropertyType.LONG:
                    return LongPropertyState.createLongProperty(name, (Long) value);
                case PropertyType.DOUBLE:
                    return DoublePropertyState.doubleProperty(name, (Double) value);
                case PropertyType.BOOLEAN:
                    return BooleanPropertyState.booleanProperty(name, (Boolean) value);
                case PropertyType.DECIMAL:
                    return DecimalPropertyState.decimalProperty(name, (BigDecimal) value);
                default:
                    return new GenericPropertyState(name, (String) value, type);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public PropertyBuilder assignFrom(PropertyState property) {
        if (property != null) {
            setName(property.getName());
            if (property.isArray()) {
                isArray = true;
                setValues((Iterable<T>) property.getValue(type.getArrayType()));
            } else {
                isArray = false;
                setValue(property.getValue(type));
            }
        }
        return this;
    }

    @Nonnull
    public PropertyBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @Nonnull
    public PropertyBuilder setArray() {
        isArray = true;
        return this;
    }

    @Nonnull
    public PropertyBuilder setScalar() {
        isArray = false;
        return this;
    }

    @Nonnull
    public PropertyBuilder setValue(T value) {
        values.clear();
        values.add(value);
        return this;
    }

    @Nonnull
    public PropertyBuilder addValue(T value) {
        values.add(value);
        return this;
    }

    @Nonnull
    public PropertyBuilder setValue(T value, int index) {
        values.set(index, value);
        return this;
    }

    @Nonnull
    public PropertyBuilder setValues(Iterable<T> values) {
        this.values = Lists.newArrayList(values);
        return this;
    }

    @Nonnull
    public PropertyBuilder removeValue(int index) {
        values.remove(index);
        return this;
    }

    @Nonnull
    public PropertyBuilder removeValue(Object value) {
        values.remove(value);
        return this;
    }
}
