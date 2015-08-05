/*
 * Copyright 2015 Flipboard Inc
 */

package com.flipboard.psync

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

class PSyncPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create('psync', PSyncPluginExtension)

        project.afterEvaluate {

            // Make sure there's an android configuration
            if (!project.android) {
                throw new IllegalStateException('Must apply \'com.android.application\' or \'com.android.library\' first!')
            }

            // Determine our variants and what type of project we're in
            //noinspection GroovyUnusedAssignment
            def variants = null
            def isApp = false;
            if (project.android.hasProperty('applicationVariants')) {
                variants = project.android.applicationVariants
                isApp = true
            } else if (project.android.hasProperty('libraryVariants')) {
                variants = project.android.libraryVariants
            } else {
                throw new IllegalStateException('Android project must have applicationVariants or libraryVariants!')
            }

            // Determine the package name
            String resolvedPackageName = project.psync.packageName
            if (!resolvedPackageName) {
                if (isApp) {
                    resolvedPackageName = project.android.defaultConfig.applicationId
                } else {
                    throw new IllegalStateException('You must specify a package name for library projects!')
                }

            }

            String includesPattern = project.psync.includesPattern

            // Register our task with the variant
            variants.all { BaseVariant variant ->

                PSyncTask psyncTask = (PSyncTask) project.task(type: PSyncTask, "generatePrefKeysFor${variant.name.capitalize()}") {
                    source = variant.getSourceSets().collect { it.getResDirectories() }
                    include includesPattern
                    outputDir = project.file("$project.buildDir/generated/source/psync/$variant.flavorName/$variant.buildType.name/")
                    packageName = resolvedPackageName
                    className = project.psync.className
                    generateRx = project.psync.generateRx
                }

                variant.registerJavaGeneratingTask(psyncTask, (File) psyncTask.outputDir)
            }
        }
    }
}
