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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.javacc.Version;
import org.javacc.jjtree.ASTNodeDescriptor;
import org.javacc.jjtree.JJTreeContext;
import org.javacc.jjtree.JJTreeGlobals;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Options;

final class NodeFiles {

  private NodeFiles() {}

  /** ID of the latest version (of JJTree) in which one of the Node classes was modified. */
  private static final String nodeVersion = Version.version;

  private static Set<String> nodesToBuild = new HashSet<>();

  static void generateNodeType(final String nodeType) {
    if (!nodeType.equals("Tree") && !nodeType.equals("Node")) {
      NodeFiles.nodesToBuild.add(nodeType);
    }
  }

  private static void generateTreeNodes(final JJTreeContext context) {
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {
      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JJTreeGlobals.parserName + "Tree.java"));
      jcb.addTools(JJTreeGlobals.toolName).setVersion(NodeFiles.nodeVersion);
      jcb.addOption(
          "MULTI",
          "NODE_USES_PARSER",
          "VISITOR",
          "TRACK_TOKENS",
          "NODE_PREFIX",
          "NODE_EXTENDS",
          "NODE_FACTORY",
          Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);
      NodeFiles.generateProlog(jcb);

      for (final String node : NodeFiles.nodesToBuild) {
        if (new File(
                new File(
                    context.treeOptions().getASTNodeDirectory(),
                    context.treeOptions().getNodePackage()),
                node + ".java")
            .exists()) {
          continue;
        }

        NodeFiles.generateMULTINode(jcb, node, context);
      }
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateMULTINode(
      final JavaCodeBuilder builder, final String nodeType, final JJTreeContext context)
      throws IOException {
    final CodeGeneratorSettings options = CodeGeneratorSettings.of(Options.getOptions());
    options.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
    options.set("NODE_TYPE", nodeType);
    options.set(
        "VISITOR_RETURN_TYPE_VOID",
        Boolean.valueOf(context.treeOptions().getVisitorReturnType().equals("void")));
    builder.printTemplate("/templates/java/MultiNode.template", options);
  }

  private static void generateTreeConstants(final JJTreeContext context) {
    final List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {
      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JavaTemplates.nodeConstants() + ".java"));
      NodeFiles.generateProlog(jcb);

      jcb.println("public interface " + JavaTemplates.nodeConstants(), " {");

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
    final String ve = NodeFiles.mergeVisitorException(context);
    String argumentType = "Object";
    if (!context.treeOptions().getVisitorDataType().equals("")) {
      argumentType = context.treeOptions().getVisitorDataType();
    }
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, CodeGeneratorSettings.create())) {
      jcb.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              JavaTemplates.visitorClass() + ".java"));
      NodeFiles.generateProlog(jcb);
      jcb.println("public interface " + JavaTemplates.visitorClass(), " {");
      jcb.println(
          "  public ",
          context.treeOptions().getVisitorReturnType(),
          " visit(Node node, ",
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
                NodeFiles.getVisitMethodName(nodeType),
                "(",
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

  private static void generateDefaultVisitor(final JJTreeContext context) {
    if (!context.treeOptions().getVisitor()) {
      return;
    }
    final String ve = NodeFiles.mergeVisitorException(context);
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
      NodeFiles.generateProlog(jcb);
      jcb.println(
          "public class ",
          JavaTemplates.defaultVisitorClass(),
          " implements ",
          JavaTemplates.visitorClass(),
          "{");
      jcb.println("  public ", ret, " defaultVisit(Node node, ", argumentType, " data)", ve, " {");
      jcb.println("    node.childrenAccept(this, data);");
      jcb.println("    return", (ret.trim().equals("void") ? "" : " data"), ";");
      jcb.println("  }");
      jcb.println("  public ", ret, " visit(Node node, ", argumentType, " data)", ve, " {");
      jcb.println(
          "    ", (ret.trim().equals("void") ? "" : "return "), "defaultVisit(node, data);");
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
              NodeFiles.getVisitMethodName(nodeType),
              "(",
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

  private static void generateDefaultNode(final JJTreeContext context) throws IOException {
    final CodeGeneratorSettings options = CodeGeneratorSettings.of(Options.getOptions());
    options.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
    options.set(
        "VISITOR_RETURN_TYPE_VOID",
        Boolean.valueOf(context.treeOptions().getVisitorReturnType().equals("void")));
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
      jcb.setFile(new File(context.treeOptions().getJJTreeOutputDirectory(), "Tree.java"));
      NodeFiles.generateProlog(jcb);
      jcb.printTemplate("/templates/java/Tree.template");
    }
    try (JavaCodeBuilder jcb = JavaCodeBuilder.of(context, options)) {
      jcb.setFile(new File(context.treeOptions().getJJTreeOutputDirectory(), "Node.java"));
      NodeFiles.generateProlog(jcb);
      jcb.printTemplate("/templates/java/Node.template");
    }
  }

  // Using when packageName & nodePackageName are different
  static void generateProlog(final JavaCodeBuilder jcb) {
    if (!JJTreeGlobals.nodePackageName.isEmpty()
        && !JJTreeGlobals.nodePackageName.equals(JJTreeGlobals.packageName)) {
      jcb.setPackageName(JJTreeGlobals.nodePackageName);
      jcb.addImportName(JJTreeGlobals.packageName + ".*");
    } else {
      jcb.setPackageName(JJTreeGlobals.packageName);
    }
  }

  static void generateOutputFiles(final JJTreeContext context) throws IOException {
    NodeFiles.generateDefaultNode(context);
    if (!NodeFiles.nodesToBuild.isEmpty()) {
      NodeFiles.generateTreeNodes(context);
    }
    NodeFiles.generateTreeConstants(context);
    NodeFiles.generateVisitor(context);
    NodeFiles.generateDefaultVisitor(context);
  }
}
