<!--
Copyright 2024 Serge Huber

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
# OSGi Bundle Analyzer Maven Plugin

The OSGi Bundle Analyzer Maven Plugin is a collection of utilities designed to help developers work with OSGi bundles in Maven projects. This plugin provides several goals to analyze and manage OSGi-related aspects of your project.

## Features

- Locate packages within project dependencies
- Analyze package usage in OSGi bundles
- View and validate OSGi bundle manifests

## Prerequisites

- Maven 3.3.9 or later
- Java 8 or later

## Usage

This plugin can be used directly from the command line. Some goals require a Maven project context, while others can be used both within a project and independently.

### Locate Packages

This goal must be run within a Maven project context:

```shell
mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:locate-package -Dpackage=com.example.package1
```


This goal scans your project's dependencies and reports where the specified package is found.

**Sample Output:**

```
📦 Package found in Project classes
Dependency trail:
├─ dev.inoyu:osgi-tools:1.0-SNAPSHOT
com/example/package1/Class1.class
com/example/package1/Class2.class
```


### Analyze Package Usage

This goal must also be run within a Maven project context:

```shell
mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:find-package-usages -Dpackage=com.example.package
```


This goal examines your project and its dependencies to find where the specified packages are used.

**Sample Output:**

```
Searching for usages of package: com.example.package
📦 Usage found in Dependency: org.example:example-artifact:1.0: com/example/usage/Class1 uses com.example.package.Class2
Dependency trail:
├─ org.example:example-artifact:1.0
```


### View Manifest

This goal can be used both within a Maven project context and independently:

1. Within a Maven project:

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:view-manifest
   ```

   This will display the manifest of the project's main artifact.

2. For arbitrary JAR files (can be used anywhere):

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:view-manifest -Djars=path/to/your/jar1.jar,path/to/your/jar2.jar
   ```

This goal displays the contents of the OSGi bundle manifest, including all headers and their values.

**Sample Output:**

```
Analyzing manifest of: path/to/your/jar1.jar
================================================================================
Bundle-Name: Example Bundle
Bundle-SymbolicName: com.example.bundle
Bundle-Version: 1.0.0
Export-Package: com.example.package;version="1.0.0"
Import-Package: org.osgi.framework;version="[1.3,2)"
================================================================================
```


## Parameters

The plugin supports the following parameters:

- `package`: The package name to search for or analyze (for `locate-package` and `find-package-usages` goals).
- `jars`: A comma-separated list of paths to JAR files to analyze (optional for `view-manifest` goal when used outside a project context).

## Examples

1. Locate a package in a Maven project:

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:locate-package -Dpackage=org.osgi.framework
   ```

2. Analyze package usage in a Maven project:

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:find-package-usages -Dpackage=com.example.api
   ```

3. View manifest of the current Maven project:

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:view-manifest
   ```

4. View manifests of multiple JARs (can be used anywhere):

   ```shell
   mvn dev.inoyu:osgi-analyzer-maven-plugin:1.0-SNAPSHOT:view-manifest -Djars=/path/to/bundle1.jar,/path/to/bundle2.jar
   ```

## Notes

- The `locate-package` and `find-package-usages` goals require a Maven project context and analyze the project's dependencies.
- The `view-manifest` goal can be used both within a Maven project (without additional parameters) and independently to analyze arbitrary JAR files using the `-Djars` parameter.

## Contributing

Contributions to the OSGi Tools Maven Plugin are welcome! Please submit pull requests or open issues on our GitHub repository.

## License

This project is licensed under the [Apache License 2.0](LICENSE).