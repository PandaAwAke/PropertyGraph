# PropertyGraph
A library to generate Abstract Syntax Tree, Control Flow Graph and Program Dependency Graph for Java programs.

This repository is a modified version of https://github.com/YoshikiHigo/TinyPDG and
https://github.com/Zanbrachrissik/PropertyGraph.
I also added javadocs for the original project. 

Note: **this project is for personal use**. Sorry for this, but I don't have enough
time to maintain this.

## Environment
JDK version 8+.
## Generate PropertyGraph
```
Main class: com.tinypdg.graphToDot.Write
```

#### Usage
```
$ cd out/artifacts/PropertyGraph_jar
$ java -jar PropertyGraph.jar [-d <projectPath>] [-p] [-c] [-a]
-d projectPath  
-p: choose to generate PDG
-c: choose to generate CFG
-a: choose to generate AST
```
**Example**

`java -jar PropertyGraph.jar -d test/src -p -c -a`
