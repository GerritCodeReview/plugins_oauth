Build
=====

This plugin is built with Bazel. To install Bazel, follow
the instruction on: https://www.bazel.io/versions/master/docs/install.html.

Two build modes are supported: Standalone and in Gerrit tree.
The standalone build mode is recommended, as this mode doesn't
require the Gerrit tree to exist locally.

### Packaged runtime JAR allowlist test

This plugin tracks the set of third-party runtime JARs that are bundled into the plugin JAR.
A deterministic, version-agnostic manifest is generated from the plugin’s runtime classpath and
compared against the checked-in allowlist:

`oauth_third_party_runtime_jars.allowlist.txt`

This acts as a guardrail to detect unintended changes to the packaged runtime dependency set.

To run the check (standalone or in Gerrit tree):

```bash
bazelisk test //:check_oauth_third_party_runtime_jars
```

#### Updating the allowlist

If the test fails because the packaged third-party JAR set changed, the plugin’s bundled runtime
dependencies have changed.

If the change is expected and has been reviewed, refresh the allowlist:

```bash
bazelisk build //:oauth_third_party_runtime_jars.txt
cp bazel-bin/oauth_third_party_runtime_jars.txt oauth_third_party_runtime_jars.allowlist.txt
```

Commit the updated allowlist along with the dependency change.

### Build standalone

Clone the plugin:

```
  git clone https://gerrit.googlesource.com/plugins/oauth
  cd oauth
```

Issue the command:

```
  bazel build :all
```

The output is created in

```
  bazel-bin/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.py
```

### Build in Gerrit tree

Clone or link this plugin to the plugins directory of Gerrit's
source tree, and issue the command:

```
  git clone https://gerrit.googlesource.com/gerrit
  git clone https://gerrit.googlesource.com/plugins/@PLUGIN@
  cd gerrit/plugins
  ln -s ../../@PLUGIN@ .
```

Load the plugin's Bazel module by adding the following lines to
Gerrit's root module:

```
bazel_dep(name = "gerrit-plugin-oauth")
local_path_override(
    module_name = "gerrit-plugin-oauth",
    path = "./plugins/oauth",
)
```

This will make the plugin's external dependencies available for
the build.

From Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run either one of:

```
  bazel test --test_tag_filters=@PLUGIN@ //...
  bazel test plugins/@PLUGIN@:@PLUGIN@_tests
```

This project can be imported into the Eclipse IDE.
Add the plugin name to the `CUSTOM_PLUGINS` set in
Gerrit core in `tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
