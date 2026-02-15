Build
=====

This plugin is built with Bazel. To install Bazel, follow
the instruction on: https://www.bazel.io/versions/master/docs/install.html.

Two build modes are supported: Standalone and in Gerrit tree.
The standalone build mode is recommended, as this mode doesn't
require the Gerrit tree to exist locally.

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

### Gerrit-tree-only plugin checks

This plugin contains additional guardrail tests that are meaningful only
when it is built inside the Gerrit source tree (e.g. checks comparing the
plugin’s packaged runtime jars against Gerrit’s own runtime classpath).

These tests are intentionally skipped in standalone plugin builds.

To run them, ensure the following is set in Gerrit's `.bazelrc`:

```
common --@com_googlesource_gerrit_bazlets//flags:in_gerrit_tree=true
```

Then execute:

```
bazel test plugins/@PLUGIN@:oauth_no_overlap_with_gerrit
```

Standalone plugin workspaces should not set this flag. In that case, the
corresponding targets are marked incompatible and reported as SKIPPED by Bazel.

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
