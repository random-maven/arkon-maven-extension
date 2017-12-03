
### Arkon Maven Extension

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/mojohaus/versions-maven-plugin.svg?label=License)](http://www.apache.org/licenses/)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.carrotgarden.maven/arkon-maven-extension/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.carrotgarden.maven/arkon-maven-extension)
[![Bintray Download](https://api.bintray.com/packages/random-maven/maven/arkon-maven-extension/images/download.svg) ](https://bintray.com/random-maven/maven/arkon-maven-extension/_latestVersion)

Extension features
* downloads remote parent pom.xml
* early in maven project build
* operates on paths lists

### Usage example

1. Register [project extension](https://github.com/random-maven/arkon-maven-extension/blob/master/.mvn/extensions.xml)
```
${project}/.mvn/extensions.xml
```

2. Configure [extension settings](https://github.com/random-maven/arkon-maven-extension/blob/master/.mvn/arkon.props)
```
${project}/.mvn/arkon.props
```

3. Activate parent [in the project](https://github.com/random-maven/arkon-maven-extension/blob/master/pom.xml#L10)
```
${project}/pom.xml
```

Now, run a build, and find a parent in `.mvn`
```
mvn clean install
ls -las ${project}/.mvn/pom.xml
```    

### Sample parent repository

[random-maven/arkon](https://github.com/random-maven/arkon)
