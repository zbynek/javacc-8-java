/*
 * Copyright (c) 2020-2025, Sreeni Viswanadha <sreeni@viswanadha.net>.
 * Copyright (c) 2024-2025, Marc Mazas <mazas.marc@gmail.com>.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the names of the copyright holders nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.javacc.java;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.javacc.Version;
import org.javacc.jjtree.ASTNodeDescriptor;
import org.javacc.jjtree.JJTreeContext;
import org.javacc.jjtree.JJTreeGlobals;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Options;

final class NodeFiles {

  NodeFiles() {}

  private final Set<String> nodesToBuild = new TreeSet<>();

  void generateNodeType(final String nodeType) {
    if (!nodeType.equals("Tree") && !nodeType.equals("Node")) {
      nodesToBuild.add(nodeType);
    }
  }

  void generateOutputFiles(final JJTreeContext context) throws IOException {
    generateBaseNodes(context);
    generateTreeNodes(context);
    generateTreeConstants(context);
    generateVisitor(context);
    generateDefaultVisitor(context);
  }

  private static void generateBaseNodes(final JJTreeContext context) throws IOException {
    final CodeGeneratorSettings options = CodeGeneratorSettings.of(Options.getOptions());
    options.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
    options.set(
        "VISITOR_RETURN_TYPE_VOID",
        Boolean.valueOf(context.treeOptions().getVisitorReturnType().equals("void")));

    // interface
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
      jcb.setFile(new File(context.treeOptions().getJJTreeOutputDirectory(), "Tree.java"));
      jcb.setVersion(Version.version).addTools(JJTreeGlobals.toolName);
      jcb.addOption(
          "SUPPORT_CLASS_VISIBILITY_PUBLIC",
          "VISITOR",
          "VISITOR_DATA_TYPE",
          "VISITOR_EXCEPTION",
          "VISITOR_RETURN_TYPE");
      generateProlog(jcb);
      jcb.printTemplate("/templates/java/Tree.template");
    }

    // class
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
      jcb.setFile(new File(context.treeOptions().getJJTreeOutputDirectory(), "Node.java"));
      jcb.setVersion(Version.version).addTools(JJTreeGlobals.toolName);
      jcb.addOption(
          "NODE_EXTENDS",
          "NODE_FACTORY",
          "SUPPORT_CLASS_VISIBILITY_PUBLIC",
          "TRACK_TOKENS",
          "VISITOR",
          "VISITOR_DATA_TYPE",
          "VISITOR_EXCEPTION",
          "VISITOR_RETURN_TYPE",
          "VISITOR_RETURN_TYPE_VOID");
      generateProlog(jcb);
      jcb.printTemplate("/templates/java/Node.template");
    }
  }

  private void generateTreeNodes(final JJTreeContext context) {
    /* Options.getOptions() gets a copy of Options.resOptions, so the following non user options
     *  are not known by Options.fmtOptionsArray() when OutputFile.getPrintWriter() prints
     *  the options banner line and are output with a null value. */
    final CodeGeneratorSettings options = CodeGeneratorSettings.of(Options.getOptions());
    options.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
    options.set(
        "VISITOR_RETURN_TYPE_VOID",
        Boolean.valueOf(context.treeOptions().getVisitorReturnType().equals("void")));
    final File dir =
        new File(
            context.treeOptions().getNodeDirectory(),
            context.treeOptions().getNodePackage().replace('.', File.separatorChar));

    if (context.treeOptions().getSingleTreeFile()) {
      // one xxxTree.java file for all node classes, but they cannot be public
      try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
        jcb.setVersion(Version.version).addTools(JJTreeGlobals.toolName);
        jcb.addOption(
            "NODE_CLASS",
            "NODE_EXTENDS",
            "NODE_FACTORY",
            "NODE_PREFIX",
            "NODE_USES_PARSER",
            "TRACK_TOKENS",
            "VISITOR",
            "VISITOR_DATA_TYPE",
            "VISITOR_EXCEPTION",
            "VISITOR_METHOD_NAME_INCLUDES_TYPE_NAME",
            "VISITOR_RETURN_TYPE");
        jcb.setFile(
            new File(
                context.treeOptions().getJJTreeOutputDirectory(),
                JJTreeGlobals.parserName + "Tree.java"));
        generateProlog(jcb);
        jcb.println("/* ");
        jcb.println(
            " * Option SINGLE_TREE_FILE set to true produces this file containing the set of all generated node classes");
        jcb.println(" *  (those that are not user defined); it may be empty.");
        jcb.println(" */");
        jcb.println();
        for (final String node : nodesToBuild) {
          if (!new File(dir, node + ".java").exists()) {
            options.set("NODE_TYPE", node);
            jcb.printTemplate("/templates/java/MultiNode.template", options);
            jcb.println();
          } else {
            jcb.println("/* Node class " + node + " not generated as custom node class found. */");
            jcb.println();
          }
        }
      } catch (final IOException e) {
        throw new Error(e.toString());
      }

    } else {
      // one file per node class
      for (final String node : nodesToBuild) {
        if (!new File(dir, node + ".java").exists()) {
          try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
            jcb.setVersion(Version.version).addTools(JJTreeGlobals.toolName);
            jcb.addOption(
                "NODE_CLASS",
                "NODE_EXTENDS",
                "NODE_FACTORY",
                "NODE_PREFIX",
                "NODE_USES_PARSER",
                "TRACK_TOKENS",
                "VISITOR",
                "VISITOR_DATA_TYPE",
                "VISITOR_EXCEPTION",
                "VISITOR_METHOD_NAME_INCLUDES_TYPE_NAME",
                "VISITOR_RETURN_TYPE");
            options.set("NODE_TYPE", node);
            jcb.setFile(new File(context.treeOptions().getJJTreeOutputDirectory(), node + ".java"));
            generateProlog(jcb);
            jcb.printTemplate("/templates/java/SingleNode.template", options);
            jcb.println();
          } catch (final IOException e) {
            throw new Error(e.toString());
          }
        }
      }
    }
  }

  private static void generateTreeConstants(final JJTreeContext context) {
    final List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {
      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JavaTemplates.nodeConstants() + ".java"));
      generateProlog(jcb);

      jcb.println("public interface " + JavaTemplates.nodeConstants(), " {");
      jcb.println("");

      for (int i = 0; i < nodeIds.size(); ++i) {
        jcb.println("  public final int ", nodeIds.get(i), " = ", i, ";");
      }
      jcb.println();
      jcb.println("  public static String[] jjtNodeName = {");
      for (final String nodeName : nodeNames) {
        jcb.println("    \"", nodeName, "\",");
      }
      jcb.println("  };");
      jcb.println("}");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateVisitor(final JJTreeContext context) {
    if (!context.treeOptions().getVisitor()) {
      return;
    }

    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();
    final String ve = mergeVisitorException(context);
    String argumentType = "Object";
    if (!context.treeOptions().getVisitorDataType().equals("")) {
      argumentType = context.treeOptions().getVisitorDataType();
    }

    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {

      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JavaTemplates.visitorClass() + ".java"));
      generateProlog(jcb);
      jcb.println("public interface " + JavaTemplates.visitorClass() + " {");
      jcb.println(
          "  public ",
          context.treeOptions().getVisitorReturnType(),
          " visit(final Node node, ",
          argumentType,
          " data)",
          ve,
          ";");

      if (context.treeOptions().getMulti()) {
        for (final String n : nodeNames) {
          if (!n.equals("void")) {
            final String nodeType = context.treeOptions().getNodePrefix() + n;
            jcb.println(
                "  public ",
                context.treeOptions().getVisitorReturnType(),
                " ",
                getVisitMethodName(nodeType),
                "(final ",
                nodeType,
                " node, ",
                argumentType + " data)",
                ve,
                ";");
          }
        }
      }
      jcb.println("}");

    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateDefaultVisitor(final JJTreeContext context) {
    if (!context.treeOptions().getVisitor()) {
      return;
    }

    final String ve = mergeVisitorException(context);
    final String ret = context.treeOptions().getVisitorReturnType();
    String argumentType = "Object";
    if (!context.treeOptions().getVisitorDataType().equals("")) {
      argumentType = context.treeOptions().getVisitorDataType();
    }
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {
      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JavaTemplates.defaultVisitorClass() + ".java"));
      generateProlog(jcb);

      jcb.println(
          "public class ",
          JavaTemplates.defaultVisitorClass(),
          " implements ",
          JavaTemplates.visitorClass(),
          "{");

      jcb.println(
          "  public "
              + ret
              + " defaultVisit(final Node node, "
              + argumentType
              + " data)"
              + ve
              + " {");
      jcb.println("    node.childrenAccept(this, data);");
      jcb.println("    return" + (ret.trim().equals("void") ? "" : " data") + ";");
      jcb.println("  }");

      jcb.println(
          "  public " + ret + " visit(final Node node, " + argumentType + " data)" + ve + " {");
      jcb.println(
          "    " + (ret.trim().equals("void") ? "" : "return ") + "defaultVisit(node, data);");
      jcb.println("  }");

      if (context.treeOptions().getMulti()) {
        for (final String n : nodeNames) {
          if (n.equals("void")) {
            continue;
          }
          final String nodeType = context.treeOptions().getNodePrefix() + n;
          jcb.println(
              "  public ",
              ret,
              " ",
              getVisitMethodName(nodeType),
              "(final ",
              nodeType,
              " node, ",
              argumentType,
              " data)",
              ve,
              " {");
          jcb.println(
              "    ", (ret.trim().equals("void") ? "" : "return "), "defaultVisit(node, data);");
          jcb.println("  }");
        }
      }
      jcb.println("}");

    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static String mergeVisitorException(final JJTreeContext context) {
    String ve = context.treeOptions().getVisitorException();
    if (!"".equals(ve)) {
      ve = " throws " + ve;
    }
    return ve;
  }

  private static String getVisitMethodName(final String className) {
    final StringBuffer sb = new StringBuffer("visit");
    if (Options.booleanValue("VISITOR_METHOD_NAME_INCLUDES_TYPE_NAME")) {
      sb.append(Character.toUpperCase(className.charAt(0)));
      for (int i = 1; i < className.length(); i++) {
        sb.append(className.charAt(i));
      }
    }
    return sb.toString();
  }

  // Used when packageName & nodePackageName are different
  static void generateProlog(final JavaCodeBuilder jcb) {
    if (!JJTreeGlobals.nodePackageName.isEmpty()
        && !JJTreeGlobals.nodePackageName.equals(JJTreeGlobals.packageName)) {
      jcb.setPackageName(JJTreeGlobals.nodePackageName);
      jcb.addImportName(JJTreeGlobals.packageName + ".*");
    } else {
      jcb.setPackageName(JJTreeGlobals.packageName);
    }
  }
}
