/*
 * Copyright 2015 Flipboard Inc
 */

package com.flipboard.psync
import com.google.common.collect.ImmutableList
import groovy.xml.QName
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import rx.Observable

/**
 * Task that generates P.java source files
 */
class PSyncTask extends SourceTask {

    static final ImmutableList<String> BOOL_TYPES = ImmutableList.of("true", "false")

    /**
     * The output directory.
     */
    @OutputDirectory
    File outputDir

    @Input
    String packageName

    @Input
    String className

    @Input
    boolean generateRx

    @TaskAction
    def generate(IncrementalTaskInputs inputs) {

        // If the whole thing isn't incremental, delete the build folder (if it exists)
        // TODO If they change the className, we should probably delete the old one for good measure if it exists
        if (!inputs.isIncremental() && outputDir.exists()) {
            logger.debug("PSync generation is not incremental; deleting build folder and starting fresh!")
            outputDir.deleteDir()
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        List<PrefEntry> entries = getPrefEntriesFromFiles(getSource()).toBlocking().first()

        PClassGenerator.generate(entries, packageName, outputDir, className, generateRx)
    }

    /**
     * Retrieves all the keys in the files in a given xml directory
     *
     * @param xmlDir Directory to search
     * @param fileRegex Regex for matching the files you want
     * @return Observable of all the distinct keys in this directory.
     */
    static Observable<List<PrefEntry>> getPrefEntriesFromFiles(Iterable<File> sources) {
        Observable.from(sources)                                                // Fetch the keys from each file
                .map {file -> new XmlParser().parse(file)}                      // Parse the file
                .flatMap {rootNode -> Observable.from(rootNode.depthFirst())}   // Extract all the nodes
                .map {Node node -> generatePrefEntry(node.attributes())}        // Generate PrefEntry objects from the attributes
                .filter {PrefEntry entry -> !entry.isBlank()}                   // Filter out ones we can't use
                .distinct()                                                     // Only want unique
                .toSortedList()                                                 // Output the sorted list
    }

    /**
     * Generates a {@link PrefEntry} from the given attributes on a Node
     *
     * @param attributes attributes on the node to parse
     * @return a generated PrefEntry, or {@link PrefEntry#BLANK} if we can't do anything with it
     */
    static PrefEntry generatePrefEntry(Map<QName, String> attributes) {
        PrefEntry entry
        String key = null
        String defaultValue = null

        // These are present for list-type preferences
        String entries = null
        String entryValues = null

        attributes.entrySet().each { Map.Entry<QName, String> attribute ->
            String name = attribute.key.localPart
            switch (name) {
                case "key":
                    key = attribute.value
                    break
                case "defaultValue":
                    defaultValue = attribute.value
                    break
                case "entries":
                    entries = attribute.value
                    break
                case "entryValues":
                    entryValues = attribute.value
                    break
            }
        }

        if (StringUtils.isEmpty(key)) {
            return PrefEntry.BLANK
        }

        boolean hasListAttributes = entries || entryValues

        if (defaultValue == null || defaultValue.length() == 0) {
            entry = PrefEntry.create(key, null)
        } else if (BOOL_TYPES.contains(defaultValue)) {
            entry = PrefEntry.create(key, Boolean.valueOf(defaultValue))
        } else if (NumberUtils.isNumber(defaultValue)) {
            entry = PrefEntry.create(key, Integer.valueOf(defaultValue))
        } else if (defaultValue.startsWith('@')) {
            entry = generateResourcePrefEntry(key, defaultValue)
            if (hasListAttributes && entry.resType == "string") {
                // Only string resource entries can be list preferences
                entry.markAsListPreference(entries, entryValues)
            }
        } else {
            entry = PrefEntry.create(key, defaultValue)
            if (hasListAttributes) {
                entry.markAsListPreference(entries, entryValues)
            }
        }

        return entry
    }

    /**
     * Resource PrefEntries are special, because we need to retrieve their resource ID.
     *
     * @param key Preference key
     * @param defaultValue String representation of the default value (e.g. "@string/hello")
     * @return PrefEntry object representing this, or {@link PrefEntry#BLANK} if we couldn't resolve its resource ID
     */
    static PrefEntry generateResourcePrefEntry(String key, String defaultValue) {
        String[] split = defaultValue.split('/')

        if (split == null || split.length < 2) {
            return PrefEntry.BLANK;
        }

        String resType = split[0].substring(1)
        String resId = split[1]
        return PrefEntry.create(key, resId, resType)
    }
}
