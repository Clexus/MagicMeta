package com.elmakers.mine.bukkit.meta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import org.apache.commons.lang.WordUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ParameterType {
    private Class<?> classType;
    private String key;
    private String name;
    private List<String> description;
    private Set<String> options = new HashSet<>();
    private String valueType;
    private String keyType;

    public ParameterType() {

    }

    public ParameterType(@Nonnull String key, @Nonnull Class<?> classType) {
        this.key = key;
        this.classType = classType;
        description = new ArrayList<>();
        description.add("");
        name = WordUtils.capitalizeFully(key, new char[]{'_'}).replaceAll("_", " ");
    }

    public ParameterType(@Nonnull String key, ParameterType listValueType) {
        this(key, List.class);
        valueType = listValueType.getKey();
    }

    public ParameterType(@Nonnull String key, ParameterType mapKeyType, ParameterType mapValueType) {
        this(key,  Map.class);
        keyType = mapKeyType.getKey();
        valueType = mapValueType.getKey();
    }

    @JsonIgnore
    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty("class_name")
    public String getClassName() {
        return this.classType.getName();
    }

    public void setClassName(String className) {
        try {
            classType = Class.forName(className);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void update() {
        if (classType.isEnum()) {
            Object[] enums = classType.getEnumConstants();
            for (Object enumConstant : enums) {
                options.add(enumConstant.toString().toLowerCase());
            }
        } else {
            // This covers PotionEffectType, which as it turns out is a huge pain.
            Field[] values = classType.getFields();
            for (Field field : values) {
                if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == classType) {
                    options.add(field.getName().toLowerCase());
                }
            }
        }
    }

    public List<String> getOptions() {
        List<String> optionsList = new ArrayList<>(options);
        Collections.sort(optionsList);
        return optionsList;
    }

    public void setOptions(Set<String> options) {
        this.options = options;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValueType() {
        return valueType;
    }

    @JsonProperty("value_type")
    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getKeyType() {
        return keyType;
    }

    @JsonProperty("key_type")
    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    @JsonIgnore
    public Class<?> getClassType() {
        return classType;
    }
}
