package com.elmakers.mine.bukkit.meta;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.reflections.Reflections;

import com.elmakers.mine.bukkit.action.BaseSpellAction;
import com.elmakers.mine.bukkit.action.CastContext;
import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.action.builtin.SelectorAction;
import com.elmakers.mine.bukkit.api.action.SpellAction;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.effect.EffectPlayer;
import com.elmakers.mine.bukkit.effect.builtin.EffectSingle;
import com.elmakers.mine.bukkit.entity.EntityData;
import com.elmakers.mine.bukkit.magic.Mage;
import com.elmakers.mine.bukkit.magic.MageClass;
import com.elmakers.mine.bukkit.magic.MageModifier;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.magic.MagicRecipe;
import com.elmakers.mine.bukkit.spell.ActionSpell;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.spell.BlockSpell;
import com.elmakers.mine.bukkit.spell.BrushSpell;
import com.elmakers.mine.bukkit.spell.TargetingSpell;
import com.elmakers.mine.bukkit.spell.UndoableSpell;
import com.elmakers.mine.bukkit.wand.WandProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.CaseFormat;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;

public class MagicMeta {
    private static final String BUILTIN_SPELL_PACKAGE = "com.elmakers.mine.bukkit.action.builtin";
    private static final String EFFECTLIB_PACKAGE = "de.slikey.effectlib.effect";

    private final SortedObjectMapper mapper = new SortedObjectMapper();

    private final MagicController controller;
    private final Mage mage;

    private MetaData data;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: MagicMeta <meta.json>");
            return;
        }

        String fileName = args[0];
        boolean regenerate = (args.length > 1 && args[1].equals("--regenerate"));
        MagicMeta meta = new MagicMeta();
        try {
            File metaFile = new File(fileName);
            if (!regenerate) {
                System.out.println("Loading " + metaFile.getAbsolutePath());
                meta.loadMeta(metaFile);
            } else {
                System.out.println("Regenerating");
            }
            meta.generateMeta();
            System.out.println("Saving to " + metaFile.getAbsolutePath());
            meta.saveMeta(metaFile);
        } catch (Exception ex) {
            System.out.println("An error ocurred generating metadata " + ex.getMessage());
            ex.printStackTrace();
        }
        System.out.println("Done.");
    }

    private MagicMeta() {
        controller = new MagicController();
        mage = new Mage("Interrogator", controller);
    }

    private void loadMeta(@Nonnull File inputFile) throws IOException {
        if (inputFile.exists()) {
            JsonNode root = mapper.readTree(inputFile);
            data = mapper.convertValue(root, MetaData.class);
            data.loaded();
        }
    }

    private void saveMeta(@Nonnull File outputFile) throws IOException {
        data.update();
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, data);
    }

    private void addSpellParameters(MagicController controller, Mage mage, BaseSpell spell, ParameterList parameters, ParameterList properties, String categoryKey) {
        Category category = getCategory(categoryKey);
        InterrogatingConfiguration templateConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        ParameterStore parameterStore = data.getParameterStore();

        spell.initialize(controller);
        spell.setMage(mage);

        // Gather base properties
        spell.loadTemplate("interrogator", templateConfiguration);
        ParameterList spellProperties = templateConfiguration.getParameters();
        spellProperties.setCategory(category.getKey(), parameterStore);
        properties.merge(spellProperties, parameterStore);

        // Gather parameters
        InterrogatingConfiguration spellConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        spell.processParameters(spellConfiguration);
        ParameterList spellParameters = spellConfiguration.getParameters();
        spellParameters.setCategory(category.getKey(), parameterStore);
        parameters.merge(spellParameters, parameterStore);
    }

    private void generateSpellMeta() {
        ParameterList parameters = new ParameterList();
        ParameterList properties = new ParameterList();

        // Check for base spell parameters
        // Do this one class at a time for categorization purposes
        addSpellParameters(controller, mage, new ActionSpell(), parameters, properties, "actions");
        addSpellParameters(controller, mage, new BrushSpell(), parameters, properties, "brushes");
        addSpellParameters(controller, mage, new BlockSpell(), parameters, properties, "construction");
        addSpellParameters(controller, mage, new UndoableSpell(), parameters, properties, "undo");
        addSpellParameters(controller, mage, new TargetingSpell(), parameters, properties, "targeting");
        addSpellParameters(controller, mage, new BaseSpell(), parameters, properties, "base");

        // Gather base spell properties loaded from loadTemplate
        data.addSpellProperties(properties);
        data.addSpellParameters(parameters);
    }

    private void generateActionMeta() {
        // Note that this seems to get everything outside of this package as well. Not sure why.
        Reflections reflections = new Reflections(BUILTIN_SPELL_PACKAGE);

        Set<Class<? extends SpellAction>> classSet = reflections.getSubTypesOf(SpellAction.class);
        List<Class<? extends SpellAction>> allClasses = new ArrayList<>(classSet);
        Collections.sort(allClasses, new ClassComparator());

        InterrogatingConfiguration templateConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        ActionSpell spell = new ActionSpell();
        spell.initialize(controller);
        spell.setMage(mage);
        spell.loadTemplate("interrogator", templateConfiguration);

        CastContext context = new CastContext(spell);

        // First get base action parameters

        BaseSpellAction baseAction = new BaseSpellAction() {
            @Override
            public SpellResult perform(com.elmakers.mine.bukkit.api.action.CastContext context) {
                return SpellResult.NO_ACTION;
            }
        };
        InterrogatingConfiguration baseConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        baseAction.initialize(spell, baseConfiguration);
        baseAction.prepare(context, baseConfiguration);
        ParameterList baseParameters = baseConfiguration.getParameters();
        data.addActionParameters(baseParameters);

        SpellAction compoundAction = new CompoundAction() {};
        InterrogatingConfiguration compoundConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        compoundAction.initialize(spell, compoundConfiguration);
        compoundAction.prepare(context, compoundConfiguration);
        ParameterList compoundParameters = compoundConfiguration.getParameters();
        data.addCompoundActionParameters(compoundParameters);

        for (Class<? extends SpellAction> actionClass : allClasses) {
            if (!actionClass.getPackage().getName().equals(BUILTIN_SPELL_PACKAGE)
                || actionClass.getAnnotation(Deprecated.class) != null
                || Modifier.isAbstract(actionClass.getModifiers())) {
                System.out.println("Skipping " + actionClass.getName());
                continue;
            }
            System.out.println("Scanning " + actionClass.getName());
            ParameterStore parameterStore = data.getParameterStore();
            try {
                SpellAction testAction = actionClass.getConstructor().newInstance();
                InterrogatingConfiguration actionConfiguration = new InterrogatingConfiguration(parameterStore);
                testAction.initialize(spell, actionConfiguration);
                testAction.prepare(context, actionConfiguration);

                ParameterList spellParameters = actionConfiguration.getParameters();
                spellParameters.removeDefaults(baseParameters);
                SpellActionDescription spellAction = new SpellActionDescription(actionClass, spellParameters);
                if (testAction instanceof CompoundAction) {
                    spellAction.setCategory(getCategory("compound").getKey());
                    CompoundAction testCompound = (CompoundAction)testAction;
                    for (String handler : testCompound.getAllHandlerKeys()) {
                        // Actions is handled as part of Compound inheritance
                        if (handler.equals("actions")) continue;
                        String handlerKey = handler + "_actions";

                        // This is kind of hacky and will probably bite me later
                        Parameter parameter = parameterStore.getParameter(handler);
                        if (parameter != null && parameter.getType().equals("string")) {
                            parameterStore.removeParameter(handler);
                        }
                        Parameter handlerParameter = parameterStore.getParameter(handlerKey);
                        if (handlerParameter == null) {
                            System.out.println("  Adding action handler: " + handler);
                            ParameterType type = parameterStore.getParameterType("action_list", java.util.List.class);
                            handlerParameter = new Parameter(handlerKey, handler, type);
                            parameterStore.addParameter(handlerKey, handlerParameter);
                        }
                        spellParameters.remove(handler);
                        spellParameters.add(handlerParameter, null);
                    }
                    spellParameters.removeDefaults(compoundParameters);
                }
                data.addAction(spellAction.getKey(), spellAction);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void generateEffectsMeta() {
        System.out.println("Scanning EffectSingle");
        InterrogatingConfiguration effectConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        EffectPlayer player = new EffectSingle();
        player.load(null, effectConfiguration);
        ParameterList singleParameters = effectConfiguration.getParameters();
        data.addEffectParameters(singleParameters);
    }

    private ParameterList collectEffectProperties(Class<? extends Effect> classType, EffectManager manager, Effect instance) {
        if (instance == null) {
            try {
                instance = classType.getConstructor(EffectManager.class).newInstance(manager);
            } catch (Exception ex) {
                System.err.println("Error instantiating " + classType.getName());
                ex.printStackTrace();
            }
        }
        ParameterList properties = new ParameterList();
        Field[] fields = classType.getFields();
        for (Field field : fields) {
            if (field.getType() == Player.class || field.getType() == Runnable.class) continue;
            String key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName());
            Parameter parameter = data.getParameter(key, field.getType());
            Object defaultValue = null;
            if (instance != null) {
                try {
                    defaultValue = field.get(instance);
                } catch (IllegalAccessException e) {
                    System.err.println("Error reading " + field.getName() + " of " + classType.getName());
                    e.printStackTrace();
                }
            }
            properties.add(parameter, defaultValue);
        }
        return properties;
    }

    private void generateEffectLibMeta() {
        // Create a dummy effect manager
        EffectManager manager = new EffectManager(new DummyPlugin());
        // First get all base effect parameters
        System.out.println("Scanning Effect");
        Effect baseEffect = new Effect(manager) {
            @Override
            public void onRun() {

            }
        };
        ParameterList baseEffectParameters = collectEffectProperties(Effect.class, manager, baseEffect);
        data.addEffectLibParameters(baseEffectParameters);

        // Gather all effect classes
        Reflections reflections = new Reflections(EFFECTLIB_PACKAGE);

        Set<Class<? extends Effect>> effectsSet = reflections.getSubTypesOf(Effect.class);
        List<Class<? extends Effect>> allEffects = new ArrayList<>(effectsSet);
        Collections.sort(allEffects, new ClassComparator());

        for (Class<? extends Effect> effectClass : allEffects) {
            if (effectClass.getAnnotation(Deprecated.class) != null
                || Modifier.isAbstract(effectClass.getModifiers())) {
                System.out.println("Skipping " + effectClass.getName());
                continue;
            }
            System.out.println("Scanning " + effectClass.getName());
            try {
                ParameterList effectParameters = collectEffectProperties(effectClass, manager, null);

                // Filter out common parameters
                effectParameters.removeDefaults(baseEffectParameters);
                EffectDescription effect = new EffectDescription(effectClass, effectParameters);
                data.addEffect(effect.getKey(), effect);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void generateWandMeta() {
        System.out.println("Adding wand properties");

        // Turns out there's no real way to scan the Wand class using InterrogatingConfiguration because it doesn't
        // load typed data directly from its configuration.
        // So we will have to fix up all the types by hand, unfortunately.
        // I think the most common case is doubles, so that's what we'll default to.
        ParameterList wandParameters = new ParameterList();
        for (String property : WandProperties.PROPERTY_KEYS) {
            Parameter parameter = data.getParameter(property, String.class);
            wandParameters.add(parameter, null);
        }
        data.addWandParameters(wandParameters);
    }

    private void generateClassMeta() {
        System.out.println("Adding class properties");

        // Turns out there's no real way to scan the Wand class using InterrogatingConfiguration because it doesn't
        // load typed data directly from its configuration.
        // So we will have to fix up all the types by hand, unfortunately.
        // I think the most common case is doubles, so that's what we'll default to.
        ParameterList classParameters = new ParameterList();
        for (String property : MageClass.PROPERTY_KEYS) {
            Parameter parameter = data.getParameter(property, String.class);
            classParameters.add(parameter, null);
        }
        data.addClassParameters(classParameters);
    }

    private void generateModifierMeta() {
        System.out.println("Adding modifier properties");

        // Turns out there's no real way to scan the Wand class using InterrogatingConfiguration because it doesn't
        // load typed data directly from its configuration.
        // So we will have to fix up all the types by hand, unfortunately.
        // I think the most common case is doubles, so that's what we'll default to.
        ParameterList modifierParameters = new ParameterList();
        for (String property : MageModifier.PROPERTY_KEYS) {
            Parameter parameter = data.getParameter(property, String.class);
            modifierParameters.add(parameter, null);
        }
        data.addModifierParameters(modifierParameters);
    }

    private void generateMobMeta() {
        System.out.println("Scanning EntityData");
        InterrogatingConfiguration mobConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        new EntityData(controller, "interrogator", mobConfiguration);
        ParameterList mobParameters = mobConfiguration.getParameters();
        data.addMobParameters(mobParameters);
    }

    private void generateSelectorOptionMeta() {
        System.out.println("Scanning Selector");
        InterrogatingConfiguration selectorConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        SelectorAction selector = new SelectorAction();
        ActionSpell spell = new ActionSpell();
        spell.initialize(controller);
        spell.setMage(mage);
        CastContext context = new CastContext(spell);
        selector.prepare(context, new MemoryConfiguration());
        selector.getSelectorOption(selectorConfiguration);
        ParameterList optionParameters = selectorConfiguration.getParameters();
        data.addOptionParameters(optionParameters);
    }

    private void generateRecipeMeta() {
        System.out.println("Scanning MagicRecipe");
        InterrogatingConfiguration recipeConfiguration = new InterrogatingConfiguration(data.getParameterStore());
        MagicRecipe recipe = new MagicRecipe("recipe", controller);
        recipe.load(recipeConfiguration);
        ParameterList recipeParameters = recipeConfiguration.getParameters();
        data.addRecipeParameters(recipeParameters);
    }

    private void generateMeta() {
        if (data == null) {
            data = new MetaData();
        }
        generateSpellMeta();
        generateActionMeta();
        generateEffectsMeta();
        generateEffectLibMeta();
        generateWandMeta();
        generateClassMeta();
        generateModifierMeta();
        generateMobMeta();
        generateSelectorOptionMeta();
        generateRecipeMeta();
    }

    private Category getCategory(String key) {
        return data.getCategory(key);
    }
}
