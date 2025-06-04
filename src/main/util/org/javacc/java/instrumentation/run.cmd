Rem Small Windows script to build and run a utility for printing a class's static fields sizes
Rem Must be edited and tailored (jdk, paths, class)
Rem Marc Mazas - 06/2025

Rem JDK version must be at least the one used to compile the class to look at
Set JDK=%OraJDK_11%

cd ..\..\..\..\..\..\..
mkdir target\agent-classes

Rem compile java classes
"%JDK%/bin/javac" -d target\agent-classes src\main\util\org\javacc\java\instrumentation\*.java

Rem make a jar to be used as a java agent with the proper manifest
"%JDK%/bin/jar" cvmf src\main\util\org\javacc\java\instrumentation\MANIFEST.MF target\SFS_agent.jar ^
  -C target\agent-classes org\javacc\java\instrumentation\Agent.class

Rem run the utility: the classpath must include the class to look at,
Rem  and this one must be given as the argument (full qualified name)

rem "%JDK%/bin/java" -cp target\agent-classes ^
rem   -javaagent:"target\SFS_agent.jar" ^
rem   org.javacc.java.instrumentation.Example

"%JDK%/bin/java" -cp target\agent-classes;..\jsqlparser2\target\classes ^
  -javaagent:"target\SFS_agent.jar" ^
  org.javacc.java.instrumentation.StaticFieldsSizes ^
  net.sf.jsqlparser.parser.CCJSqlParserTokenManager

"%JDK%/bin/java" -cp target\agent-classes;..\jsqlparser~ss\target\classes ^
  -javaagent:"target\SFS_agent.jar" ^
  org.javacc.java.instrumentation.StaticFieldsSizes ^
  net.sf.jsqlparser.parser.CCJSqlParserTokenManager
  
"%JDK%/bin/java" -cp target\agent-classes;..\jsqlparser~ss\target\classes ^
  -javaagent:"target\SFS_agent.jar" ^
  org.javacc.java.instrumentation.StaticFieldsSizes ^
  net.sf.jsqlparser.parser.CCJSqlParserTokenManager$CharDataConsts

cd /d %~dp0