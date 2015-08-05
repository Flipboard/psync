package com.flipboard.psync;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;

/**
 * Separate class in Java for generating the code because Groovy can't talk to Java vararg methods
 */
public final class PClassGenerator {

    private static final ClassName CN_RX_PREFERENCES = ClassName.get("com.f2prateek.rx.preferences", "RxSharedPreferences");
    private static final ClassName CN_RX_PREFERENCE = ClassName.get("com.f2prateek.rx.preferences", "Preference");
    private static final Modifier[] MODIFIERS = {Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL};
    private static final Pattern COULD_BE_CAMEL = Pattern.compile("[a-zA-Z]+[a-zA-Z0-9]*");
    private static final Pattern ALL_CAPS = Pattern.compile("[A-Z0-9]*");

    /**
     * Groovy can't talk to Java vararg methods, such as JavaPoet's many vararg methods. Utility
     * class is here so we can use JavaPoet nicely.
     *
     * @param inputKeys List of the preference keys to generate for
     * @param packageName Package name to create the P class in
     * @param outputDir Output directory to create the P.java file in
     * @param className Name to use for the generated class
     * @param generateRx Boolean indicating whether or not to generate Rx-Preferences support code
     * @throws IOException because Java
     */
    public static void generate(List<PrefEntry> inputKeys, String packageName, File outputDir, String className, boolean generateRx) throws IOException {
        TypeSpec.Builder pClass = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        setUpContextAndPreferences(pClass, generateRx);

        pClass.addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addStatement("throw new $T($S)", AssertionError.class, "No instances.")
                        .build()
        );

        for (PrefEntry entry : inputKeys) {
            pClass.addType(generatePrefBlock(entry, packageName, generateRx));
        }

        JavaFile javaFile = JavaFile.builder(packageName, pClass.build()).build();
        javaFile.writeTo(outputDir);
    }

    private static void setUpContextAndPreferences(TypeSpec.Builder pClass, boolean generateRx) {
        pClass.addField(FieldSpec.builder(Resources.class, "RESOURCES", Modifier.PRIVATE, Modifier.STATIC)
                        .initializer("null")
                        .build()
        );

        pClass.addField(FieldSpec.builder(SharedPreferences.class, "PREFERENCES", Modifier.PRIVATE, Modifier.STATIC)
                        .initializer("null")
                        .build()
        );

        if (generateRx) {
            pClass.addField(FieldSpec.builder(CN_RX_PREFERENCES, "RX_PREFERENCES", Modifier.PRIVATE, Modifier.STATIC)
                            .initializer("null")
                            .build()
            );
        }

        pClass.addMethod(MethodSpec.methodBuilder("init")
                        .addModifiers(MODIFIERS)
                        .addJavadoc("Initializer that takes a {@link Context} for resource resolution. This should be an Application context instance, and will retrieve default shared preferences.\n")
                        .addParameter(ParameterSpec.builder(Context.class, "applicationContext", Modifier.FINAL).build())
                        .beginControlFlow("if (applicationContext == null)")
                        .addStatement("throw new $T($S)", IllegalStateException.class, "applicationContext cannot be null!")
                        .endControlFlow()
                        .beginControlFlow("if (!(applicationContext instanceof $T))", Application.class)
                        .addStatement("throw new $T($S)", IllegalArgumentException.class, "You may only use an Application instance as context!")
                        .endControlFlow()
                        .addStatement("RESOURCES = applicationContext.getResources()")
                        .addStatement("// Sensible default")
                        .addStatement("setSharedPreferences($T.getDefaultSharedPreferences(applicationContext))", PreferenceManager.class)
                        .build()
        );

        MethodSpec.Builder setSharedPreferencesBuilder = MethodSpec.methodBuilder("setSharedPreferences")
                .addModifiers(MODIFIERS)
                .addParameter(ParameterSpec.builder(SharedPreferences.class, "sharedPreferences", Modifier.FINAL).build())
                .beginControlFlow("if (sharedPreferences == null)")
                .addStatement("throw new $T($S)", IllegalStateException.class, "sharedPreferences cannot be null!")
                .endControlFlow()
                .addStatement("PREFERENCES = sharedPreferences");

        if (generateRx) {
            setSharedPreferencesBuilder.addStatement("RX_PREFERENCES = $T.create(PREFERENCES)", CN_RX_PREFERENCES);
        }

        pClass.addMethod(setSharedPreferencesBuilder.build());
    }

    private static TypeSpec generatePrefBlock(PrefEntry entry, String packageName, boolean generateRx) {
        TypeSpec.Builder entryClass = TypeSpec.classBuilder(camelCaseKey(entry.key)).addModifiers(MODIFIERS);
        entryClass.addField(FieldSpec.builder(String.class, "key", MODIFIERS).initializer("$S", entry.key).build());

        if (entry.defaultType != null) {
            if (entry.isResource) {
                entryClass.addField(FieldSpec.builder(int.class, "defaultResId", MODIFIERS).initializer("$T.$N.$N", ClassName.get(packageName, "R"), entry.resType, entry.defaultValue).build());
                entryClass.addMethod(generateResolveDefaultResMethod(entry));
            } else {
                boolean isString = entry.defaultType == String.class;
                entryClass.addMethod(MethodSpec.methodBuilder("defaultValue")
                        .addModifiers(MODIFIERS)
                        .returns(entry.defaultType)
                        .addStatement("return " + (isString ? "$S" : "$N"), isString ? entry.defaultValue : entry.defaultValue.toString())
                        .build());
            }
        }

        // Add getter
        if (entry.valueType != null || entry.defaultType != null) {
            Class<?> prefType = entry.valueType != null ? entry.valueType : entry.defaultType;
            entryClass.addMethod(MethodSpec.methodBuilder("get")
                            .addModifiers(MODIFIERS)
                            .returns(prefType)
                            .addStatement("return PREFERENCES.$N", resolvePreferenceStmt(entry, true))
                            .build()
            );

            entryClass.addMethod(MethodSpec.methodBuilder("put")
                            .addModifiers(MODIFIERS)
                            .returns(SharedPreferences.Editor.class)
                            .addParameter(ParameterSpec.builder(prefType, "val", Modifier.FINAL).build())
                            .addStatement("return PREFERENCES.edit().$N", resolvePreferenceStmt(entry, false))
                            .build()
            );

            Class<?> referenceType = resolveReferenceType(prefType);
            if (generateRx && referenceType != null) {
                entryClass.addMethod(MethodSpec.methodBuilder("rx")
                                .addModifiers(MODIFIERS)
                                .returns(ParameterizedTypeName.get(CN_RX_PREFERENCE, TypeName.get(referenceType)))
                                .addStatement("return RX_PREFERENCES.get$N(key)", referenceType.getSimpleName())
                                .build()
                );
            }
        }

        if (entry.hasListAttributes) {
            if (entry.entriesGetterStmt != null) {
                entryClass.addMethod(MethodSpec.methodBuilder("entries")
                                .addModifiers(MODIFIERS)
                                .returns(CharSequence[].class)
                                .addStatement("return RESOURCES." + entry.entriesGetterStmt)
                                .build()
                );
            }
            if (entry.entryValuesGetterStmt != null) {
                entryClass.addMethod(MethodSpec.methodBuilder("entryValues")
                                .addModifiers(MODIFIERS)
                                .returns(CharSequence[].class)
                                .addStatement("return RESOURCES." + entry.entryValuesGetterStmt)
                                .build()
                );
            }
        }

        return entryClass.build();
    }

    static String camelCaseKey(String input) {

        // Default to lower_underscore, as this is the platform convention
        CaseFormat format = CaseFormat.LOWER_UNDERSCORE;

        boolean couldBeCamel = COULD_BE_CAMEL.matcher(input).matches();
        if (!couldBeCamel) {
            if (input.contains("-")) {
                format = CaseFormat.LOWER_HYPHEN;
            } else if (input.contains("_")) {
                boolean isAllCaps =  ALL_CAPS.matcher(input).matches();
                format = isAllCaps ? CaseFormat.UPPER_UNDERSCORE : CaseFormat.LOWER_UNDERSCORE;
            }
        } else {
            format = Character.isUpperCase(input.charAt(0)) ? CaseFormat.UPPER_CAMEL : CaseFormat.LOWER_CAMEL;
        }

        return format == CaseFormat.LOWER_CAMEL ? input : format.to(CaseFormat.LOWER_CAMEL, input);
    }

    private static MethodSpec generateResolveDefaultResMethod(PrefEntry entry) {
        return MethodSpec.methodBuilder("defaultValue")
                .addModifiers(MODIFIERS)
                .returns(entry.valueType)
                .addCode(CodeBlock.builder().addStatement("return RESOURCES.$N", entry.resourceDefaultValueGetterStmt).build())
                .build();
    }

    private static Class<?> resolveReferenceType(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }
        switch (clazz.getSimpleName()) {
            case "Boolean":
            case "boolean":
                return Boolean.class;
            case "Integer":
            case "int":
                return Integer.class;
            default:
                // Currently unsupported
                return null;
        }
    }

    private static String resolvePreferenceStmt(PrefEntry entry, boolean isGetter) {
        String defaultValue = "defaultValue()";
        String simpleName = StringUtils.capitalize(entry.valueType.getSimpleName());
        if (entry.defaultType == null) {
            // No defaultValue() method will be available
            switch (simpleName) {
                case "Boolean":
                    defaultValue = "false";
                    break;
                case "Int":
                    defaultValue = "-1";
                    break;
                case "String":
                    defaultValue = "null";
                    break;
                default:
                    defaultValue = "null";
                    break;
            }
        }

        if (isGetter) {
            return "get" + simpleName + "(key, " + defaultValue + ")";
        } else {
            return "put" + simpleName + "(key, val)";
        }
    }

    private PClassGenerator() {
        throw new AssertionError("No instances.");
    }

}
