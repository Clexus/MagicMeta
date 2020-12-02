package com.elmakers.mine.bukkit.meta;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.ClassUtils;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import com.elmakers.mine.bukkit.magic.SourceLocation;
import com.elmakers.mine.bukkit.wand.WandMode;
import com.google.common.base.CaseFormat;

public class ParameterStore {
    private final Map<String, ParameterType> parameterTypes = new HashMap<>();
    private Map<String, Parameter> parameters = new HashMap<>();

    public ParameterType getParameterType(@Nonnull Class<?> classType) {
        String key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, classType.getSimpleName());
        Class<?> outerClass = classType.getDeclaringClass();
        if (outerClass != null) {
            key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, outerClass.getSimpleName()) + "_" + key;
        }
        return getParameterType(key, classType);
    }

    public ParameterType getParameterType(@Nonnull String key, @Nonnull Class<?> classType) {
        ParameterType parameterType = parameterTypes.get(key);
        if (parameterType == null) {
            parameterType = new ParameterType(key, classType);
            parameterTypes.put(key, parameterType);
        }

        return parameterType;
    }

    public ParameterType getListType(@Nonnull String key, ParameterType valueType) {
        ParameterType parameterType = parameterTypes.get(key);
        if (parameterType == null) {
            parameterType = new ParameterType(key, valueType);
            parameterTypes.put(key, parameterType);
        }

        return parameterType;
    }

    public ParameterType getMapType(@Nonnull String key, ParameterType keyType, ParameterType valueType) {
        ParameterType parameterType = parameterTypes.get(key);
        if (parameterType == null) {
            parameterType = new ParameterType(key, keyType, valueType);
            parameterTypes.put(key, parameterType);
        }

        return parameterType;
    }

    public void mergeType(String typeName, ParameterList parameters) {

        ParameterType parameterType = parameterTypes.get(typeName);
        if (parameterType == null) {
            parameterType = new ParameterType(typeName);
            parameterTypes.put(typeName, parameterType);
        }

        // Temporary re-create
        parameterType.setClassType(ConfigurationSection.class);
        Map<String, String> existingParameters = parameterType.getParameters();
        if (existingParameters == null) {
            parameterType.setParameters(parameterType.getOptions());
        }
        parameterType.setOptions(null);

        Map<String, String> typeParameters = parameterType.getParameters();
        Map<String, Parameter> fields = new HashMap<>();
        for (String key : typeParameters.keySet()) {
            Parameter parameter = getParameter(key);
            if (parameter == null) {
                System.out.println("Missing parameter: " + key);
                continue;
            }
            fields.put(parameter.getField(), parameter);
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Parameter parameter = getParameter(key);
            if (parameter == null) {
                System.out.println("Missing parameter: " + key);
                continue;
            }
            String field = parameter.getField();
            if (!fields.containsKey(field)) {
                typeParameters.put(field, entry.getValue());
                System.out.println("    Adding new " + typeName + " parameter: " + key + " => " +field);
            }
        }
    }

    public Map<String, Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Parameter> parameters) {
        this.parameters.putAll(parameters);
    }

    public Map<String, ParameterType> getTypes() {
        return parameterTypes;
    }

    public void setTypes(Map<String, ParameterType> types) {
        parameterTypes.putAll(types);
    }

    public void update() {
        for (ParameterType parameterType : parameterTypes.values()) {
            parameterType.update();
        }
    }

    @Nullable
    public Parameter getParameter(String key) {
        return parameters.get(key);
    }

    public Parameter getParameter(String field, Class<?> defaultClass) {
        if (defaultClass.isPrimitive()) {
            defaultClass = ClassUtils.primitiveToWrapper(defaultClass);
        }

        // Easier to do this here then fill it in by hand
        ParameterType parameterType;
        switch (field) {
            case "force":
            case "indestructible":
            case "passive":
            case "quick_cast":
            case "quiet":
            case "upgrade":
                parameterType = getParameterType(Boolean.class);
                break;
            case "repeat":
            case "delay":
            case "warmup":
                parameterType = getParameterType(Integer.class);
                break;
            case "actions":
                parameterType = getParameterType("actions", Map.class);
                break;
            case "spells":
                parameterType = getListType("spell_list", getParameterType("spell", String.class));
                break;
            case "brushes":
                parameterType = getListType("material_list", getParameterType(Material.class));
                break;
            case "protection":
            case "weakness":
            case "strength":
                parameterType = getMapType("damage_type_map", getParameterType("damage_type", String.class), getParameterType(Double.class));
                break;
            case "alternate_spell":
            case "alternate_spell2":
            case "active_spell":
            case "cast_spell":
                parameterType = getParameterType("spell", String.class);
                break;
            case "remove_effects":
                parameterType = getListType("potion_effect_list", getParameterType(PotionEffectType.class));
                break;
            case "potion_effects":
            case "add_effects":
            case "projectile_potion_effects":
                parameterType = getMapType("potion_effect_map", getParameterType(PotionEffectType.class), getParameterType(Integer.class));
                break;
            case "entity_attributes":
            case "item_attributes":
                parameterType = getMapType("attribute_map", getParameterType(Attribute.class), getParameterType(Double.class));
                break;
            case "enchantments":
                parameterType = getMapType("enchantment_map", getParameterType(Enchantment.class), getParameterType(Integer.class));
                break;
            case "attributes":
                parameterType = getParameterType("attributes", String.class);
                break;
            case "upgrade_required_path":
            case "path":
                parameterType = getParameterType("path", String.class);
                break;
            case "brush_mode":
            case "mode":
                parameterType = getParameterType(WandMode.class);
                break;
            case "type":
                parameterType = getParameterType(EntityType.class);
                break;
            case "weather":
                parameterType = getParameterType("weather", String.class);
                break;
            case "color2":
            case "color":
                parameterType = getParameterType("color", String.class);
                break;
            case "damage_type":
                parameterType = getParameterType("damage_type", String.class);
                break;
            case "icon_url":
                parameterType = getParameterType("texture", String.class);
                break;
            case "icon":
            case "icon_inactive":
            case "icon_disabled":
                parameterType = getParameterType("icon", String.class);
                break;
            case "active_brush":
            case "material":
            case "brush":
                parameterType = getParameterType(Material.class);
                break;
            case "biome":
                parameterType = getParameterType(Biome.class);
                break;
            case "effect_particle":
            case "particle":
                parameterType = getParameterType(Particle.class);
                break;
            case "effect_sound":
            case "sound":
                parameterType = getParameterType(Sound.class);
                break;
            case "firework":
                parameterType = getParameterType(FireworkEffect.Type.class);
                break;
            case "effect":
                parameterType = getParameterType(Effect.class);
                break;
            case "source_location":
            case "target_location":
                parameterType = getParameterType(SourceLocation.LocationType.class);
                break;
            case "location_offset":
            case "offset":
            case "random_source_offset":
            case "random_target_offset":
            case "relative_offset":
            case "relative_source_offset":
            case "relative_target_offset":
            case "return_offset":
            case "return_relative_offset":
            case "source_direction_offset":
            case "source_offset":
            case "origin_offset":
            case "target_direction_offset":
            case "target_offset":
            case "velocity_offset":
                parameterType = getParameterType(Vector.class);
                break;
            default:
                parameterType = getParameterType(defaultClass);
        }

        String key = field;
        Parameter parameter = parameters.get(key);
        if (parameter != null) {
            String typeKey = parameter.getType();
            ParameterType existingType = parameterTypes.get(typeKey);

            // Allow strings to overlap
            if (existingType == null) {
                System.out.println("Looking up field key " + key + " got type " + typeKey + " that doesn't exit");
            } else if (parameterType.getClassType() != String.class && !existingType.getKey().equals(parameterType.getKey())) {
                // Allow numeric types to overap.
                if (Number.class.isAssignableFrom(parameterType.getClassType()) && Number.class.isAssignableFrom(existingType.getClassType())) {
                    return parameter;
                }

                key = field + "_" + parameterType.getKey();
                parameter = parameters.get(key);
                if (parameter != null) {
                    return parameter;
                }
            } else {
                return parameter;
            }
        }

        parameter = new Parameter(key, field, parameterType);
        parameters.put(parameter.getKey(), parameter);
        return parameter;
    }

    public void loaded() {
        for (Map.Entry<String, ParameterType> entry : parameterTypes.entrySet()) {
            entry.getValue().setKey(entry.getKey());
        }
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            entry.getValue().setKey(entry.getKey());
        }
    }

    public void addParameter(String key, Parameter parameter) {
        parameters.put(key, parameter);
    }
}
