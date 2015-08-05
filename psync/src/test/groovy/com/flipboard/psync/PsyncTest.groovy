package com.flipboard.psync
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.LibraryVariant
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.ReturnStmt
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import rx.Observable

import java.lang.reflect.Modifier

import static com.flipboard.psync.TestHelper.isPSF
import static com.google.common.truth.Truth.assertThat

class PsyncTest {

    // This is necessary because the IDE debugger and command line invocations have different working directories ಠ_ಠ
    private static final WORKING_DIR = System.getProperty("user.dir")
    private static final PATH_PREFIX = WORKING_DIR.endsWith("psync") ? WORKING_DIR : "$WORKING_DIR/psync"

    static final String FIXTURE_WORKING_DIR = "$PATH_PREFIX/src/test/fixtures/android_app"
    private static final RESOURCE_PATH = "$PATH_PREFIX/src/test/resources/"
    private static final OUT_PATH = "$PATH_PREFIX/build/test/out/"

    @BeforeClass
    static void setup() {
        File destinationDir = new File(OUT_PATH)
        destinationDir.mkdirs()
    }

    @AfterClass
    static void tearDown() {
        new File(OUT_PATH).deleteDir()

        // I don't know why this gets generated, but toss it
        new File("${FIXTURE_WORKING_DIR}/userHome").deleteDir()
    }

    @Test
    void testBasicPrefEntry() {
        PrefEntry<String> entry = PrefEntry.create("myKey", "someDefault")
        assertThat(entry.equals(entry)) // Truth will check == under the hood first, which we want to avoid
        assertThat(entry).isNotEqualTo "Banana"

        PrefEntry that = PrefEntry.create("myKey", "someDefault")
        assertThat(entry).isEqualTo that

        that = PrefEntry.create("myKey", "differentDefault")
        assertThat(entry).isEqualTo that

        that = PrefEntry.create("myKey", 3)
        assertThat(entry).isEqualTo that

        that = PrefEntry.create("myOtherKey", "someDefault")
        assertThat(entry).isNotEqualTo that
    }

    @Test(expected = UnsupportedOperationException.class)
    void testThrowsOnUnsupportedType() {
        PrefEntry.create("myKey", Double.class)
    }

    @Test()
    void testUnsupportedResourceType() {
        assertThat(PSyncTask.generateResourcePrefEntry("my_key", "@id/my_pref_value").isBlank())
        assertThat(PSyncTask.generateResourcePrefEntry("my_key", "@dimen/my_pref_value").isBlank())
        assertThat(PSyncTask.generateResourcePrefEntry("my_key", "@banana/my_pref_value").isBlank())
    }

    @Test
    void testGenerateResourcePrefEntry() {
        PrefEntry entry = PSyncTask.generateResourcePrefEntry("my_key", "@string/my_pref_value")
        assertThat(entry.key).isEqualTo "my_key"
        assertThat(entry.isResource)
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultValue).isEqualTo "my_pref_value"
        assertThat(entry.resType).isEqualTo "string"
        assertThat(entry.defaultType).isEqualTo String.class
    }

    @Test
    void testNaturalOrdering() {
        List<PrefEntry> entries = Arrays.asList(
                PrefEntry.create("a", "someDefault"),
                PrefEntry.create("b", "someDefault"),
                PrefEntry.create("c", "someDefault"),
                PrefEntry.create("d", "someDefault"),
        )

        Collections.shuffle(entries)
        Collections.sort(entries)
        assertThat(entries).isStrictlyOrdered()
    }

    @Test
    void testCamelKey() {
        assertThat(PClassGenerator.camelCaseKey("lower_case")).isEqualTo "lowerCase"
        assertThat(PClassGenerator.camelCaseKey("camelCase")).isEqualTo "camelCase"
        assertThat(PClassGenerator.camelCaseKey("UPPER_STUFF")).isEqualTo "upperStuff"
        assertThat(PClassGenerator.camelCaseKey("UPPER-STUFF")).isEqualTo "upperStuff"
        assertThat(PClassGenerator.camelCaseKey("CamelCase")).isEqualTo "camelCase"
        assertThat(PClassGenerator.camelCaseKey("oneTwo_3")).isEqualTo "onetwo3"
    }

    @Test
    void testGetPrefEntriesFromFiles() {
        RecordingObserver<PrefEntry> o = new RecordingObserver<>()
        //noinspection GroovyAssignabilityCheck
        PSyncTask.getPrefEntriesFromFiles(Collections.singletonList(new File("$RESOURCE_PATH/prefs.xml")))
                .flatMap {List<PrefEntry> entries -> Observable.from(entries)}
                .subscribe(o)

        PrefEntry entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "number_of_columns"
        assertThat(entry.defaultValue).isInstanceOf Integer
        assertThat(entry.defaultType).isEqualTo int.class
        assertThat(!entry.isResource)
        assertThat(entry.defaultValue).isEqualTo 3
        assertThat(entry.resType).isNull()
        assertThat(entry.valueType).isEqualTo int.class
        assertThat(entry.resourceDefaultValueGetterStmt).isNull()
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "number_of_rows"
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(entry.isResource)
        assertThat(entry.defaultValue).isEqualTo "num_rows"
        assertThat(entry.resType).isEqualTo "integer"
        assertThat(entry.valueType).isEqualTo int.class
        assertThat(entry.resourceDefaultValueGetterStmt).isEqualTo "getInteger(defaultResId)"
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "pref_cat_server"
        assertThat(entry.defaultValue).isNull()
        assertThat(entry.defaultType).isNull()
        assertThat(!entry.isResource)
        assertThat(entry.resType).isNull()
        assertThat(entry.resourceDefaultValueGetterStmt).isNull()
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "primary_color"
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(entry.isResource)
        assertThat(entry.defaultValue).isEqualTo "flipboard_red"
        assertThat(entry.resType).isEqualTo "color"
        assertThat(entry.valueType).isEqualTo int.class
        assertThat(entry.resourceDefaultValueGetterStmt).isEqualTo "getColor(defaultResId)"
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "request_agent"
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(!entry.isResource)
        assertThat(entry.defaultValue).isEqualTo "banana"
        assertThat(entry.resType).isNull()
        assertThat(entry.valueType).isEqualTo String.class
        assertThat(entry.resourceDefaultValueGetterStmt).isNull()
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "request_types"
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(entry.defaultValue).isEqualTo "default_request_type"
        assertThat(entry.resType).isEqualTo "string"
        assertThat(entry.isResource)
        assertThat(entry.valueType).isEqualTo String.class
        assertThat(entry.resourceDefaultValueGetterStmt).isEqualTo "getString(defaultResId)"
        assertThat(entry.entriesGetterStmt).isEqualTo "getTextArray(R.array.request_types_entries)"
        assertThat(entry.entryValuesGetterStmt).isEqualTo "getTextArray(R.array.request_types_entry_values)"

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "server_url"
        assertThat(entry.defaultValue).isInstanceOf String
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(entry.isResource)
        assertThat(entry.defaultValue).isEqualTo "server_url"
        assertThat(entry.resType).isEqualTo "string"
        assertThat(entry.valueType).isEqualTo String.class
        assertThat(entry.resourceDefaultValueGetterStmt).isEqualTo "getString(defaultResId)"
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "show_images"
        assertThat(entry.defaultValue).isInstanceOf Boolean
        assertThat(entry.defaultType).isEqualTo boolean.class
        assertThat(!entry.isResource)
        assertThat(entry.defaultValue as boolean)
        assertThat(entry.resType).isNull()
        assertThat(entry.valueType).isEqualTo boolean.class
        assertThat(entry.resourceDefaultValueGetterStmt).isNull()
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        entry = o.takeNext()
        assertThat(entry).isNotNull()
        assertThat(entry.key).isEqualTo "use_inputs"
        assertThat(entry.defaultValue).isInstanceOf String.class
        assertThat(entry.defaultType).isEqualTo String.class
        assertThat(entry.isResource)
        assertThat(entry.defaultValue).isEqualTo "use_inputs"
        assertThat(entry.resType).isEqualTo "bool"
        assertThat(entry.valueType).isEqualTo boolean.class
        assertThat(entry.resourceDefaultValueGetterStmt).isEqualTo "getBoolean(defaultResId)"
        assertThat(entry.hasListAttributes).isFalse()
        assertThat(entry.entriesGetterStmt).isNull()
        assertThat(entry.entryValuesGetterStmt).isNull()

        o.assertOnCompleted()
        o.assertNoMoreEvents()
    }

    @Test
    public void testGeneration() {
        List<PrefEntry> entries = PSyncTask.getPrefEntriesFromFiles(Collections.singletonList(new File("$RESOURCE_PATH/prefs.xml"))).toBlocking().first()
        File outputDir = new File(OUT_PATH)
        PClassGenerator.generate(entries, "com.flipboard.psync.test", outputDir, "P", true)
        File generatedFile = new File("$OUT_PATH/com/flipboard/psync/test/P.java")

        assertThat(generatedFile.exists())

        // Verify the file
        CompilationUnit cu = JavaParser.parse(generatedFile)

        ClassOrInterfaceDeclaration pClass = cu.getTypes()[0] as ClassOrInterfaceDeclaration

        assertThat(Modifier.isPublic(pClass.modifiers))
        assertThat(Modifier.isFinal(pClass.modifiers))
        assertThat(pClass.name).isEqualTo "P"
        assertThat(pClass.members).hasSize 15

        ConstructorDeclaration constructor = pClass.members.find {it instanceof ConstructorDeclaration} as ConstructorDeclaration
        assertThat(constructor).isNotNull()
        assertThat(Modifier.isPrivate(constructor.modifiers))
        assertThat(constructor.block.stmts[0].toString()).isEqualTo "throw new  AssertionError(\"No instances.\");"
        assertThat(pClass.members.find {it instanceof ConstructorDeclaration})

        List<BodyDeclaration> typeMembers = pClass.members.subList(6, pClass.members.size())
        typeMembers.each {
            assertThat(it).isInstanceOf ClassOrInterfaceDeclaration
        }
        assertThat(typeMembers.collect {it.name}).isStrictlyOrdered()

        int classCount = 0;

        ClassOrInterfaceDeclaration colNum = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(colNum.modifiers))
        assertThat(colNum.name).isEqualTo "numberOfColumns"
        assertThat(colNum.members).hasSize 5
        FieldDeclaration colNumKey = colNum.members[0] as FieldDeclaration
        assertThat(isPSF(colNumKey.modifiers))
        assertThat(colNumKey.type.toString()).isEqualTo "String"
        assertThat(colNumKey.variables[0].id.name).isEqualTo "key"
        assertThat(colNumKey.variables[0].init.toString()).isEqualTo "\"number_of_columns\""
        MethodDeclaration colNumDefaultGetter = colNum.members[1] as MethodDeclaration
        assertThat(isPSF(colNumDefaultGetter.modifiers))
        assertThat(colNumDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(colNumDefaultGetter.type.toString()).isEqualTo "int"
        assertThat(colNumDefaultGetter.body.stmts).hasSize(1)
        assertThat(colNumDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(colNumDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "3"
        MethodDeclaration colNumPrefGetter = colNum.members[2] as MethodDeclaration
        assertThat(isPSF(colNumPrefGetter.modifiers))
        assertThat(colNumPrefGetter.name).isEqualTo "get"
        assertThat(colNumPrefGetter.type.toString()).isEqualTo "int"
        assertThat(colNumPrefGetter.body.stmts).hasSize(1)
        assertThat(colNumPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(colNumPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getInt(key, defaultValue())"
        MethodDeclaration colNumPrefPutter = colNum.members[3] as MethodDeclaration
        assertThat(isPSF(colNumPrefPutter.modifiers))
        assertThat(colNumPrefPutter.name).isEqualTo "put"
        assertThat(colNumPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(colNumPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(colNumPrefPutter.parameters[0].modifiers))
        assertThat(colNumPrefPutter.parameters[0].type.toString()).isEqualTo "int"
        assertThat(colNumPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(colNumPrefPutter.body.stmts).hasSize(1)
        assertThat(colNumPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(colNumPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putInt(key, val)"
        MethodDeclaration colNumRxGetter = colNum.members[4] as MethodDeclaration
        assertThat(isPSF(colNumRxGetter.modifiers))
        assertThat(colNumRxGetter.name).isEqualTo "rx"
        assertThat(colNumRxGetter.type.toString()).isEqualTo "Preference<Integer>"
        assertThat(colNumRxGetter.body.stmts).hasSize(1)
        assertThat(colNumRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(colNumRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getInteger(key)"

        ClassOrInterfaceDeclaration numRows = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(numRows.modifiers))
        assertThat(numRows.name).isEqualTo "numberOfRows"
        assertThat(numRows.members).hasSize 6
        FieldDeclaration numRowsKey = numRows.members[0] as FieldDeclaration
        assertThat(isPSF(numRowsKey.modifiers))
        assertThat(numRowsKey.type.toString()).isEqualTo "String"
        assertThat(numRowsKey.variables[0].id.name).isEqualTo "key"
        assertThat(numRowsKey.variables[0].init.toString()).isEqualTo "\"number_of_rows\""
        FieldDeclaration numRowsDefault = numRows.members[1] as FieldDeclaration
        assertThat(isPSF(numRowsDefault.modifiers))
        assertThat(numRowsDefault.type.toString()).isEqualTo "int"
        assertThat(numRowsDefault.variables[0].id.name).isEqualTo "defaultResId"
        assertThat(numRowsDefault.variables[0].init.toString()).isEqualTo "R.integer.num_rows"
        MethodDeclaration numRowsDefaultGetter = numRows.members[2] as MethodDeclaration
        assertThat(isPSF(numRowsDefaultGetter.modifiers))
        assertThat(numRowsDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(numRowsDefaultGetter.type.toString()).isEqualTo "int"
        assertThat(numRowsDefaultGetter.body.stmts).hasSize(1)
        assertThat(numRowsDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(numRowsDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getInteger(defaultResId)"
        MethodDeclaration numRowsPrefGetter = numRows.members[3] as MethodDeclaration
        assertThat(isPSF(numRowsPrefGetter.modifiers))
        assertThat(numRowsPrefGetter.name).isEqualTo "get"
        assertThat(numRowsPrefGetter.type.toString()).isEqualTo "int"
        assertThat(numRowsPrefGetter.body.stmts).hasSize(1)
        assertThat(numRowsPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(numRowsPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getInt(key, defaultValue())"
        MethodDeclaration numRowsPrefPutter = numRows.members[4] as MethodDeclaration
        assertThat(isPSF(numRowsPrefPutter.modifiers))
        assertThat(numRowsPrefPutter.name).isEqualTo "put"
        assertThat(numRowsPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(numRowsPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(numRowsPrefPutter.parameters[0].modifiers))
        assertThat(numRowsPrefPutter.parameters[0].type.toString()).isEqualTo "int"
        assertThat(numRowsPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(numRowsPrefPutter.body.stmts).hasSize(1)
        assertThat(numRowsPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(numRowsPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putInt(key, val)"
        MethodDeclaration numRowsRxGetter = numRows.members[5] as MethodDeclaration
        assertThat(isPSF(numRowsRxGetter.modifiers))
        assertThat(numRowsRxGetter.name).isEqualTo "rx"
        assertThat(numRowsRxGetter.type.toString()).isEqualTo "Preference<Integer>"
        assertThat(numRowsRxGetter.body.stmts).hasSize(1)
        assertThat(numRowsRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(numRowsRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getInteger(key)"

        ClassOrInterfaceDeclaration catServer = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(catServer.modifiers))
        assertThat(catServer.name).isEqualTo "prefCatServer"
        assertThat(catServer.members).hasSize 1
        assertThat(catServer.members[0]).isInstanceOf FieldDeclaration
        FieldDeclaration catServerField = catServer.members[0] as FieldDeclaration
        assertThat(isPSF(catServerField.modifiers))
        assertThat(catServerField.type.toString()).isEqualTo "String"
        assertThat(catServerField.variables[0].id.name).isEqualTo "key"
        assertThat(catServerField.variables[0].init.toString()).isEqualTo "\"pref_cat_server\""

        ClassOrInterfaceDeclaration primaryColor = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(primaryColor.modifiers))
        assertThat(primaryColor.name).isEqualTo "primaryColor"
        assertThat(primaryColor.members).hasSize 6
        FieldDeclaration primaryColorKey = primaryColor.members[0] as FieldDeclaration
        assertThat(isPSF(primaryColorKey.modifiers))
        assertThat(primaryColorKey.type.toString()).isEqualTo "String"
        assertThat(primaryColorKey.variables[0].id.name).isEqualTo "key"
        assertThat(primaryColorKey.variables[0].init.toString()).isEqualTo "\"primary_color\""
        FieldDeclaration primaryColorDefault = primaryColor.members[1] as FieldDeclaration
        assertThat(isPSF(primaryColorDefault.modifiers))
        assertThat(primaryColorDefault.type.toString()).isEqualTo "int"
        assertThat(primaryColorDefault.variables[0].id.name).isEqualTo "defaultResId"
        assertThat(primaryColorDefault.variables[0].init.toString()).isEqualTo "R.color.flipboard_red"
        MethodDeclaration primaryColorDefaultGetter = primaryColor.members[2] as MethodDeclaration
        assertThat(isPSF(primaryColorDefaultGetter.modifiers))
        assertThat(primaryColorDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(primaryColorDefaultGetter.type.toString()).isEqualTo "int"
        assertThat(primaryColorDefaultGetter.body.stmts).hasSize(1)
        assertThat(primaryColorDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(primaryColorDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getColor(defaultResId)"
        MethodDeclaration primaryColorPrefGetter = primaryColor.members[3] as MethodDeclaration
        assertThat(isPSF(primaryColorPrefGetter.modifiers))
        assertThat(primaryColorPrefGetter.name).isEqualTo "get"
        assertThat(primaryColorPrefGetter.type.toString()).isEqualTo "int"
        assertThat(primaryColorPrefGetter.body.stmts).hasSize(1)
        assertThat(primaryColorPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(primaryColorPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getInt(key, defaultValue())"
        MethodDeclaration primaryColorPrefPutter = primaryColor.members[4] as MethodDeclaration
        assertThat(isPSF(primaryColorPrefPutter.modifiers))
        assertThat(primaryColorPrefPutter.name).isEqualTo "put"
        assertThat(primaryColorPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(primaryColorPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(primaryColorPrefPutter.parameters[0].modifiers))
        assertThat(primaryColorPrefPutter.parameters[0].type.toString()).isEqualTo "int"
        assertThat(primaryColorPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(primaryColorPrefPutter.body.stmts).hasSize(1)
        assertThat(primaryColorPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(primaryColorPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putInt(key, val)"
        MethodDeclaration primaryColorRxGetter = primaryColor.members[5] as MethodDeclaration
        assertThat(isPSF(primaryColorRxGetter.modifiers))
        assertThat(primaryColorRxGetter.name).isEqualTo "rx"
        assertThat(primaryColorRxGetter.type.toString()).isEqualTo "Preference<Integer>"
        assertThat(primaryColorRxGetter.body.stmts).hasSize(1)
        assertThat(primaryColorRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(primaryColorRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getInteger(key)"

        ClassOrInterfaceDeclaration requestAgent = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(requestAgent.modifiers))
        assertThat(requestAgent.name).isEqualTo "requestAgent"
        assertThat(requestAgent.members).hasSize 5
        FieldDeclaration requestAgentKey = requestAgent.members[0] as FieldDeclaration
        assertThat(isPSF(requestAgentKey.modifiers))
        assertThat(requestAgentKey.type.toString()).isEqualTo "String"
        assertThat(requestAgentKey.variables[0].id.name).isEqualTo "key"
        assertThat(requestAgentKey.variables[0].init.toString()).isEqualTo "\"request_agent\""
        MethodDeclaration requestAgentDefaultGetter = requestAgent.members[1] as MethodDeclaration
        assertThat(isPSF(requestAgentDefaultGetter.modifiers))
        assertThat(requestAgentDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(requestAgentDefaultGetter.type.toString()).isEqualTo "String"
        assertThat(requestAgentDefaultGetter.body.stmts).hasSize(1)
        assertThat(requestAgentDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestAgentDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "\"banana\""
        MethodDeclaration requestAgentPrefGetter = requestAgent.members[2] as MethodDeclaration
        assertThat(isPSF(requestAgentPrefGetter.modifiers))
        assertThat(requestAgentPrefGetter.name).isEqualTo "get"
        assertThat(requestAgentPrefGetter.type.toString()).isEqualTo "String"
        assertThat(requestAgentPrefGetter.body.stmts).hasSize(1)
        assertThat(requestAgentPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestAgentPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getString(key, defaultValue())"
        MethodDeclaration requestAgentPrefPutter = requestAgent.members[3] as MethodDeclaration
        assertThat(isPSF(requestAgentPrefPutter.modifiers))
        assertThat(requestAgentPrefPutter.name).isEqualTo "put"
        assertThat(requestAgentPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(requestAgentPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(requestAgentPrefPutter.parameters[0].modifiers))
        assertThat(requestAgentPrefPutter.parameters[0].type.toString()).isEqualTo "String"
        assertThat(requestAgentPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(requestAgentPrefPutter.body.stmts).hasSize(1)
        assertThat(requestAgentPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestAgentPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putString(key, val)"
        MethodDeclaration requestAgentRxGetter = requestAgent.members[4] as MethodDeclaration
        assertThat(isPSF(requestAgentRxGetter.modifiers))
        assertThat(requestAgentRxGetter.name).isEqualTo "rx"
        assertThat(requestAgentRxGetter.type.toString()).isEqualTo "Preference<String>"
        assertThat(requestAgentRxGetter.body.stmts).hasSize(1)
        assertThat(requestAgentRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestAgentRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getString(key)"

        ClassOrInterfaceDeclaration requestTypes = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(requestTypes.modifiers))
        assertThat(requestTypes.name).isEqualTo "requestTypes"
        assertThat(requestTypes.members).hasSize 8
        FieldDeclaration requestTypesKey = requestTypes.members[0] as FieldDeclaration
        assertThat(isPSF(requestTypesKey.modifiers))
        assertThat(requestTypesKey.type.toString()).isEqualTo "String"
        assertThat(requestTypesKey.variables[0].id.name).isEqualTo "key"
        assertThat(requestTypesKey.variables[0].init.toString()).isEqualTo "\"request_types\""
        FieldDeclaration requestTypesDefault = requestTypes.members[1] as FieldDeclaration
        assertThat(isPSF(requestTypesDefault.modifiers))
        assertThat(requestTypesDefault.type.toString()).isEqualTo "int"
        assertThat(requestTypesDefault.variables[0].id.name).isEqualTo "defaultResId"
        assertThat(requestTypesDefault.variables[0].init.toString()).isEqualTo "R.string.default_request_type"
        MethodDeclaration requestTypesDefaultGetter = requestTypes.members[2] as MethodDeclaration
        assertThat(isPSF(requestTypesDefaultGetter.modifiers))
        assertThat(requestTypesDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(requestTypesDefaultGetter.type.toString()).isEqualTo "String"
        assertThat(requestTypesDefaultGetter.body.stmts).hasSize(1)
        assertThat(requestTypesDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestTypesDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getString(defaultResId)"
        MethodDeclaration requestTypesPrefGetter = requestTypes.members[3] as MethodDeclaration
        assertThat(isPSF(requestTypesPrefGetter.modifiers))
        assertThat(requestTypesPrefGetter.name).isEqualTo "get"
        assertThat(requestTypesPrefGetter.type.toString()).isEqualTo "String"
        assertThat(requestTypesPrefGetter.body.stmts).hasSize(1)
        assertThat(requestTypesPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestTypesPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getString(key, defaultValue())"
        MethodDeclaration requestTypesPrefPutter = requestTypes.members[4] as MethodDeclaration
        assertThat(isPSF(requestTypesPrefPutter.modifiers))
        assertThat(requestTypesPrefPutter.name).isEqualTo "put"
        assertThat(requestTypesPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(requestTypesPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(requestTypesPrefPutter.parameters[0].modifiers))
        assertThat(requestTypesPrefPutter.parameters[0].type.toString()).isEqualTo "String"
        assertThat(requestTypesPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(requestTypesPrefPutter.body.stmts).hasSize(1)
        assertThat(requestTypesPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestTypesPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putString(key, val)"
        MethodDeclaration requestTypesRxGetter = requestTypes.members[5] as MethodDeclaration
        assertThat(isPSF(requestTypesRxGetter.modifiers))
        assertThat(requestTypesRxGetter.name).isEqualTo "rx"
        assertThat(requestTypesRxGetter.type.toString()).isEqualTo "Preference<String>"
        assertThat(requestTypesRxGetter.body.stmts).hasSize(1)
        assertThat(requestTypesRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(requestTypesRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getString(key)"
        MethodDeclaration entriesGetter = requestTypes.members[6] as MethodDeclaration
        assertThat(isPSF(entriesGetter.modifiers))
        assertThat(entriesGetter.name).isEqualTo "entries"
        assertThat(entriesGetter.type.toString()).isEqualTo "CharSequence[]"
        assertThat(entriesGetter.body.stmts).hasSize(1)
        assertThat(entriesGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(entriesGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getTextArray(R.array.request_types_entries)"
        MethodDeclaration entryValuesGetter = requestTypes.members[7] as MethodDeclaration
        assertThat(isPSF(entryValuesGetter.modifiers))
        assertThat(entryValuesGetter.name).isEqualTo "entryValues"
        assertThat(entryValuesGetter.type.toString()).isEqualTo "CharSequence[]"
        assertThat(entryValuesGetter.body.stmts).hasSize(1)
        assertThat(entryValuesGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(entryValuesGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getTextArray(R.array.request_types_entry_values)"

        ClassOrInterfaceDeclaration serverUrl = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(serverUrl.modifiers))
        assertThat(serverUrl.name).isEqualTo "serverUrl"
        assertThat(serverUrl.members).hasSize 6
        FieldDeclaration serverUrlKey = serverUrl.members[0] as FieldDeclaration
        assertThat(isPSF(serverUrlKey.modifiers))
        assertThat(serverUrlKey.type.toString()).isEqualTo "String"
        assertThat(serverUrlKey.variables[0].id.name).isEqualTo "key"
        assertThat(serverUrlKey.variables[0].init.toString()).isEqualTo "\"server_url\""
        FieldDeclaration serverUrlDefault = serverUrl.members[1] as FieldDeclaration
        assertThat(isPSF(serverUrlDefault.modifiers))
        assertThat(serverUrlDefault.type.toString()).isEqualTo "int"
        assertThat(serverUrlDefault.variables[0].id.name).isEqualTo "defaultResId"
        assertThat(serverUrlDefault.variables[0].init.toString()).isEqualTo "R.string.server_url"
        MethodDeclaration serverDefaultGetter = serverUrl.members[2] as MethodDeclaration
        assertThat(isPSF(serverDefaultGetter.modifiers))
        assertThat(serverDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(serverDefaultGetter.type.toString()).isEqualTo "String"
        assertThat(serverDefaultGetter.body.stmts).hasSize(1)
        assertThat(serverDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(serverDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getString(defaultResId)"
        MethodDeclaration serverUrlPrefGetter = serverUrl.members[3] as MethodDeclaration
        assertThat(isPSF(serverUrlPrefGetter.modifiers))
        assertThat(serverUrlPrefGetter.name).isEqualTo "get"
        assertThat(serverUrlPrefGetter.type.toString()).isEqualTo "String"
        assertThat(serverUrlPrefGetter.body.stmts).hasSize(1)
        assertThat(serverUrlPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(serverUrlPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getString(key, defaultValue())"
        MethodDeclaration serverUrlPrefPutter = serverUrl.members[4] as MethodDeclaration
        assertThat(isPSF(serverUrlPrefPutter.modifiers))
        assertThat(serverUrlPrefPutter.name).isEqualTo "put"
        assertThat(serverUrlPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(serverUrlPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(serverUrlPrefPutter.parameters[0].modifiers))
        assertThat(serverUrlPrefPutter.parameters[0].type.toString()).isEqualTo "String"
        assertThat(serverUrlPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(serverUrlPrefPutter.body.stmts).hasSize(1)
        assertThat(serverUrlPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(serverUrlPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putString(key, val)"
        MethodDeclaration serverUrlRxGetter = serverUrl.members[5] as MethodDeclaration
        assertThat(isPSF(serverUrlRxGetter.modifiers))
        assertThat(serverUrlRxGetter.name).isEqualTo "rx"
        assertThat(serverUrlRxGetter.type.toString()).isEqualTo "Preference<String>"
        assertThat(serverUrlRxGetter.body.stmts).hasSize(1)
        assertThat(serverUrlRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(serverUrlRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getString(key)"

        ClassOrInterfaceDeclaration showImages = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(showImages.modifiers))
        assertThat(showImages.name).isEqualTo "showImages"
        assertThat(showImages.members).hasSize 5
        assertThat(showImages.members[0]).isInstanceOf FieldDeclaration
        FieldDeclaration showImagesKey = showImages.members[0] as FieldDeclaration
        assertThat(isPSF(showImagesKey.modifiers))
        assertThat(showImagesKey.type.toString()).isEqualTo "String"
        assertThat(showImagesKey.variables[0].id.name).isEqualTo "key"
        assertThat(showImagesKey.variables[0].init.toString()).isEqualTo "\"show_images\""
        MethodDeclaration showImagesDefaultGetter = showImages.members[1] as MethodDeclaration
        assertThat(isPSF(showImagesDefaultGetter.modifiers))
        assertThat(showImagesDefaultGetter.type.toString()).isEqualTo "boolean"
        assertThat(showImagesDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(showImagesDefaultGetter.body.stmts).hasSize(1)
        assertThat(showImagesDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(showImagesDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "true"
        MethodDeclaration showImagesPrefGetter = showImages.members[2] as MethodDeclaration
        assertThat(isPSF(showImagesPrefGetter.modifiers))
        assertThat(showImagesPrefGetter.name).isEqualTo "get"
        assertThat(showImagesPrefGetter.type.toString()).isEqualTo "boolean"
        assertThat(showImagesPrefGetter.body.stmts).hasSize(1)
        assertThat(showImagesPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(showImagesPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getBoolean(key, defaultValue())"
        MethodDeclaration showImagesPrefPutter = showImages.members[3] as MethodDeclaration
        assertThat(isPSF(showImagesPrefPutter.modifiers))
        assertThat(showImagesPrefPutter.name).isEqualTo "put"
        assertThat(showImagesPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(showImagesPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(showImagesPrefPutter.parameters[0].modifiers))
        assertThat(showImagesPrefPutter.parameters[0].type.toString()).isEqualTo "boolean"
        assertThat(showImagesPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(showImagesPrefPutter.body.stmts).hasSize(1)
        assertThat(showImagesPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(showImagesPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putBoolean(key, val)"
        MethodDeclaration showImagesRxGetter = showImages.members[4] as MethodDeclaration
        assertThat(isPSF(showImagesRxGetter.modifiers))
        assertThat(showImagesRxGetter.name).isEqualTo "rx"
        assertThat(showImagesRxGetter.type.toString()).isEqualTo "Preference<Boolean>"
        assertThat(showImagesRxGetter.body.stmts).hasSize(1)
        assertThat(showImagesRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(showImagesRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getBoolean(key)"

        ClassOrInterfaceDeclaration useInputs = typeMembers[classCount++] as ClassOrInterfaceDeclaration
        assertThat(isPSF(useInputs.modifiers))
        assertThat(useInputs.name).isEqualTo "useInputs"
        assertThat(useInputs.members).hasSize 6
        FieldDeclaration useInputsKey = useInputs.members[0] as FieldDeclaration
        assertThat(isPSF(useInputsKey.modifiers))
        assertThat(useInputsKey.type.toString()).isEqualTo "String"
        assertThat(useInputsKey.variables[0].id.name).isEqualTo "key"
        assertThat(useInputsKey.variables[0].init.toString()).isEqualTo "\"use_inputs\""
        FieldDeclaration useInputsDefault = useInputs.members[1] as FieldDeclaration
        assertThat(isPSF(useInputsDefault.modifiers))
        assertThat(useInputsDefault.type.toString()).isEqualTo "int"
        assertThat(useInputsDefault.variables[0].id.name).isEqualTo "defaultResId"
        assertThat(useInputsDefault.variables[0].init.toString()).isEqualTo "R.bool.use_inputs"
        MethodDeclaration useInputsDefaultGetter = useInputs.members[2] as MethodDeclaration
        assertThat(isPSF(useInputsDefaultGetter.modifiers))
        assertThat(useInputsDefaultGetter.name).isEqualTo "defaultValue"
        assertThat(useInputsDefaultGetter.type.toString()).isEqualTo "boolean"
        assertThat(useInputsDefaultGetter.body.stmts).hasSize(1)
        assertThat(useInputsDefaultGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(useInputsDefaultGetter.body.stmts[0].expr.toString()).isEqualTo "RESOURCES.getBoolean(defaultResId)"
        MethodDeclaration useInputsPrefGetter = useInputs.members[3] as MethodDeclaration
        assertThat(isPSF(useInputsPrefGetter.modifiers))
        assertThat(useInputsPrefGetter.name).isEqualTo "get"
        assertThat(useInputsPrefGetter.type.toString()).isEqualTo "boolean"
        assertThat(useInputsPrefGetter.body.stmts).hasSize(1)
        assertThat(useInputsPrefGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(useInputsPrefGetter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.getBoolean(key, defaultValue())"
        MethodDeclaration useInputsPrefPutter = useInputs.members[4] as MethodDeclaration
        assertThat(isPSF(useInputsPrefPutter.modifiers))
        assertThat(useInputsPrefPutter.name).isEqualTo "put"
        assertThat(useInputsPrefPutter.type.toString()).isEqualTo "SharedPreferences.Editor"
        assertThat(useInputsPrefPutter.parameters)hasSize 1
        assertThat(Modifier.isFinal(useInputsPrefPutter.parameters[0].modifiers))
        assertThat(useInputsPrefPutter.parameters[0].type.toString()).isEqualTo "boolean"
        assertThat(useInputsPrefPutter.parameters[0].id.toString()).isEqualTo "val"
        assertThat(useInputsPrefPutter.body.stmts).hasSize(1)
        assertThat(useInputsPrefPutter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(useInputsPrefPutter.body.stmts[0].expr.toString()).isEqualTo "PREFERENCES.edit().putBoolean(key, val)"
        MethodDeclaration useInputsRxGetter = useInputs.members[5] as MethodDeclaration
        assertThat(isPSF(useInputsRxGetter.modifiers))
        assertThat(useInputsRxGetter.name).isEqualTo "rx"
        assertThat(useInputsRxGetter.type.toString()).isEqualTo "Preference<Boolean>"
        assertThat(useInputsRxGetter.body.stmts).hasSize(1)
        assertThat(useInputsRxGetter.body.stmts[0]).isInstanceOf ReturnStmt
        assertThat(useInputsRxGetter.body.stmts[0].expr.toString()).isEqualTo "RX_PREFERENCES.getBoolean(key)"

        // Cleanup
        outputDir.deleteDir()
    }

    @Test
    public void testBasicConfiguration() {
        Project project = TestHelper.evaluatableAppProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.evaluate()

        // Register our task with the variant
        project.android.applicationVariants.all { ApplicationVariant variant ->
            Task task = project.tasks."generatePrefKeysFor${variant.name.capitalize()}"
            assertThat(task).isNotNull()

            PSyncTask syncTask = task as PSyncTask
            assertThat(syncTask.packageName).isEqualTo "com.flipboard.psync.test"
            assertThat(syncTask.className).isEqualTo "P"
            assertThat(syncTask.outputDir).isEqualTo new File("$project.buildDir/generated/source/psync/$variant.flavorName/$variant.buildType.name/")

            List<File> xmlFiles = syncTask.getSource().collect{it}
            assertThat(xmlFiles).isNotNull()
            assertThat(xmlFiles).hasSize 1
            assertThat(xmlFiles[0]).isEqualTo new File("${FIXTURE_WORKING_DIR}/src/main/res/xml/prefs.xml")

            syncTask.generate(TestHelper.getTaskInputs())

            assertThat(syncTask.outputDir.exists())
            assertThat("${syncTask.outputDir}/{${syncTask.packageName.replace('.', '/')}/${syncTask.className}.java")
        }

        project.buildDir.deleteDir()
    }

    @Test
    public void testClearExistingDir() {
        Project project = TestHelper.evaluatableAppProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.evaluate()

        // Register our task with the variant
        project.android.applicationVariants.all { ApplicationVariant variant ->
            Task task = project.tasks."generatePrefKeysFor${variant.name.capitalize()}"
            assertThat(task).isNotNull()

            PSyncTask syncTask = task as PSyncTask
            File outputDir = syncTask.outputDir;
            outputDir.mkdirs()
            File tmpFile = new File(outputDir, "tmp.txt")
            tmpFile.createNewFile()

            syncTask.generate(TestHelper.getTaskInputs())

            assertThat(outputDir.exists())
            assertThat(outputDir.listFiles().collect {it.name}).doesNotContain("tmp.txt")
        }

        project.buildDir.deleteDir()
    }

    @Test
    public void testBasicLibConfiguration() {
        Project project = TestHelper.evaluatableLibProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.psync {
            packageName = 'com.flipboard.psync.test'
        }
        project.evaluate()

        // Register our task with the variant
        project.android.libraryVariants.all { LibraryVariant variant ->
            Task task = project.tasks."generatePrefKeysFor${variant.name.capitalize()}"
            assertThat(task).isNotNull()

            PSyncTask syncTask = task as PSyncTask
            assertThat(syncTask.packageName).isEqualTo "com.flipboard.psync.test"
            assertThat(syncTask.className).isEqualTo "P"
            assertThat(syncTask.outputDir).isEqualTo new File("$project.buildDir/generated/source/psync/$variant.flavorName/$variant.buildType.name/")

            List<File> xmlFiles = syncTask.getSource().collect{it}
            assertThat(xmlFiles).isNotNull()
            assertThat(xmlFiles).hasSize 1
            assertThat(xmlFiles[0]).isEqualTo new File("${FIXTURE_WORKING_DIR}/src/main/res/xml/prefs.xml")

            syncTask.generate(TestHelper.getTaskInputs())

            assertThat(syncTask.outputDir.exists())
            assertThat("${syncTask.outputDir}/{${syncTask.packageName.replace('.', '/')}/${syncTask.className}.java")
        }

        project.buildDir.deleteDir()
    }

    @Test(expected = ProjectConfigurationException.class)
    public void testThrowsOnMissingAppId() {
        Project project = ProjectBuilder.builder().withProjectDir(new File(FIXTURE_WORKING_DIR)).build()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.evaluate()
        // No plugin applied, can't work here.
    }

    @Test(expected = ProjectConfigurationException.class)
    public void testThrowsOnMissingLibPackage() {
        Project project = TestHelper.evaluatableLibProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.evaluate()
        // No package name, can't work here.
    }

    @Test
    public void testIncludes() {
        Project project = TestHelper.evaluatableAppProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.psync {
            includesPattern = '**/xml/otherprefs.xml'
        }
        project.evaluate()

        // Register our task with the variant
        project.android.applicationVariants.all { ApplicationVariant variant ->
            Task task = project.tasks."generatePrefKeysFor${variant.name.capitalize()}"
            assertThat(task).isNotNull()

            PSyncTask syncTask = task as PSyncTask
            List<File> xmlFiles = syncTask.getSource().collect{it}
            assertThat(xmlFiles).isNotNull()
            assertThat(xmlFiles).isEmpty()
        }

        project.buildDir.deleteDir()
    }

    @Test
    public void testClassName() {
        String name = "MyPrefs"
        Project project = TestHelper.evaluatableAppProject()
        PSyncPlugin plugin = new PSyncPlugin()
        plugin.apply(project)
        project.psync {
            className = name
        }
        project.evaluate()

        // Register our task with the variant
        project.android.applicationVariants.all { ApplicationVariant variant ->
            Task task = project.tasks."generatePrefKeysFor${variant.name.capitalize()}"
            assertThat(task).isNotNull()

            PSyncTask syncTask = task as PSyncTask
            assertThat(syncTask.className).isEqualTo name

            syncTask.generate(TestHelper.getTaskInputs())

            assertThat(syncTask.outputDir.exists())
            assertThat("${syncTask.outputDir}/{${syncTask.packageName.replace('.', '/')}/${name}.java")
        }

        project.buildDir.deleteDir()
    }
}
