# PSync

PSync is a gradle plugin for android projects to generate Java representations of xml preferences.

Some applications have a lot of preferences, each their own keys, default values, and more. These tend to be
 stored in xml files (under `res/xml`), and don't have any programmatic linking of their values. The result?
 You have to *manually* keep these values in sync with your Java code. Yikes!

We got tired of dealing with this at Flipboard. Our preference class ended up with 200+ lines of boilerplate
at the top that we manually had to keep in sync, and it was becoming a nuisance. PSync was developed
to resolve this, and we hope it helps you too!

## Setup

Apply the PSync plugin to your module *below* your android plugin application

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.com.flipboard:psync:1.1.5"
  }
}

apply plugin: 'com.android.application' // Can be library too
apply plugin: 'com.flipboard.psync'
```

The PSync plugin will create a generating task for each of your variants, and will generate a file
at compilation that will be included in your classpath. This process is purposefully very similar to
how the `R.java` file works. The default name for this class is `P.java`, but you can configure it to
be another name if you wish.

Speaking of configuration, here's how you can configure PSync to work for you.

```groovy
psync {
    className = "MyClassName"
    includesPattern = "**/xml/<mypatternforfiles>.xml"
    packageName = "com.example.myapp"
    generateRx = true
}
```

Let's take a look at each:

**className** is the name of the class. The default is just `P`.

**includesPattern** is an ant-style includes pattern for preference xml files in your resources. This
is useful if you have a lot of other xml files and want to save the plugin a little work by filtering
out non-pref ones. In Flipboard, all our preference files are prefixed with `prefs_`, so our includes
pattern would be `**/xml/prefs_*.xml`. The default is to parse all xml files in `xml` resource directories.

**packageName** is what you want to use for the package name of the generated resource classes. The
default behavior of the plugin is to retrieve this from your `variant`'s `applicationId` value. **NOTE:**
Library projects **MUST** specify this, since they don't have `applicationId` values.

**generateRx** is a flag indicating whether or not you want code generated for usage with [Rx-Preferences](https://github.com/f2prateek/rx-preferences),
which is a great library that adds reactive bindings around SharedPreferences

## Usage

Using the generated file is easy, and should feel very familiar to how you would use `R.java`.

Each preference is represented by a static inner class, with a `key` field, a `defaultResId` field if
it's a resource, and some or all of following functions:
* `defaultValue()` for retrieving the default value
* `get()` for retrieving the current stored value
* `put()` for storing a new value
  * NOTE: This returns an `Editor` instance, where you much `apply` or `commit` the change(s)
* `rx()` for retrieving an appropriate `Rx-Preferences` instance of `Preference`, if you enabled `generateRx` above.

These functions are generated when appropriate. If no default value or resource reference is specified,
the plugin will not try to guess the type and generate code for it.

First thing's first: Initialize your P.java file in your Application's `onCreate()` method.

```java
public class MyApp extends Application {

    @Override
    public void onCreate() {

        // Required
        P.init(this);

        // Optional
        // By default, it will initialize its internal SharedPreferences instance to the system default
        // You can change its used instance at any time
        P.setSharedPreferences(myOtherInstance);
    }

}
```

Let's take a look at an example. The following xml preference:

```xml
<CheckboxPreference
    android:key="show_images"
    android:defaultValue="true"
    />
```

Becomes this Java code:

```java
public final class P {
    public static final class showImages {
        public static final String key = "show_images";

        public static final boolean defaultValue() {
            return true;
        }

        public static final boolean get() {
            return PREFERENCES.getBoolean(key, defaultValue());
        }

        public static final SharedPreferences.Editor put(final boolean val) {
            return PREFERENCES.edit().putBoolean(key, val);
        }

        public static final Preference<Boolean> rx() {
            return RX_SHARED_PREFERENCES.getBoolean(key);
        }
    }
}
```

You can now reference this in code like so:

```java
String theKey = P.showImages.key;
boolean theDefault = P.showImages.defaultValue();
boolean current = P.showImages.get();
P.showImages.put(false).apply();

// If you use Rx-Preferences
P.showImages.rx().asObservable().omgDoRxStuff!
```

Nice and simple right? Note that the entry block's name will be a camelCaseLower conversion of the key.

Let's look at a resource example now. The following preference:

```xml
<Preference
    android:key="server_url"
    android:defaultValue="@string/server_url"
    />
```

Becomes the following Java code:

```java
public final class P {
    public static final class serverUrl {
        public static final String key = "server_url";
        public static final int defaultResId = R.string.server_url;

        public static final String defaultValue() {
            return RESOURCES.getString(defaultResId);
        }

        public static final String get() {
            return PREFERENCES.getString(key, defaultValue());
        }

        public static final SharedPreferences.Editor put(final String val) {
            return PREFERENCES.edit().putString(key, val);
        }

        public static final Preference<String> rx() {
            return RX_SHARED_PREFERENCES.getString(key);
        }
    }
}
```

Notice that resources are handled a little differently. This is intentional, and for convenience. Using
this code now looks like this:

```java
String theKey = P.serverUrl.key;
int theResId = P.serverUrl.defaultResId;
String theDefault = P.serverUrl.defaultValue();
String currentValue = P.serverUrl.get();
P.serverUrl.put("https://example.com").apply();

// If you use Rx-Preferences
P.serverUrl.rx().asObservable().omgDoRxStuff!
```

Easy peasy. Enjoy!

## Contributing
We welcome pull requests for bug fixes, new features, and improvements to PSync. Contributors
to PSync repository must accept Flipboard's Apache-style
[Individual Contributor License Agreement (CLA)](https://docs.google.com/forms/d/1gh9y6_i8xFn6pA15PqFeye19VqasuI9-bGp_e0owy74/viewform)
before any changes can be merged.
