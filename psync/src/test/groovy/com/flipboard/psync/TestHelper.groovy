package com.flipboard.psync

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.testfixtures.ProjectBuilder

import java.lang.reflect.Modifier

final class TestHelper {

    public static Project evaluatableAppProject() {
        Project project = ProjectBuilder.builder().withProjectDir(new File(PsyncTest.FIXTURE_WORKING_DIR)).build()
        project.apply plugin: 'com.android.application'
        project.android {
            compileSdkVersion 23
            buildToolsVersion '23.0.0'

            defaultConfig {
                versionCode 1
                versionName '1.0'
                minSdkVersion 14
                targetSdkVersion 23
                applicationId 'com.flipboard.psync.test'
            }

            buildTypes {
                release {
                    signingConfig signingConfigs.debug
                }
            }
        }

        return project
    }

    public static Project evaluatableLibProject() {
        Project project = ProjectBuilder.builder().withProjectDir(new File(PsyncTest.FIXTURE_WORKING_DIR)).build()
        project.apply plugin: 'com.android.library'
        project.android {
            compileSdkVersion 23
            buildToolsVersion '23.0.0'

            defaultConfig {
                versionCode 1
                versionName '1.0'
                minSdkVersion 14
                targetSdkVersion 23
            }

            buildTypes {
                release {
                    signingConfig signingConfigs.debug
                }
            }
        }

        return project
    }

    public static IncrementalTaskInputs getTaskInputs() {
        return new IncrementalTaskInputs() {
            @Override
            boolean isIncremental() {
                return false
            }

            @Override
            void outOfDate(Action<? super InputFileDetails> action) {

            }

            @Override
            void removed(Action<? super InputFileDetails> action) {

            }
        }
    }

    public static boolean isPSF(int modifiers) {
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
    }
}
