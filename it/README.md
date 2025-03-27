In this directory lie different xml files for building and running the integration tests,  
plus the local Maven repository for them.

The `pom.xml` is the parent of the poms in `issues`, `examples` and `grammars`.  
It holds the definitions and configurations of the plugins that are specific to the project  
(the definitions and configurations of the common plugins are in the javacc-8 parent project).

The `build-utils.xml` is an Ant script file holding different targets to be called by Maven.
