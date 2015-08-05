/*
 * Copyright 2015 Flipboard Inc
 */

package com.flipboard.psync

/**
 * Configuration values for the PSync plugin.
 */
class PSyncPluginExtension {

    /**
     * Ant-style includes pattern for identifying what files you want to include in the XML parsing.
     *
     * Note that this is relative to the res directory ('src/main/res')
     *
     * Default is to parse all files in the directory.
     */
    String includesPattern = "**/xml/*.xml"

    /**
     * Package name you want the generated class to be in. Default is to use the applicationID.
     *
     * REQUIRED for _library_ projects.
     */
    String packageName = null

    /**
     * Name you want for the generated class. Default is "P"
     */
    String className = "P"

    /**
     * Enable this to generate rx() methods for preference blocks, which uses
     * f2prateek's Rx-Preferences library. Note that this will fail compilation if you don't include
     * it as a dependency.
     *
     * See https://github.com/f2prateek/rx-preferences
     *
     * Default is false
     */
    boolean generateRx = false;

}
