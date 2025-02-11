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
 *     * Neither the names of of the copyright holders nor the names of its
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.javacc.parser.Action;
import org.javacc.parser.BNFProduction;
import org.javacc.parser.Choice;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.CodeProduction;
import org.javacc.parser.Context;
import org.javacc.parser.Expansion;
import org.javacc.parser.JavaCCGlobals;
import org.javacc.parser.JavaCCParserConstants;
import org.javacc.parser.JavaCodeProduction;
import org.javacc.parser.Lookahead;
import org.javacc.parser.MetaParseException;
import org.javacc.parser.NonTerminal;
import org.javacc.parser.NormalProduction;
import org.javacc.parser.OneOrMore;
import org.javacc.parser.Options;
import org.javacc.parser.ParserData;
import org.javacc.parser.RegularExpression;
import org.javacc.parser.Semanticize;
import org.javacc.parser.Sequence;
import org.javacc.parser.Token;
import org.javacc.parser.TryBlock;
import org.javacc.parser.ZeroOrMore;
import org.javacc.parser.ZeroOrOne;
import org.javacc.utils.CodeBuilder;
import org.javacc.utils.CodeBuilder.GenericCodeBuilder;

/** Generate the parser. */
class ParserCodeGenerator implements org.javacc.parser.ParserCodeGenerator {

  /*
   * These lists are used to maintain expansions for which code generation in phase 2 and phase 3
   *  is required.
   * Whenever a call is generated to a phase 2 or phase 3 routine, a corresponding entry is added
   *  here if it has not already been added.
   * The phase 3 routines have been optimized in version 0.7pre2.
   * Essentially only those methods (and only those portions of these methods) are generated
   *  that are required.
   * The lookahead amount is used to determine this.
   * This change requires the use of a hash table because it is now possible for the same phase 3
   *  routine to be requested multiple times with different lookaheads.
   * The hash table provides a easily searchable capability to determine the previous requests.
   * The phase 3 routines nExpressionTreeConstantsow are performed in a two step process:
   *  - the first step gathers the requests (replacing requests with lower lookaheads with those
   *     requiring larger lookaheads),
   *  - the second step then generates these methods.
   */

  private final List<Lookahead> phase2list = new ArrayList<>();
  private final List<Phase3Data> phase3list = new ArrayList<>();
  private boolean jj2LA;
  private final Hashtable<Expansion, Phase3Data> phase3table = new Hashtable<>();

  private final Context context;
  private final Map<Expansion, String> internalNames = new HashMap<>();
  private final Map<Expansion, Integer> internalIndexes = new HashMap<>();

  private GenericCodeBuilder gcb;

  ParserCodeGenerator(final Context context) {
    this.context = context;
  }

  @Override
  public void generateCode(final CodeGeneratorSettings settings, final ParserData parserData) {

    gcb = GenericCodeBuilder.of(context, settings);
    gcb.setFile(new File(Options.getOutputDirectory(), context.globals().cu_name + ".java"));

    context.globals().lookaheadNeeded = false;
    final boolean isJavaModernMode =
        Options.getJavaTemplateType().equals(Options.UOV__JAVA_TEMPLATE_TYPE__MODERN);

    Token t = null; // used to scan user code in PARSER_BEGIN - PARSER_END section

    if (context.errors().get_error_count() != 0) {
      throw new RuntimeException(new MetaParseException());
    }

    if (Options.getBuildParser()) {
      final List<String> tn = new ArrayList<>(context.globals().toolNames);
      tn.add(JavaCCGlobals.toolName);

      boolean implementsExists = false;

      if (context.globals().cu_to_insertion_point_1.size() != 0) {
        final Object firstToken = context.globals().cu_to_insertion_point_1.get(0);
        gcb.printTokenSetup((Token) firstToken);
        for (final Iterator<Token> it = context.globals().cu_to_insertion_point_1.iterator();
            it.hasNext(); ) {
          t = it.next();
          if (t.kind == JavaCCParserConstants.IMPLEMENTS) {
            implementsExists = true;
          } else if (t.kind == JavaCCParserConstants.CLASS) {
            implementsExists = false;
          }
          gcb.printToken(t);
        }
      }

      // copy other stuff
      Token t1 = context.globals().otherLanguageDeclTokenBeg;
      final Token t2 = context.globals().otherLanguageDeclTokenEnd;

      if (t1 != null) {
        while (t1.kind != JavaCCParserConstants.LBRACE) {
          gcb.printToken(t1);

          if (t1.kind == JavaCCParserConstants.IMPLEMENTS) {
            implementsExists = true;
          } else if (t1.kind == JavaCCParserConstants.CLASS) {
            implementsExists = false;
          }
          t1 = t1.next;
        }
      }

      if (implementsExists) {
        gcb.print(", ");
      } else {
        gcb.print(" implements ");
      }
      gcb.print(context.globals().cu_name + "Constants ");

      if (t1 != null) {
        while (t1.next != t2) {
          gcb.printToken(t1);
          t1 = t1.next;
        }
      }

      if (context.globals().cu_to_insertion_point_2.size() != 0) {
        gcb.printTokenSetup(context.globals().cu_to_insertion_point_2.get(0));
        for (final Token token : context.globals().cu_to_insertion_point_2) {
          gcb.printToken(token);
        }
      }

      gcb.println();
      gcb.println();
      gcb.println("  /* Generated code for user productions */");
      gcb.println();

      build();
      gcb.println("  /* Base code  */");
      gcb.println();

      if (Options.getStatic()) {
        gcb.println(
            "  private static " + JavaUtil.getBooleanType() + " jj_initialized_once = false;");
      }
      if (Options.getUserTokenManager()) {
        gcb.println("  /** User defined Token Manager. */");
        gcb.println("  public " + JavaUtil.getStatic() + "TokenManager token_source;");
      } else {
        gcb.println("  /** Generated TokenManager. */");
        gcb.println(
            "  public "
                + JavaUtil.getStatic()
                + context.globals().cu_name
                + "TokenManager token_source;");
        if (!Options.getUserCharStream()) {
          if (Options.getJavaUnicodeEscape()) {
            gcb.println("  " + JavaUtil.getStatic() + "JavaCharStream jj_input_stream;");
          } else {
            gcb.println("  " + JavaUtil.getStatic() + "SimpleCharStream jj_input_stream;");
          }
        }
      }
      gcb.println("  /** Current token. */");
      gcb.println("  public " + JavaUtil.getStatic() + "Token token;");
      gcb.println("  /** Next token. */");
      gcb.println("  public " + JavaUtil.getStatic() + "Token jj_nt;");
      if (!Options.getCacheTokens()) {
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_ntk;");
      }
      if (Options.getDepthLimit() > 0) {
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_depth;");
      }
      if (context.globals().jj2index != 0) {
        gcb.println("  private " + JavaUtil.getStatic() + "Token jj_scanpos, jj_lastpos;");
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_la;");
        if (context.globals().lookaheadNeeded) {
          gcb.println("  /** Whether we are looking ahead. */");
          gcb.println(
              "  private "
                  + JavaUtil.getStatic()
                  + JavaUtil.getBooleanType()
                  + " jj_lookingAhead = false;");
          gcb.println(
              "  private " + JavaUtil.getStatic() + JavaUtil.getBooleanType() + " jj_semLA;");
        }
      }
      if (Options.getErrorReporting()) {
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_gen;");
        gcb.println(
            "  private "
                + JavaUtil.getStatic()
                + "final int[] jj_la1 = new int["
                + context.globals().maskindex
                + "];");
        final int tokenMaskSize = ((context.globals().tokenCount - 1) / 32) + 1;
        for (int i = 0; i < tokenMaskSize; i++) {
          gcb.println("  private static int[] jj_la1_" + i + ";");
        }
        gcb.println();

        gcb.println("  static {");
        for (int i = 0; i < tokenMaskSize; i++) {
          gcb.println("    jj_la1_init_" + i + "();");
        }
        gcb.println("  }");
        for (int i = 0; i < tokenMaskSize; i++) {
          gcb.println();
          gcb.println("  private static void jj_la1_init_" + i + "() {");
          gcb.print("    jj_la1_" + i + " = new int[] {");
          for (final int[] tokenMask : context.globals().maskVals) {
            gcb.print("0x" + Integer.toHexString(tokenMask[i]) + ", ");
          }
          gcb.println("};");
          gcb.println("  }");
        }
      }
      if ((context.globals().jj2index != 0) && Options.getErrorReporting()) {
        gcb.println();
        gcb.println(
            "  private "
                + JavaUtil.getStatic()
                + "final JJCalls[] jj_2_rtns = new JJCalls["
                + context.globals().jj2index
                + "];");
        gcb.println(
            "  private "
                + JavaUtil.getStatic()
                + JavaUtil.getBooleanType()
                + " jj_rescan = false;");
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_gc = 0;");
      }
      gcb.println();

      if (Options.getDebugParser()) {
        gcb.println("  {");
        gcb.println("    enable_tracing();");
        gcb.println("  }");
        gcb.println();
      }

      if (!Options.getUserTokenManager()) {
        if (Options.getUserCharStream()) {
          gcb.println();
          gcb.println("  /** Constructor with user supplied CharStream. */");
          gcb.println("  public " + context.globals().cu_name + "(CharStream stream) {");
          if (Options.getStatic()) {
            gcb.println("    if (jj_initialized_once) {");
            gcb.println(
                "      System.out.println(\"ERROR: Second call to constructor of static parser.  \");");
            gcb.println(
                "      System.out.println(\"     You must either use ReInit() "
                    + "or set the JavaCC option STATIC\");");
            gcb.println("      System.out.println(\"     to false during parser generation.\");");
            gcb.println(
                "      throw new "
                    + (Options.getLegacyExceptionHandling() ? "Error" : "RuntimeException")
                    + "();");
            gcb.println("    }");
            gcb.println("    jj_initialized_once = true;");
          }
          if (Options.getTokenManagerUsesParser()) {
            gcb.println(
                "    token_source = new "
                    + context.globals().cu_name
                    + "TokenManager(this, stream);");
          } else {
            gcb.println(
                "    token_source = new " + context.globals().cu_name + "TokenManager(stream);");
          }
          gcb.println("    token = new Token();");
          if (Options.getCacheTokens()) {
            gcb.println("    token.next = jj_nt = token_source.getNextToken();");
          } else {
            gcb.println("    jj_ntk = -1;");
          }
          if (Options.getDepthLimit() > 0) {
            gcb.println("      jj_depth = -1;");
          }
          if (Options.getErrorReporting()) {
            gcb.println("  jj_gen = 0;");
            if (context.globals().maskindex > 0) {
              gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
              gcb.println("      jj_la1[i] = -1;");
              gcb.println("    }");
            }
            if (context.globals().jj2index != 0) {
              gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
              gcb.println("      jj_2_rtns[i] = new JJCalls();");
              gcb.println("    }");
            }
          }
          gcb.println("  }");
          gcb.println();

          gcb.println("  /** Reinitialise. */");
          gcb.println("  public " + JavaUtil.getStatic() + "void ReInit(CharStream stream) {");

          if (Options.doesTokenManagerRequireParserAccess()) {
            gcb.println("    token_source.ReInit(this,stream);");
          } else {
            gcb.println("    token_source.ReInit(stream);");
          }

          gcb.println("    token = new Token();");
          if (Options.getCacheTokens()) {
            gcb.println("    token.next = jj_nt = token_source.getNextToken();");
          } else {
            gcb.println("    jj_ntk = -1;");
          }
          if (Options.getDepthLimit() > 0) {
            gcb.println("    jj_depth = -1;");
          }
          if (context.globals().lookaheadNeeded) {
            gcb.println("    jj_lookingAhead = false;");
          }
          if (context.globals().jjtreeGenerated) {
            gcb.println("    jjtree.reset();");
          }
          if (Options.getErrorReporting()) {
            gcb.println("    jj_gen = 0;");
            if (context.globals().maskindex > 0) {
              gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
              gcb.println("      jj_la1[i] = -1;");
              gcb.println("    }");
            }
            if (context.globals().jj2index != 0) {
              gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
              gcb.println("      jj_2_rtns[i] = new JJCalls();");
              gcb.println("    }");
            }
          }
          gcb.println("  }");
        } else {

          if (!isJavaModernMode) {
            gcb.println("  /** Constructor with InputStream. */");
            gcb.println("  public " + context.globals().cu_name + "(java.io.InputStream stream) {");
            gcb.println("    this(stream, null);");
            gcb.println("  }");
            gcb.println();

            gcb.println("  /** Constructor with InputStream and supplied encoding. */");
            gcb.println(
                "  public "
                    + context.globals().cu_name
                    + "(java.io.InputStream stream, String encoding) {");
            if (Options.getStatic()) {
              gcb.println("    if (jj_initialized_once) {");
              gcb.println(
                  "      System.out.println(\"ERROR: Second call to constructor of static parser.  \");");
              gcb.println(
                  "      System.out.println(\"     You must either use ReInit() or "
                      + "set the JavaCC option STATIC\");");
              gcb.println("      System.out.println(\"     to false during parser generation.\");");
              gcb.println(
                  "      throw new "
                      + (Options.getLegacyExceptionHandling() ? "Error" : "RuntimeException")
                      + "();");
              gcb.println("    }");
              gcb.println("    jj_initialized_once = true;");
            }

            if (Options.getJavaUnicodeEscape()) {
              gcb.println("    try {");
              gcb.println("      jj_input_stream = new JavaCharStream(stream, encoding, 1, 1);");
              gcb.println("    } catch (java.io.UnsupportedEncodingException e) {");
              if (!Options.getGenerateChainedException()) {
                gcb.println("      throw new RuntimeException(e.getMessage());");
              } else {
                gcb.println("      throw new RuntimeException(e);");
              }
              gcb.println("    }");
            } else {
              gcb.println("    try {");
              gcb.println("      jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1);");
              gcb.println("    } catch (java.io.UnsupportedEncodingException e) {");
              if (!Options.getGenerateChainedException()) {
                gcb.println("      throw new RuntimeException(e.getMessage());");
              } else {
                gcb.println("      throw new RuntimeException(e);");
              }
              gcb.println("    }");
            }
            if (Options.getTokenManagerUsesParser() && !Options.getStatic()) {
              gcb.println(
                  "    token_source = new "
                      + context.globals().cu_name
                      + "TokenManager(this, jj_input_stream);");
            } else {
              gcb.println(
                  "   token_source = new "
                      + context.globals().cu_name
                      + "TokenManager(jj_input_stream);");
            }
            gcb.println("    token = new Token();");
            if (Options.getCacheTokens()) {
              gcb.println("    token.next = jj_nt = token_source.getNextToken();");
            } else {
              gcb.println("    jj_ntk = -1;");
            }
            if (Options.getDepthLimit() > 0) {
              gcb.println("      jj_depth = -1;");
            }
            if (Options.getErrorReporting()) {
              gcb.println("    jj_gen = 0;");
              if (context.globals().maskindex > 0) {
                gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
                gcb.println("      jj_la1[i] = -1;");
                gcb.println("    }");
              }
              if (context.globals().jj2index != 0) {
                gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
                gcb.println("      jj_2_rtns[i] = new JJCalls();");
                gcb.println("    }");
              }
            }
            gcb.println("  }");
            gcb.println();

            gcb.println("  /** Reinitialise. */");
            gcb.println(
                "  public " + JavaUtil.getStatic() + "void ReInit(java.io.InputStream stream) {");
            gcb.println("    ReInit(stream, null);");
            gcb.println("  }");
            gcb.println();

            gcb.println();
            gcb.println("  /** Reinitialise. */");
            gcb.println(
                "  public "
                    + JavaUtil.getStatic()
                    + "void ReInit(java.io.InputStream stream, String encoding) {");

            gcb.println("    try { ");
            gcb.println("      jj_input_stream.ReInit(stream, encoding, 1, 1);  ");
            gcb.println("    } catch (java.io.UnsupportedEncodingException e) { ");
            if (!Options.getGenerateChainedException()) {
              gcb.println("      throw new RuntimeException(e.getMessage());");
            } else {
              gcb.println("      throw new RuntimeException(e);");
            }
            gcb.println("    }");

            if (Options.doesTokenManagerRequireParserAccess()) {
              gcb.println("    token_source.ReInit(this,jj_input_stream);");
            } else {
              gcb.println("    token_source.ReInit(jj_input_stream);");
            }

            gcb.println("    token = new Token();");
            if (Options.getCacheTokens()) {
              gcb.println("    token.next = jj_nt = token_source.getNextToken();");
            } else {
              gcb.println("    jj_ntk = -1;");
            }
            if (Options.getDepthLimit() > 0) {
              gcb.println("    jj_depth = -1;");
            }
            if (context.globals().jjtreeGenerated) {
              gcb.println("    jjtree.reset();");
            }
            if (Options.getErrorReporting()) {
              gcb.println("    jj_gen = 0;");
              gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
              gcb.println("      jj_la1[i] = -1;");
              gcb.println("    }");
              if (context.globals().jj2index != 0) {
                gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
                gcb.println("      jj_2_rtns[i] = new JJCalls();");
                gcb.println("    }");
              }
            }
            gcb.println("  }");
            gcb.println();
          }

          final String readerInterfaceName = isJavaModernMode ? "Provider" : "java.io.Reader";
          final String stringReaderClass =
              isJavaModernMode ? "StringProvider" : "java.io.StringReader";

          gcb.println("  /** Constructor. */");
          gcb.println(
              "  public " + context.globals().cu_name + "(" + readerInterfaceName + " stream) {");
          if (Options.getStatic()) {
            gcb.println("    if (jj_initialized_once) {");
            gcb.println(
                "      System.out.println(\"ERROR: Second call to constructor of static parser. \");");
            gcb.println(
                "      System.out.println(\"     You must either use ReInit() or "
                    + "set the JavaCC option STATIC\");");
            gcb.println("      System.out.println(\"     to false during parser generation.\");");
            gcb.println(
                "      throw new "
                    + (Options.getLegacyExceptionHandling() ? "Error" : "RuntimeException")
                    + "();");
            gcb.println("    }");
            gcb.println("    jj_initialized_once = true;");
          }
          if (Options.getJavaUnicodeEscape()) {
            gcb.println("    jj_input_stream = new JavaCharStream(stream, 1, 1);");
          } else {
            gcb.println("    jj_input_stream = new SimpleCharStream(stream, 1, 1);");
          }
          if (Options.getTokenManagerUsesParser() && !Options.getStatic()) {
            gcb.println(
                "    token_source = new "
                    + context.globals().cu_name
                    + "TokenManager(this, jj_input_stream);");
          } else {
            gcb.println(
                "    token_source = new "
                    + context.globals().cu_name
                    + "TokenManager(jj_input_stream);");
          }
          gcb.println("    token = new Token();");
          if (Options.getCacheTokens()) {
            gcb.println("    token.next = jj_nt = token_source.getNextToken();");
          } else {
            gcb.println("    jj_ntk = -1;");
          }
          if (Options.getDepthLimit() > 0) {
            gcb.println("    jj_depth = -1;");
          }
          if (Options.getErrorReporting()) {
            gcb.println("    jj_gen = 0;");
            if (context.globals().maskindex > 0) {
              gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
              gcb.println("      jj_la1[i] = -1;");
              gcb.println("    }");
            }
            if (context.globals().jj2index != 0) {
              gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
              gcb.println("      jj_2_rtns[i] = new JJCalls();");
              gcb.println("    }");
            }
          }
          gcb.println("  }");
          gcb.println();

          // Add-in a string based constructor because its convenient
          //  (modern only to prevent regressions)
          if (isJavaModernMode) {
            gcb.println("  /** Constructor (modern template). */");
            gcb.println(
                "  public "
                    + context.globals().cu_name
                    + "(String s) throws ParseException, "
                    + JavaTemplates.getTokenMgrErrorClass()
                    + " {");
            gcb.println("    this(new " + stringReaderClass + "(s));");
            gcb.println("  }");
            gcb.println();

            gcb.println("  /** Reinitialise (modern template). */");
            gcb.println("  public void ReInit(String s) {");
            gcb.println("    ReInit(new " + stringReaderClass + "(s));");
            gcb.println("  }");
            gcb.println();

            gcb.println("  /** Constructor (modern template). */");
            gcb.println(
                "  public "
                    + context.globals().cu_name
                    + "(java.io.InputStream is) throws ParseException, "
                    + JavaTemplates.getTokenMgrErrorClass()
                    + ",");
            gcb.println("                                    java.io.IOException {");
            gcb.println("    this(new StreamProvider(is));");
            gcb.println("  }");
            gcb.println();

            gcb.println("  /** Reinitialise (modern template). */");
            gcb.println("  public void ReInit(java.io.InputStream is) throws java.io.IOException {");
            gcb.println("    ReInit(new StreamProvider(is));");
            gcb.println("  }");
          }
          gcb.println();

          gcb.println("  /** Reinitialise. */");
          gcb.println(
              "  public "
                  + JavaUtil.getStatic()
                  + "void ReInit("
                  + readerInterfaceName
                  + " reader) {");
          if (Options.getJavaUnicodeEscape()) {
            gcb.println("    if (jj_input_stream == null) {");
            gcb.println("      jj_input_stream = new JavaCharStream(reader, 1, 1);");
            gcb.println("    } else {");
            gcb.println("      jj_input_stream.ReInit(reader, 1, 1);");
            gcb.println("    }");
          } else {
            gcb.println("    if (jj_input_stream == null) {");
            gcb.println("      jj_input_stream = new SimpleCharStream(reader, 1, 1);");
            gcb.println("    } else {");
            gcb.println("      jj_input_stream.ReInit(reader, 1, 1);");
            gcb.println("    }");
          }

          gcb.println("    if (token_source == null) {");

          if (Options.getTokenManagerUsesParser() && !Options.getStatic()) {
            gcb.println(
                "      token_source = new "
                    + context.globals().cu_name
                    + "TokenManager(this, jj_input_stream);");
          } else {
            gcb.println(
                "      token_source = new "
                    + context.globals().cu_name
                    + "TokenManager(jj_input_stream);");
          }

          gcb.println("    }");

          if (Options.doesTokenManagerRequireParserAccess()) {
            gcb.println("    token_source.ReInit(this,jj_input_stream);");
          } else {
            gcb.println("    token_source.ReInit(jj_input_stream);");
          }

          gcb.println("    token = new Token();");
          if (Options.getCacheTokens()) {
            gcb.println("    token.next = jj_nt = token_source.getNextToken();");
          } else {
            gcb.println("    jj_ntk = -1;");
          }
          if (Options.getDepthLimit() > 0) {
            gcb.println("    jj_depth = -1;");
          }
          if (context.globals().jjtreeGenerated) {
            gcb.println("    jjtree.reset();");
          }
          if (Options.getErrorReporting()) {
            gcb.println("    jj_gen = 0;");
            if (context.globals().maskindex > 0) {
              gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
              gcb.println("      jj_la1[i] = -1;");
              gcb.println("    }");
            }
            if (context.globals().jj2index != 0) {
              gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
              gcb.println("      jj_2_rtns[i] = new JJCalls();");
              gcb.println("    }");
            }
          }
          gcb.println("  }");
        }
      }
      gcb.println();

      if (Options.getUserTokenManager()) {
        gcb.println("  /** Constructor with user supplied Token Manager. */");
        gcb.println("  public " + context.globals().cu_name + "(TokenManager tm) {");
      } else {
        gcb.println("  /** Constructor with generated Token Manager. */");
        gcb.println(
            "  public "
                + context.globals().cu_name
                + "("
                + context.globals().cu_name
                + "TokenManager tm) {");
      }
      if (Options.getStatic()) {
        gcb.println("    if (jj_initialized_once) {");
        gcb.println(
            "      System.out.println(\"ERROR: Second call to constructor of static parser. \");");
        gcb.println(
            "      System.out.println(\"     You must either use ReInit() or "
                + "set the JavaCC option STATIC\");");
        gcb.println("      System.out.println(\"     to false during parser generation.\");");
        gcb.println(
            "      throw new "
                + (Options.getLegacyExceptionHandling() ? "Error" : "RuntimeException")
                + "();");
        gcb.println("    }");
        gcb.println("    jj_initialized_once = true;");
      }
      gcb.println("    token_source = tm;");
      gcb.println("    token = new Token();");
      if (Options.getCacheTokens()) {
        gcb.println("    token.next = jj_nt = token_source.getNextToken();");
      } else {
        gcb.println("    jj_ntk = -1;");
      }
      if (Options.getDepthLimit() > 0) {
        gcb.println("    jj_depth = -1;");
      }
      if (Options.getErrorReporting()) {
        gcb.println("    jj_gen = 0;");
        if (context.globals().maskindex > 0) {
          gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
          gcb.println("      jj_la1[i] = -1;");
          gcb.println("    }");
        }
        if (context.globals().jj2index != 0) {
          gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
          gcb.println("      jj_2_rtns[i] = new JJCalls();");
          gcb.println("    }");
        }
      }
      gcb.println("  }");
      gcb.println();

      if (Options.getUserTokenManager()) {
        gcb.println("  /** Reinitialise. */");
        gcb.println("  public void ReInit(TokenManager tm) {");
      } else {
        gcb.println("  /** Reinitialise. */");
        gcb.println("  public void ReInit(" + context.globals().cu_name + "TokenManager tm) {");
      }
      gcb.println("    token_source = tm;");
      gcb.println("    token = new Token();");
      if (Options.getCacheTokens()) {
        gcb.println("    token.next = jj_nt = token_source.getNextToken();");
      } else {
        gcb.println("    jj_ntk = -1;");
      }
      if (Options.getDepthLimit() > 0) {
        gcb.println("    jj_depth = -1;");
      }
      if (context.globals().jjtreeGenerated) {
        gcb.println("    jjtree.reset();");
      }
      if (Options.getErrorReporting()) {
        gcb.println("    jj_gen = 0;");
        if (context.globals().maskindex > 0) {
          gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
          gcb.println("      jj_la1[i] = -1;");
          gcb.println("    }");
        }
        if (context.globals().jj2index != 0) {
          gcb.println("    for (int i = 0; i < jj_2_rtns.length; i++) {");
          gcb.println("      jj_2_rtns[i] = new JJCalls();");
          gcb.println("    }");
        }
      }
      gcb.println("  }");
      gcb.println();

      gcb.println(
          "  private "
              + JavaUtil.getStatic()
              + "Token jj_consume_token(int kind) throws ParseException {");
      if (Options.getCacheTokens()) {
        gcb.println("    Token oldToken = token;");
        gcb.println("    if ((token = jj_nt).next != null) {");
        gcb.println("      jj_nt = jj_nt.next;");
        gcb.println("    } else {");
        gcb.println("      jj_nt = jj_nt.next = token_source.getNextToken();");
        gcb.println("    }");
      } else {
        gcb.println("    Token oldToken;");
        gcb.println("    if ((oldToken = token).next != null) {");
        gcb.println("      token = token.next;");
        gcb.println("    } else {");
        gcb.println("      token = token.next = token_source.getNextToken();");
        gcb.println("    }");
        gcb.println("    jj_ntk = -1;");
      }
      gcb.println("    if (token.kind == kind) {");
      if (Options.getErrorReporting()) {
        gcb.println("      jj_gen++;");
        if (context.globals().jj2index != 0) {
          gcb.println("      if (++jj_gc > 100) {");
          gcb.println("        jj_gc = 0;");
          gcb.println("        for (int i = 0; i < jj_2_rtns.length; i++) {");
          gcb.println("          JJCalls c = jj_2_rtns[i];");
          gcb.println("          while (c != null) {");
          gcb.println("            if (c.gen < jj_gen) {");
          gcb.println("              c.first = null;");
          gcb.println("            }");
          gcb.println("            c = c.next;");
          gcb.println("          }");
          gcb.println("        }");
          gcb.println("      }");
        }
      }
      if (Options.getDebugParser()) {
        gcb.println("      trace_token(token, \"\");");
      }
      gcb.println("      return token;");
      gcb.println("    }");
      if (Options.getCacheTokens()) {
        gcb.println("    jj_nt = token;");
      }
      gcb.println("    token = oldToken;");
      if (Options.getErrorReporting()) {
        gcb.println("    jj_kind = kind;");
      }
      gcb.println("    throw generateParseException();");
      gcb.println("  }");
      gcb.println();

      if (context.globals().jj2index != 0) {
        gcb.println("  @SuppressWarnings(\"serial\")");
        gcb.println(
            "  private static final class LookaheadSuccess extends "
                + (Options.getLegacyExceptionHandling()
                    ? "java.lang.Error"
                    : "java.lang.RuntimeException")
                + " {");
        gcb.println("    @Override");
        gcb.println("    public Throwable fillInStackTrace() {");
        gcb.println("      return this;");
        gcb.println("    }");
        gcb.println("  }");
        gcb.println();

        gcb.println("  private static final LookaheadSuccess jj_ls = new LookaheadSuccess();");
        gcb.println();

        gcb.println(
            "  private "
                + JavaUtil.getStatic()
                + JavaUtil.getBooleanType()
                + " jj_scan_token(int kind) {");
        gcb.println("    if (jj_scanpos == jj_lastpos) {");
        gcb.println("      jj_la--;");
        gcb.println("      if (jj_scanpos.next == null) {");
        gcb.println(
            "        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();");
        gcb.println("      } else {");
        gcb.println("        jj_lastpos = jj_scanpos = jj_scanpos.next;");
        gcb.println("      }");
        gcb.println("    } else {");
        gcb.println("      jj_scanpos = jj_scanpos.next;");
        gcb.println("    }");
        if (Options.getErrorReporting()) {
          gcb.println("    if (jj_rescan) {");
          gcb.println("      int i = 0;");
          gcb.println("      Token tok = token;");
          gcb.println("      while (tok != null && tok != jj_scanpos) {");
          gcb.println("        i++;");
          gcb.println("        tok = tok.next;");
          gcb.println("      }");
          gcb.println("      if (tok != null) {");
          gcb.println("        jj_add_error_token(kind, i);");
          gcb.println("      }");
          if (Options.getDebugLookahead()) {
            gcb.println("    } else {");
            gcb.println("      trace_scan(jj_scanpos, kind);");
          }
          gcb.println("    }");
        } else if (Options.getDebugLookahead()) {
          gcb.println("    trace_scan(jj_scanpos, kind);");
        }
        gcb.println("    if (jj_scanpos.kind != kind) {");
        gcb.println("      return true;");
        gcb.println("    }");
        gcb.println("    if (jj_la == 0 && jj_scanpos == jj_lastpos) {");
        gcb.println("      throw jj_ls;");
        gcb.println("    }");
        gcb.println("    return false;");
        gcb.println("  }");
        gcb.println();
      }
      gcb.println();

      gcb.println("/** Get the next Token. */");
      gcb.println("  public " + JavaUtil.getStatic() + "final Token getNextToken() {");
      if (Options.getCacheTokens()) {
        gcb.println("    if ((token = jj_nt).next != null) {");
        gcb.println("      jj_nt = jj_nt.next;");
        gcb.println("    } else {");
        gcb.println("      jj_nt = jj_nt.next = token_source.getNextToken();");
        gcb.println("    }");
      } else {
        gcb.println("    if (token.next != null) {");
        gcb.println("      token = token.next;");
        gcb.println("    } else {");
        gcb.println("      token = token.next = token_source.getNextToken();");
        gcb.println("    }");
        gcb.println("    jj_ntk = -1;");
      }
      if (Options.getErrorReporting()) {
        gcb.println("    jj_gen++;");
      }
      if (Options.getDebugParser()) {
        gcb.println("    trace_token(token, \" (in getNextToken)\");");
      }
      gcb.println("    return token;");
      gcb.println("  }");
      gcb.println();

      gcb.println("/** Get the specific Token. */");
      gcb.println("  public " + JavaUtil.getStatic() + "final Token getToken(int index) {");
      if (context.globals().lookaheadNeeded) {
        gcb.println("    Token t = jj_lookingAhead ? jj_scanpos : token;");
      } else {
        gcb.println("    Token t = token;");
      }
      gcb.println("    for (int i = 0; i < index; i++) {");
      gcb.println("      if (t.next != null) {");
      gcb.println("        t = t.next;");
      gcb.println("      } else {");
      gcb.println("        t = t.next = token_source.getNextToken();");
      gcb.println("      }");
      gcb.println("    }");
      gcb.println("    return t;");
      gcb.println("  }");
      gcb.println();

      if (!Options.getCacheTokens()) {
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_ntk_f() {");
        gcb.println("    if ((jj_nt = token.next) == null) {");
        gcb.println("      return (jj_ntk = (token.next = token_source.getNextToken()).kind);");
        gcb.println("    } else {");
        gcb.println("      return (jj_ntk = jj_nt.kind);");
        gcb.println("    }");
        gcb.println("  }");
        gcb.println();
      }

      if (Options.getErrorReporting()) {
        if (!Options.getGenerateGenerics()) {
          gcb.println(
              "  private "
                  + JavaUtil.getStatic()
                  + "java.util.List jj_expentries = new java.util.ArrayList();");
        } else {
          gcb.println(
              "  private "
                  + JavaUtil.getStatic()
                  + "java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();");
        }
        gcb.println("  private " + JavaUtil.getStatic() + "int[] jj_expentry;");
        gcb.println("  private " + JavaUtil.getStatic() + "int jj_kind = -1;");
        if (context.globals().jj2index != 0) {
          gcb.println("  private " + JavaUtil.getStatic() + "int[] jj_lasttokens = new int[100];");
          gcb.println("  private " + JavaUtil.getStatic() + "int jj_endpos;");
          gcb.println();

          gcb.println(
              "  private " + JavaUtil.getStatic() + "void jj_add_error_token(int kind, int pos) {");
          gcb.println("    if (pos >= 100) {");
          gcb.println("     return;");
          gcb.println("    }");
          gcb.println("    if (pos == jj_endpos + 1) {");
          gcb.println("      jj_lasttokens[jj_endpos++] = kind;");
          gcb.println("    } else if (jj_endpos != 0) {");
          gcb.println("      jj_expentry = new int[jj_endpos];");
          gcb.println("      for (int i = 0; i < jj_endpos; i++) {");
          gcb.println("        jj_expentry[i] = jj_lasttokens[i];");
          gcb.println("      }");
          if (!Options.getGenerateGenerics()) {
            gcb.println(
                "      for (java.util.Iterator it = jj_expentries.iterator(); it.hasNext();) {");
            gcb.println("        int[] oldentry = (int[])(it.next());");
          } else {
            gcb.println("      for (int[] oldentry : jj_expentries) {");
          }

          gcb.println("        if (oldentry.length == jj_expentry.length) {");
          gcb.println("          boolean isMatched = true;");
          gcb.println("          for (int i = 0; i < jj_expentry.length; i++) {");
          gcb.println("            if (oldentry[i] != jj_expentry[i]) {");
          gcb.println("              isMatched = false;");
          gcb.println("              break;");
          gcb.println("            }");
          gcb.println("          }");
          gcb.println("          if (isMatched) {");
          gcb.println("            jj_expentries.add(jj_expentry);");
          gcb.println("            break;");
          gcb.println("          }");
          gcb.println("        }");
          gcb.println("      }");
          gcb.println("      if (pos != 0) {");
          gcb.println("        jj_lasttokens[(jj_endpos = pos) - 1] = kind;");
          gcb.println("      }");
          gcb.println("    }");
          gcb.println("  }");
        }
        gcb.println();

        gcb.println("  /** Generate ParseException. */");
        gcb.println(
            "  public " + JavaUtil.getStatic() + "ParseException generateParseException() {");
        gcb.println("    jj_expentries.clear();");
        gcb.println(
            "    "
                + JavaUtil.getBooleanType()
                + "[] la1tokens = new "
                + JavaUtil.getBooleanType()
                + "["
                + context.globals().tokenCount
                + "];");
        gcb.println("    if (jj_kind >= 0) {");
        gcb.println("      la1tokens[jj_kind] = true;");
        gcb.println("      jj_kind = -1;");
        gcb.println("    }");
        gcb.println("    for (int i = 0; i < " + context.globals().maskindex + "; i++) {");
        gcb.println("      if (jj_la1[i] == jj_gen) {");
        gcb.println("        for (int j = 0; j < 32; j++) {");
        for (int i = 0; i < (((context.globals().tokenCount - 1) / 32) + 1); i++) {
          gcb.println("          if ((jj_la1_" + i + "[i] & (1 << j)) != 0) {");
          gcb.print("              la1tokens[");
          if (i != 0) {
            gcb.print((32 * i) + " + ");
          }
          gcb.println("j] = true;");
          gcb.println("          }");
        }
        gcb.println("        }");
        gcb.println("      }");
        gcb.println("    }");
        gcb.println("    for (int i = 0; i < " + context.globals().tokenCount + "; i++) {");
        gcb.println("      if (la1tokens[i]) {");
        gcb.println("        jj_expentry = new int[1];");
        gcb.println("        jj_expentry[0] = i;");
        gcb.println("        jj_expentries.add(jj_expentry);");
        gcb.println("      }");
        gcb.println("    }");
        if (context.globals().jj2index != 0) {
          gcb.println("    jj_endpos = 0;");
          gcb.println("    jj_rescan_token();");
          gcb.println("    jj_add_error_token(0, 0);");
        }
        gcb.println("    int[][] exptokseq = new int[jj_expentries.size()][];");
        gcb.println("    for (int i = 0; i < jj_expentries.size(); i++) {");
        if (!Options.getGenerateGenerics()) {
          gcb.println("      exptokseq[i] = (int[])jj_expentries.get(i);");
        } else {
          gcb.println("      exptokseq[i] = jj_expentries.get(i);");
        }
        gcb.println("    }");

        if (isJavaModernMode) {
          // Add the lexical state onto the exception message
          gcb.println("    return new ParseException(token, exptokseq, tokenImage, ");
          gcb.println(
              "        token_source == null ? null : token_source.lexStateNames[token_source.curLexState]);");
        } else {
          gcb.println("    return new ParseException(token, exptokseq, tokenImage);");
        }

        gcb.println("  }");
      } else {
        gcb.println("  /** Generate ParseException. */");
        gcb.println(
            "  public " + JavaUtil.getStatic() + "ParseException generateParseException() {");
        gcb.println("    Token errortok = token.next;");
        if (Options.getKeepLineColumn()) {
          gcb.println("    int line = errortok.beginLine, column = errortok.beginColumn;");
        }
        gcb.println("    String mess = (errortok.kind == 0) ? tokenImage[0] : errortok.image;");
        if (Options.getKeepLineColumn()) {
          gcb.println(
              "    return new ParseException("
                  + "\"Parse error at line \" + line + \", column \" + column + \".  "
                  + "Encountered: \" + mess);");
        } else {
          gcb.println(
              "    return new ParseException(\"Parse error at <unknown location>.  "
                  + "Encountered: \" + mess);");
        }
        gcb.println("  }");
      }
      gcb.println();

      gcb.println(
          "  private " + JavaUtil.getStatic() + JavaUtil.getBooleanType() + " trace_enabled;");
      gcb.println();

      gcb.println("  /** Is trace enabled. */");
      gcb.println("  public " + JavaUtil.getStatic() + "final boolean trace_enabled() {");
      gcb.println("    return trace_enabled;");
      gcb.println("  }");
      gcb.println();

      if (Options.getDebugParser()) {
        gcb.println("  /** Enable tracing. */");
        gcb.println("  public " + JavaUtil.getStatic() + "final void enable_tracing() {");
        gcb.println("    trace_enabled = true;");
        gcb.println("  }");
        gcb.println();

        gcb.println("  /** Disable tracing. */");
        gcb.println("  public " + JavaUtil.getStatic() + "final void disable_tracing() {");
        gcb.println("    trace_enabled = false;");
        gcb.println("  }");
        gcb.println();

        gcb.println("  private " + JavaUtil.getStatic() + "int trace_indent = 0;");
        gcb.println();

        gcb.println("  protected " + JavaUtil.getStatic() + "void trace_call(String s) {");
        gcb.println("    if (trace_enabled) {");
        gcb.println("      for (int i = 0; i < trace_indent; i++) { System.out.print(\" \"); }");
        gcb.println("      System.out.println(\"Call:   \" + s);");
        gcb.println("    }");
        gcb.println("    trace_indent = trace_indent + 2;");
        gcb.println("  }");
        gcb.println();

        gcb.println("  protected " + JavaUtil.getStatic() + "void trace_return(String s) {");
        gcb.println("    trace_indent = trace_indent - 2;");
        gcb.println("    if (trace_enabled) {");
        gcb.println("      for (int i = 0; i < trace_indent; i++) { System.out.print(\" \"); }");
        gcb.println("      System.out.println(\"Return: \" + s);");
        gcb.println("    }");
        gcb.println("  }");
        gcb.println();

        gcb.println(
            "  protected " + JavaUtil.getStatic() + "void trace_token(Token t, String where) {");
        gcb.println("    if (trace_enabled) {");
        gcb.println("      for (int i = 0; i < trace_indent; i++) { System.out.print(\" \"); }");
        gcb.println(
            "      System.out.print(\"Consumed token: <\" + t.kind + \", \" + tokenImage[t.kind]);");
        gcb.println(
            "      if (t.kind != 0 && !tokenImage[t.kind].equals(\"\\\"\" + t.image + \"\\\"\")) {");
        gcb.println(
            "        System.out.print(\" \\\"\" + "
                + JavaTemplates.getTokenMgrErrorClass()
                + ".addEscapes(t.image) + \"\\\"\");");
        gcb.println("      }");
        gcb.println(
            "      System.out.println(\"> at \" + t.beginLine + "
                + "\":\" + t.beginColumn + where);");
        gcb.println("    }");
        gcb.println("  }");
        gcb.println();

        gcb.println("  protected " + JavaUtil.getStatic() + "void trace_scan(Token t1, int k2) {");
        gcb.println("    if (trace_enabled) {");
        gcb.println("      for (int i = 0; i < trace_indent; i++) { System.out.print(\" \"); }");
        gcb.println(
            "      System.out.print(\"Visited token: <\" + t1.kind + \", \" + tokenImage[t1.kind]);");
        gcb.println(
            "      if (t1.kind != 0 && !tokenImage[t1.kind].equals(\"\\\"\" + t1.image + \"\\\"\")) {");
        gcb.println(
            "        System.out.print(\" \\\"\" + "
                + JavaTemplates.getTokenMgrErrorClass()
                + ".addEscapes(t1.image) + \"\\\"\");");
        gcb.println("      }");
        gcb.println(
            "      System.out.println(\"> at \" + t1.beginLine + \""
                + ":\" + t1.beginColumn + \"; Expected token: <\" + k2 + \", \" + tokenImage[k2] + \">\");");
        gcb.println("    }");
        gcb.println("  }");
        gcb.println();
      } else {
        gcb.println("  /** Enable tracing. */");
        gcb.println("  public " + JavaUtil.getStatic() + "final void enable_tracing() {");
        gcb.println("  }");
        gcb.println();
        gcb.println("  /** Disable tracing. */");
        gcb.println("  public " + JavaUtil.getStatic() + "final void disable_tracing() {");
        gcb.println("  }");
        gcb.println();
      }

      if ((context.globals().jj2index != 0) && Options.getErrorReporting()) {
        gcb.println("  private " + JavaUtil.getStatic() + "void jj_rescan_token() {");
        gcb.println("    jj_rescan = true;");
        gcb.println("    for (int i = 0; i < " + context.globals().jj2index + "; i++) {");
        gcb.println("      try {");
        gcb.println("        JJCalls p = jj_2_rtns[i];");
        gcb.println("        do {");
        gcb.println("          if (p.gen > jj_gen) {");
        gcb.println("            jj_la = p.arg;");
        gcb.println("            jj_lastpos = jj_scanpos = p.first;");
        gcb.println("            switch (i) {");
        for (int i = 0; i < context.globals().jj2index; i++) {
          gcb.println("              case " + i + ":");
          gcb.println("                jj_3_" + (i + 1) + "();");
          gcb.println("                break;");
        }
        gcb.println("              default:");
        gcb.println("                break;");
        gcb.println("            }");
        gcb.println("          }");
        gcb.println("          p = p.next;");
        gcb.println("        } while (p != null);");
        gcb.println("      } catch (LookaheadSuccess ls) {");
        gcb.println("        // success");
        gcb.println("      }");
        gcb.println("    }");
        gcb.println("    jj_rescan = false;");
        gcb.println("  }");
        gcb.println();

        gcb.println("  private " + JavaUtil.getStatic() + "void jj_save(int index, int xla) {");
        gcb.println("    JJCalls p = jj_2_rtns[index];");
        gcb.println("    while (p.gen > jj_gen) {");
        gcb.println("      if (p.next == null) {");
        gcb.println("        p = p.next = new JJCalls();");
        gcb.println("        break;");
        gcb.println("      }");
        gcb.println("      p = p.next;");
        gcb.println("    }");
        gcb.println("    p.gen = jj_gen + xla - jj_la; ");
        gcb.println("    p.first = token;");
        gcb.println("    p.arg = xla;");
        gcb.println("  }");
        gcb.println();
      }

      if ((context.globals().jj2index != 0) && Options.getErrorReporting()) {
        gcb.println("  static final class JJCalls {");
        gcb.println("    int gen;");
        gcb.println("    Token first;");
        gcb.println("    int arg;");
        gcb.println("    JJCalls next;");
        gcb.println("  }");
        gcb.println();
      }

      if (context.globals().cu_from_insertion_point_2.size() != 0) {
        gcb.printTokenSetup(context.globals().cu_from_insertion_point_2.get(0));
        for (final Iterator<Token> it = context.globals().cu_from_insertion_point_2.iterator();
            it.hasNext(); ) {
          t = it.next();
          gcb.printToken(t);
        }
        gcb.printTrailingComments(t);
      }
      gcb.println();
    }
    // codeBuilder.genCodeLine("}");
  }

  @Override
  public void finish(final CodeGeneratorSettings settings, final ParserData parserData) {
    try {
      gcb.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns true if there is a JAVACODE production that the argument expansion may directly expand
   * to (without consuming tokens or encountering lookahead).
   */
  private boolean javaCodeCheck(final Expansion exp) {
    if (exp instanceof RegularExpression) {
      return false;
    } else if (exp instanceof NonTerminal) {
      final NormalProduction prod = ((NonTerminal) exp).getProd();
      if (prod instanceof CodeProduction) {
        return true;
      } else {
        return javaCodeCheck(prod.getExpansion());
      }
    } else if (exp instanceof Choice) {
      final Choice ch = (Choice) exp;
      for (final Expansion element : ch.getChoices()) {
        if (javaCodeCheck(element)) {
          return true;
        }
      }
      return false;
    } else if (exp instanceof Sequence) {
      final Sequence seq = (Sequence) exp;
      for (int i = 0; i < seq.units.size(); i++) {
        final Expansion[] units = seq.units.toArray(new Expansion[seq.units.size()]);
        if ((units[i] instanceof Lookahead) && ((Lookahead) units[i]).isExplicit()) {
          // An explicit lookahead (rather than one generated implicitly).
          // Assume
          // the user knows what he / she is doing, e.g.
          // "A" ( "B" | LOOKAHEAD("X") jcode() | "C" )* "D"
          return false;
        } else if (javaCodeCheck(units[i])) {
          return true;
        } else if (!Semanticize.emptyExpansionExists(units[i])) {
          return false;
        }
      }
      return false;
    } else if (exp instanceof OneOrMore) {
      final OneOrMore om = (OneOrMore) exp;
      return javaCodeCheck(om.getExpansion());
    } else if (exp instanceof ZeroOrMore) {
      final ZeroOrMore zm = (ZeroOrMore) exp;
      return javaCodeCheck(zm.getExpansion());
    } else if (exp instanceof ZeroOrOne) {
      final ZeroOrOne zo = (ZeroOrOne) exp;
      return javaCodeCheck(zo.getExpansion());
    } else if (exp instanceof TryBlock) {
      final TryBlock tb = (TryBlock) exp;
      return javaCodeCheck(tb.exp);
    } else {
      return false;
    }
  }

  /**
   * An array used to store the first sets generated by the following method. A true entry means
   * that the corresponding token is in the first set.
   */
  private boolean[] firstSet;

  /**
   * Sets up the array "firstSet" above based on the Expansion argument passed to it. Since this is
   * a recursive function, it assumes that "firstSet" has been reset before the first call.
   */
  private void genFirstSet(final Expansion exp) {
    if (exp instanceof RegularExpression) {
      firstSet[((RegularExpression) exp).ordinal] = true;
    } else if (exp instanceof NonTerminal) {
      if (!(((NonTerminal) exp).getProd() instanceof CodeProduction)) {
        genFirstSet(((BNFProduction) ((NonTerminal) exp).getProd()).getExpansion());
      }
    } else if (exp instanceof Choice) {
      final Choice ch = (Choice) exp;
      for (final Expansion element : ch.getChoices()) {
        genFirstSet(element);
      }
    } else if (exp instanceof Sequence) {
      final Sequence seq = (Sequence) exp;
      final Object obj = seq.units.get(0);
      if ((obj instanceof Lookahead) && (((Lookahead) obj).getActionTokens().size() != 0)) {
        jj2LA = true;
      }
      for (int i = 0; i < seq.units.size(); i++) {
        final Expansion unit = seq.units.get(i);
        // Javacode productions can not have FIRST sets. Instead we generate the
        // FIRST set
        // for the preceding LOOKAHEAD (the semantic checks should have made
        // sure that
        // the LOOKAHEAD is suitable).
        if ((unit instanceof NonTerminal)
            && (((NonTerminal) unit).getProd() instanceof CodeProduction)) {
          if ((i > 0) && (seq.units.get(i - 1) instanceof Lookahead)) {
            final Lookahead la = (Lookahead) seq.units.get(i - 1);
            genFirstSet(la.getLaExpansion());
          }
        } else {
          genFirstSet(seq.units.get(i));
        }
        if (!Semanticize.emptyExpansionExists(seq.units.get(i))) {
          break;
        }
      }
    } else if (exp instanceof OneOrMore) {
      final OneOrMore om = (OneOrMore) exp;
      genFirstSet(om.getExpansion());
    } else if (exp instanceof ZeroOrMore) {
      final ZeroOrMore zm = (ZeroOrMore) exp;
      genFirstSet(zm.getExpansion());
    } else if (exp instanceof ZeroOrOne) {
      final ZeroOrOne zo = (ZeroOrOne) exp;
      genFirstSet(zo.getExpansion());
    } else if (exp instanceof TryBlock) {
      final TryBlock tb = (TryBlock) exp;
      genFirstSet(tb.exp);
    }
  }

  /* Constants used in the following method "buildLookaheadChecker". */
  private final int NOOPENSTM = 0;
  private final int OPENIF = 1;
  private final int OPENSWITCH = 2;

  /*
   * The phase 1 routines generates their output into String's and dumps these String's once for
   *  each method.
   * These String's contain the special characters '\u0001' to indicate a positive indent,
   *  and '\u0002' to indicate a negative indent.
   * '\n' is used to indicate a line terminator.
   * The characters '\u0003' and '\u0004' are used to delineate portions of text where '\n's
   *  should not be followed by an indentation.
   */

  /**
   * This method takes two parameters - an array of Lookahead's "<code>conds</code>", and an array
   * of String's "<code>actions</code>".<br>
   * "<code>actions</code>" contains exactly one element more than "<code>conds</code>".<br>
   * "<code>actions</code>" are Java source code, and "<code>conds</code>" translate to conditions
   * <br>
   * - so lets say "<code>f(conds[i])</code>" is <code>true</code> if the lookahead required by "
   * <code>conds[i]
   * </code>" is indeed the case. <br>
   * This method returns a string corresponding to the Java code for: <br>
   * <code>
   * if (f(conds[0]) actions[0]<br>else if (f(conds[1]) actions[1]<br>. . .<br> else actions[action.length-1]
   * </code> <br>
   * A particular action entry ("<code>actions[i]</code>") can be <code>null</code>, in which case,
   * a noop is generated for that action.
   */
  private String buildLookaheadChecker(final Lookahead[] conds, final String[] actions) {

    // The state variables.
    int state = NOOPENSTM;
    int indentAmt = 0;
    final boolean[] casedValues = new boolean[context.globals().tokenCount];
    String retval = "";
    Lookahead la;
    Token t = null;
    final int tokenMaskSize = ((context.globals().tokenCount - 1) / 32) + 1;
    int[] tokenMask = null;

    // Iterate over all the conditions.
    int index = 0;
    while (index < conds.length) {

      la = conds[index];
      jj2LA = false;

      if ((la.getAmount() == 0)
          || Semanticize.emptyExpansionExists(la.getLaExpansion())
          || javaCodeCheck(la.getLaExpansion())) {

        // This handles the following cases:
        // . If syntactic lookahead is not wanted (and hence explicitly specified as 0).
        // . If it is possible for the lookahead expansion to recognize the empty string
        //    - in which case the lookahead trivially passes.
        // . If the lookahead expansion has a JAVACODE production that it directly expands to
        //    - in which case the lookahead trivially passes.
        if (la.getActionTokens().size() == 0) {
          // In addition, if there is no semantic lookahead, then the lookahead trivially succeeds.
          // So break the main loop and treat this case as the default last action.
          break;
        } else {
          // This case is when there is only semantic lookahead (without any preceding syntactic
          //  lookahead). In this case, an "if" statement is generated.
          switch (state) {
            case NOOPENSTM:
              retval += "\n" + "if (";
              indentAmt++;
              break;
            case OPENIF:
              retval += "\u0002\n" + "} else if (";
              break;
            case OPENSWITCH:
              retval += "\u0002\n" + "default:" + "\u0001";
              if (Options.getErrorReporting()) {
                retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
                context.globals().maskindex++;
              }
              context.globals().maskVals.add(tokenMask);
              retval += "\n" + "if (";
              indentAmt++;
          }
          gcb.printTokenSetup(la.getActionTokens().get(0));
          final StringBuilder ifContent = new StringBuilder();
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            ifContent.append(CodeBuilder.toString(t));
          }
          ifContent.append(gcb.getTrailingComments(t));
          retval += ifContent.toString().trim();
          retval += ") {\u0001" + actions[index];
          state = OPENIF;
        }
      } else if ((la.getAmount() == 1) && (la.getActionTokens().size() == 0)) {
        // Special optimal processing when the lookahead is exactly 1,
        //  and there is no semantic lookahead.

        if (firstSet == null) {
          firstSet = new boolean[context.globals().tokenCount];
        }
        for (int i = 0; i < context.globals().tokenCount; i++) {
          firstSet[i] = false;
        }
        // jj2LA is set to false at the beginning of the containing "if" statement.
        // It is checked immediately after the end of the same statement to determine
        //  if lookaheads are to be performed using calls to the jj2 methods.
        genFirstSet(la.getLaExpansion());
        // genFirstSet may find that semantic attributes are appropriate for the next token.
        // In which case, it sets jj2LA to true.
        if (!jj2LA) {

          // This case is if there is no applicable semantic lookahead and the lookahead is one
          //  (excluding the earlier cases such as JAVACODE, etc.).
          switch (state) {
            case OPENIF:
              retval += "\u0002\n" + "} else {\u0001";
              // Control flows through to next case.
            case NOOPENSTM:
              retval += "\n" + "switch (";
              if (Options.getCacheTokens()) {
                retval += "jj_nt.kind) {\u0001";
              } else {
                retval += "(jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {\u0001";
              }
              for (int i = 0; i < context.globals().tokenCount; i++) {
                casedValues[i] = false;
              }
              indentAmt++;
              tokenMask = new int[tokenMaskSize];
              for (int i = 0; i < tokenMaskSize; i++) {
                tokenMask[i] = 0;
              }
              // Don't need to do anything if state is OPENSWITCH.
          }
          for (int i = 0; i < context.globals().tokenCount; i++) {
            if (firstSet[i]) {
              if (!casedValues[i]) {
                casedValues[i] = true;
                retval += "\u0002\ncase ";
                final int j1 = i / 32;
                final int j2 = i % 32;
                tokenMask[j1] |= 1 << j2;
                final String s = context.globals().names_of_tokens.get(Integer.valueOf(i));
                if (s == null) {
                  retval += i;
                } else {
                  retval += s;
                }
                retval += ":\u0001";
              }
            }
          }
          retval += " {";
          retval += actions[index];
          retval += "\nbreak;\n}";
          state = OPENSWITCH;
        }

      } else {
        // This is the case when lookahead is determined through calls to jj2 methods.
        // The other case is when lookahead is 1, but semantic attributes need to be evaluated.
        // Hence this crazy control structure.

        jj2LA = true;
      }

      if (jj2LA) {
        // In this case lookahead is determined by the jj2 methods.

        switch (state) {
          case NOOPENSTM:
            retval += "\n" + "if (";
            indentAmt++;
            break;
          case OPENIF:
            retval += "\u0002\n" + "} else if (";
            break;
          case OPENSWITCH:
            retval += "\u0002\n" + "default:" + "\u0001";
            if (Options.getErrorReporting()) {
              retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
              context.globals().maskindex++;
            }
            context.globals().maskVals.add(tokenMask);
            retval += "\n" + "if (";
            indentAmt++;
        }
        context.globals().jj2index++;

        // At this point, la.la_expansion.internal_name must be "".
        internalNames.put(la.getLaExpansion(), "_" + context.globals().jj2index);
        internalIndexes.put(la.getLaExpansion(), context.globals().jj2index);
        phase2list.add(la);
        retval += "jj_2" + internalNames.get(la.getLaExpansion()) + "(" + la.getAmount() + ")";
        if (la.getActionTokens().size() != 0) {
          // In addition, there is also a semantic lookahead.
          // So concatenate the semantic check with the syntactic one.
          retval += " && (";
          gcb.printTokenSetup(la.getActionTokens().get(0));
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += gcb.getTrailingComments(t);
          retval += ")";
        }
        retval += ") {\u0001" + actions[index];
        state = OPENIF;
      }

      index++;
    }

    // Generate code for the default case. Note this may not be the last entry of "actions"
    //  if any condition can be statically determined to be always "true".

    switch (state) {
      case NOOPENSTM:
        retval += actions[index];
        break;
      case OPENIF:
        retval += "\u0002\n" + "} else {\u0001" + actions[index];
        break;
      case OPENSWITCH:
        retval += "\u0002\n" + "default:" + "\u0001";
        if (Options.getErrorReporting()) {
          retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
          context.globals().maskVals.add(tokenMask);
          context.globals().maskindex++;
        }
        retval += actions[index];
    }
    for (int i = 0; i < indentAmt; i++) {
      retval += "\u0002\n}";
    }

    return retval;
  }

  private int indentamt;

  private void dumpFormattedString(final String str) {
    char ch = ' ';
    char prevChar;
    boolean indentOn = true;
    for (int i = 0; i < str.length(); i++) {
      prevChar = ch;
      ch = str.charAt(i);
      if ((ch == '\n') && (prevChar == '\r')) {
        // do nothing - we've already printed a new line for the '\r' during the previous iteration.
      } else if ((ch == '\n') || (ch == '\r')) {
        if (indentOn) {
          phase1NewLine();
        } else {
          gcb.println();
        }
      } else if (ch == '\u0001') {
        indentamt += 2;
      } else if (ch == '\u0002') {
        indentamt -= 2;
      } else if (ch == '\u0003') {
        indentOn = false;
      } else if (ch == '\u0004') {
        indentOn = true;
      } else {
        gcb.print(ch);
      }
    }
  }

  private void buildPhase1Routine(final BNFProduction p) {
    Token t = p.getReturnTypeTokens().get(0);
    boolean voidReturn = false;
    if (t.kind == JavaCCParserConstants.VOID) {
      voidReturn = true;
    }
    gcb.printTokenSetup(t);
    gcb.printLeadingComments(t, "  ");
    gcb.print(
        "  "
            + (p.getAccessMod() != null ? p.getAccessMod() : "public")
            + " "
            + JavaUtil.getStatic()
            + "final ");
    gcb.printTokenOnly(t);
    for (int i = 1; i < p.getReturnTypeTokens().size(); i++) {
      t = p.getReturnTypeTokens().get(i);
      gcb.printToken(t);
    }
    gcb.printTrailingComments(t);
    gcb.print(p.getLhs() + "(");
    if (p.getParameterListTokens().size() != 0) {
      gcb.printTokenSetup(p.getParameterListTokens().get(0));
      for (final Iterator<Token> it = p.getParameterListTokens().iterator(); it.hasNext(); ) {
        t = it.next();
        gcb.printToken(t);
      }
      gcb.printTrailingComments(t);
    }
    gcb.print(")");
    gcb.print(" throws ParseException");

    for (final List<Token> name : p.getThrowsList()) {
      gcb.print(", ");
      for (final Iterator<Token> it2 = name.iterator(); it2.hasNext(); ) {
        t = it2.next();
        gcb.print(t.image);
      }
    }

    gcb.print(" {");

    genStackCheck(voidReturn);

    indentamt = 4;
    if (Options.getDebugParser()) {
      gcb.println();
      gcb.println("    trace_call(\"" + JavaCCGlobals.addUnicodeEscapes(p.getLhs()) + "\");");
      gcb.println("    try {");
      indentamt = 6;
    }

    if (!Options.booleanValue(Options.UO__IGNORE_ACTIONS)
        && (p.getDeclarationTokens().size() != 0)) {
      gcb.printTokenSetup(p.getDeclarationTokens().get(0));
      for (final Iterator<Token> it = p.getDeclarationTokens().iterator(); it.hasNext(); ) {
        t = it.next();
        gcb.printToken(t);
      }
      gcb.printTrailingComments(t);
    }

    final String code = phase1ExpansionGen(p.getExpansion());
    dumpFormattedString(code);
    gcb.println();

    if (p.isJumpPatched() && !voidReturn) {
      gcb.println(
          "    throw new "
              + (Options.getLegacyExceptionHandling() ? "Error" : "RuntimeException")
              + "(\"Missing return statement in function\");");
    }
    if (Options.getDebugParser()) {
      gcb.println("    } finally {");
      gcb.println("      trace_return(\"" + JavaCCGlobals.addUnicodeEscapes(p.getLhs()) + "\");");
      gcb.println("    }");
    }
    genStackCheckEnd();
    gcb.println("  }");
  }

  private void phase1NewLine() {
    gcb.println();
    for (int i = 0; i < indentamt; i++) {
      gcb.print(" ");
    }
  }

  private int gensymindex = 0;

  private String phase1ExpansionGen(final Expansion e) {
    String retval = "";
    Token t = null;
    Lookahead[] conds;
    String[] actions;
    if (e instanceof RegularExpression) {
      final RegularExpression e_nrw = (RegularExpression) e;
      retval += "\n";
      if (e_nrw.lhsTokens.size() != 0) {
        gcb.printTokenSetup(e_nrw.lhsTokens.get(0));
        for (final Iterator<Token> it = e_nrw.lhsTokens.iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += gcb.getTrailingComments(t);
        retval += " = ";
      }
      final String tail = e_nrw.rhsToken == null ? ");" : ")." + e_nrw.rhsToken.image + ";";
      if (e_nrw.label.equals("")) {
        final Object label = context.globals().names_of_tokens.get(Integer.valueOf(e_nrw.ordinal));
        if (label != null) {
          retval += "jj_consume_token(" + (String) label + tail;
        } else {
          retval += "jj_consume_token(" + e_nrw.ordinal + tail;
        }
      } else {
        retval += "jj_consume_token(" + e_nrw.label + tail;
      }

    } else if (e instanceof NonTerminal) {
      final NonTerminal e_nrw = (NonTerminal) e;
      retval += "\n";
      if (e_nrw.getLhsTokens().size() != 0) {
        gcb.printTokenSetup(e_nrw.getLhsTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getLhsTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += gcb.getTrailingComments(t);
        retval += " = ";
      }
      retval += e_nrw.getName() + "(";
      if (e_nrw.getArgumentTokens().size() != 0) {
        gcb.printTokenSetup(e_nrw.getArgumentTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getArgumentTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += gcb.getTrailingComments(t);
      }
      retval += ");";
    } else if (e instanceof Action) {
      final Action e_nrw = (Action) e;
      retval += "\u0003\n";
      if (!Options.booleanValue(Options.UO__IGNORE_ACTIONS)
          && (e_nrw.getActionTokens().size() != 0)) {
        gcb.printTokenSetup(e_nrw.getActionTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getActionTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += gcb.getTrailingComments(t);
      }
      retval += "\u0004";
    } else if (e instanceof Choice) {
      final Choice e_nrw = (Choice) e;
      conds = new Lookahead[e_nrw.getChoices().size()];
      actions = new String[e_nrw.getChoices().size() + 1];
      actions[e_nrw.getChoices().size()] =
          "\n" + "jj_consume_token(-1);\n" + "throw new ParseException();";
      // In previous line, the "throw" never throws an exception since the evaluation of
      //  jj_consume_token(-1) causes ParseException to be thrown first.
      Sequence nestedSeq;
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nestedSeq = (Sequence) e_nrw.getChoices().get(i);
        actions[i] = phase1ExpansionGen(nestedSeq);
        conds[i] = (Lookahead) nestedSeq.units.get(0);
      }
      retval = buildLookaheadChecker(conds, actions);
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the Lookahead object.
      for (int i = 1; i < e_nrw.units.size(); i++) {
        // For C++, since we are not using exceptions, we will protect all the
        //  expansion choices with if (!error)
        final boolean wrap_in_block = false;
        retval += phase1ExpansionGen(e_nrw.units.get(i));
        if (wrap_in_block) {
          retval += "\n}";
        }
      }
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      retval += "\n";
      final int labelIndex = ++gensymindex;
      retval += "label_" + labelIndex + ":\n";
      retval += "while (true) {\u0001";
      retval += phase1ExpansionGen(nested_e);
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = "\n;";
      actions[1] = "\nbreak label_" + labelIndex + ";";

      retval += buildLookaheadChecker(conds, actions);
      retval += "\u0002\n" + "}";
    } else if (e instanceof ZeroOrMore) {
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      retval += "\n";
      final int labelIndex = ++gensymindex;
      retval += "label_" + labelIndex + ":\n";
      retval += "while (true) {\u0001";
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = "\n;";
      actions[1] = "\nbreak label_" + labelIndex + ";";
      retval += buildLookaheadChecker(conds, actions);
      retval += phase1ExpansionGen(nested_e);
      retval += "\u0002\n" + "}";
    } else if (e instanceof ZeroOrOne) {
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = phase1ExpansionGen(nested_e);
      actions[1] = "\n;";
      retval += buildLookaheadChecker(conds, actions);
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      final Expansion nested_e = e_nrw.exp;
      List<Token> list;
      retval += "\n";
      retval += "try {\u0001";
      retval += phase1ExpansionGen(nested_e);
      retval += "\u0002\n" + "}";
      for (int i = 0; i < e_nrw.catchblks.size(); i++) {
        retval += " catch (";
        list = e_nrw.types.get(i);
        if (list.size() != 0) {
          gcb.printTokenSetup((list.get(0)));
          for (final Iterator<Token> it = list.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += gcb.getTrailingComments(t);
        }
        retval += " ";
        // t = (Token)(e_nrw.ids.get(i));
        // codeBuilder.printTokenSetup(t);
        // retval += CodeGenHelper.getStringToPrint(t);
        // retval += codeBuilder.getTrailingComments(t);
        // retval += ") {\u0003\n";
        list = e_nrw.catchblks.get(i);
        if (list.size() != 0) {
          gcb.printTokenSetup(list.get(0));
          for (final Iterator<Token> it = list.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += gcb.getTrailingComments(t);
        }
        retval += "\u0004\n" + "}";
      }
      if (e_nrw.finallyblk != null) {
        retval += " finally {\u0003\n";

        if (e_nrw.finallyblk.size() != 0) {
          gcb.printTokenSetup(e_nrw.finallyblk.get(0));
          for (final Iterator<Token> it = e_nrw.finallyblk.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += gcb.getTrailingComments(t);
        }
        retval += "\u0004\n" + "}";
      }
    }
    return retval;
  }

  private void buildPhase2Routine(final Lookahead la) {
    final Expansion e = la.getLaExpansion();
    gcb.println(
        "  private "
            + JavaUtil.getStatic()
            + JavaUtil.getBooleanType()
            + " jj_2"
            + internalNames.get(e)
            + "(int xla) {");
    gcb.println("    jj_la = xla;");
    gcb.println("    jj_lastpos = jj_scanpos = token;");

    String ret_suffix = "";
    if (Options.getDepthLimit() > 0) {
      ret_suffix = " && !jj_depth_error";
    }

    gcb.println("    try {");
    gcb.println("      return (!jj_3" + internalNames.get(e) + "()" + ret_suffix + ");");
    gcb.println("    } catch (LookaheadSuccess ls) {");
    gcb.println("      return true;");
    if (Options.getErrorReporting()) {
      gcb.println("    } finally {");
      gcb.println(
          "      jj_save(" + (Integer.parseInt(internalNames.get(e).substring(1)) - 1) + ", xla);");
    }
    gcb.println("    }");
    gcb.println("  }");
    gcb.println();
    final Phase3Data p3d = new Phase3Data(e, la.getAmount());
    phase3list.add(p3d);
    phase3table.put(e, p3d);
  }

  private boolean xsp_declared;

  private Expansion jj3_expansion;

  private String genReturn(final boolean value) {
    final String retval = value ? "true" : "false";
    if (Options.getDebugLookahead() && (jj3_expansion != null)) {
      String tracecode =
          "trace_return(\""
              + JavaCCGlobals.addUnicodeEscapes(((NormalProduction) jj3_expansion.parent).getLhs())
              + "(LOOKAHEAD "
              + (value ? "FAILED" : "SUCCEEDED")
              + ")\");";
      if (Options.getErrorReporting()) {
        tracecode = "if (!jj_rescan) " + tracecode;
      }
      return "{ " + tracecode + " return " + retval + "; }";
    } else {
      return "return " + retval + ";";
    }
  }

  private void generate3R(final Expansion e, final Phase3Data inf) {
    Expansion seq = e;
    if (!internalNames.containsKey(e) || internalNames.get(e).equals("")) {
      while (true) {
        if ((seq instanceof Sequence) && (((Sequence) seq).units.size() == 2)) {
          seq = ((Sequence) seq).units.get(1);
        } else if (seq instanceof NonTerminal) {
          final NonTerminal e_nrw = (NonTerminal) seq;
          final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
          if (ntprod instanceof CodeProduction) {
            break; // nothing to do here
          } else {
            seq = ntprod.getExpansion();
          }
        } else {
          break;
        }
      }

      if (seq instanceof RegularExpression) {
        internalNames.put(e, "jj_scan_token(" + ((RegularExpression) seq).ordinal + ")");
        return;
      }

      gensymindex++;
      internalNames.put(
          e,
          "R_"
              + e.getProductionName()
              + "_"
              + e.getLine()
              + "_"
              + e.getColumn()
              + "_"
              + gensymindex);
      internalIndexes.put(e, gensymindex);
    }
    Phase3Data p3d = phase3table.get(e);
    if ((p3d == null) || (p3d.count < inf.count)) {
      p3d = new Phase3Data(e, inf.count);
      phase3list.add(p3d);
      phase3table.put(e, p3d);
    }
  }

  private void setupPhase3Builds(final Phase3Data inf) {
    final Expansion e = inf.exp;
    if (e instanceof RegularExpression) {
      // nothing to here
    } else if (e instanceof NonTerminal) {
      // All expansions of non-terminals have the "name" fields set.
      // So there's no need to check it below for "e_nrw" and "ntexp".
      // We rely here on the fact that the "name" fields of both these variables are the same.
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        // nothing to do here
      } else {
        generate3R(ntprod.getExpansion(), inf);
      }
    } else if (e instanceof Choice) {
      final Choice e_nrw = (Choice) e;
      for (final Expansion element : e_nrw.getChoices()) {
        generate3R(element, inf);
      }
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the Lookahead object.
      int cnt = inf.count;
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        setupPhase3Builds(new Phase3Data(eseq, cnt));
        cnt -= minimumSize(eseq);
        if (cnt <= 0) {
          break;
        }
      }
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      setupPhase3Builds(new Phase3Data(e_nrw.exp, inf.count));
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      generate3R(e_nrw.getExpansion(), inf);
    } else if (e instanceof ZeroOrMore) {
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      generate3R(e_nrw.getExpansion(), inf);
    } else if (e instanceof ZeroOrOne) {
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      generate3R(e_nrw.getExpansion(), inf);
    }
  }

  private String getTypeForToken() {
    return "Token";
  }

  private String genjj_3Call(final Expansion e) {
    if (internalNames.containsKey(e) && internalNames.get(e).startsWith("jj_scan_token")) {
      return internalNames.get(e);
    } else {
      return "jj_3" + internalNames.get(e) + "()";
    }
  }

  private void buildPhase3Routine(final Phase3Data inf, final boolean recursive_call) {
    final Expansion e = inf.exp;
    Token t = null;
    if (internalNames.containsKey(e) && internalNames.get(e).startsWith("jj_scan_token")) {
      return;
    }

    if (!recursive_call) {
      gcb.println(
          "  private "
              + JavaUtil.getStatic()
              + JavaUtil.getBooleanType()
              + " jj_3"
              + internalNames.get(e)
              + "() {");
      genStackCheck(false);
      xsp_declared = false;
      if (Options.getDebugLookahead() && (e.parent instanceof NormalProduction)) {
        gcb.print("    ");
        if (Options.getErrorReporting()) {
          gcb.print("if (!jj_rescan) ");
        }
        gcb.println(
            "trace_call(\""
                + JavaCCGlobals.addUnicodeEscapes(((NormalProduction) e.parent).getLhs())
                + "(LOOKING AHEAD...)\");");
        jj3_expansion = e;
      } else {
        jj3_expansion = null;
      }
    }
    if (e instanceof RegularExpression) {
      final RegularExpression e_nrw = (RegularExpression) e;
      if (e_nrw.label.equals("")) {
        final String label = context.globals().names_of_tokens.get(Integer.valueOf(e_nrw.ordinal));
        if (label != null) {
          gcb.println("    if (jj_scan_token(" + label + ")) {");
        } else {
          gcb.println("    if (jj_scan_token(" + e_nrw.ordinal + ")) {");
        }
      } else {
        gcb.println("    if (jj_scan_token(" + e_nrw.label + ")) {");
      }
      gcb.println("      " + genReturn(true));
      gcb.println("    }");
      // codeBuilder.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos) " +
      // genReturn(false));
    } else if (e instanceof NonTerminal) {
      // All expansions of non-terminals have the "name" fields set.
      // So there's no need to check it below for "e_nrw" and "ntexp".
      // We rely here on the fact that the "name" fields of both these variables are the same.
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        gcb.println("    if (true) {");
        gcb.println("      jj_la = 0;");
        gcb.println("      jj_scanpos = jj_lastpos;");
        gcb.println("      " + genReturn(false));
        gcb.println("    }");
      } else {
        final Expansion ntexp = ntprod.getExpansion();
        gcb.println("    if (" + genjj_3Call(ntexp) + ") {");
        gcb.println("      " + genReturn(true));
        gcb.println("    }");
      }
    } else if (e instanceof Choice) {
      Sequence nested_seq;
      final Choice e_nrw = (Choice) e;
      if (e_nrw.getChoices().size() != 1) {
        if (!xsp_declared) {
          xsp_declared = true;
          gcb.println("    " + getTypeForToken() + " xsp;");
        }
        gcb.println("    xsp = jj_scanpos;");
      }
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nested_seq = (Sequence) e_nrw.getChoices().get(i);
        final Lookahead la = (Lookahead) nested_seq.units.get(0);
        if (la.getActionTokens().size() != 0) {
          // We have semantic lookahead that must be evaluated.
          context.globals().lookaheadNeeded = true;
          gcb.println("    jj_lookingAhead = true;");
          gcb.print("    jj_semLA = ");
          gcb.printTokenSetup(la.getActionTokens().get(0));
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            gcb.printToken(t);
          }
          gcb.printTrailingComments(t);
          gcb.println(";");
          gcb.println("    jj_lookingAhead = false;");
        }
        for (int k = 0; k < i; k++) {
          gcb.print("  ");
        }
        gcb.print("    if (");
        if (la.getActionTokens().size() != 0) {
          gcb.print("!jj_semLA || ");
        }
        if (i != (e_nrw.getChoices().size() - 1)) {
          gcb.println(genjj_3Call(nested_seq) + ") {");
          for (int k = 0; k < i; k++) {
            gcb.print("  ");
          }
          gcb.println("      jj_scanpos = xsp;");
        } else {
          gcb.println(genjj_3Call(nested_seq) + ") {");
          for (int k = 0; k < i; k++) {
            gcb.print("  ");
          }
          gcb.println("      " + genReturn(true));
          for (int k = 0; k < i; k++) {
            gcb.print("  ");
          }
          gcb.println("    }");
        }
      }
      for (int i = e_nrw.getChoices().size(); i > 1; i--) {
        for (int k = i - 1; k > 1; k--) {
          gcb.print("  ");
        }
        gcb.println("    }");
      }
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the Lookahead object.
      int cnt = inf.count;
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        buildPhase3Routine(new Phase3Data(eseq, cnt), true);
        cnt -= minimumSize(eseq);
        if (cnt <= 0) {
          break;
        }
      }
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      buildPhase3Routine(new Phase3Data(e_nrw.exp, inf.count), true);
    } else if (e instanceof OneOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        gcb.println("    " + getTypeForToken() + " xsp;");
      }
      final OneOrMore e_nrw = (OneOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      gcb.println("    if (" + genjj_3Call(nested_e) + ") {");
      gcb.println("      " + genReturn(true));
      gcb.println("    }");
      gcb.println("    while (true) {");
      gcb.println("      xsp = jj_scanpos;");
      gcb.println("      if (" + genjj_3Call(nested_e) + ") {");
      gcb.println("        jj_scanpos = xsp;");
      gcb.println("        break;");
      gcb.println("      }");
      gcb.println("    }");
    } else if (e instanceof ZeroOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        gcb.println("    " + getTypeForToken() + " xsp;");
      }
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      gcb.println("    while (true) {");
      gcb.println("      xsp = jj_scanpos;");
      gcb.println("      if (" + genjj_3Call(nested_e) + ") {");
      gcb.println("        jj_scanpos = xsp;");
      gcb.println("        break;");
      gcb.println("      }");
      gcb.println("    }");
    } else if (e instanceof ZeroOrOne) {
      if (!xsp_declared) {
        xsp_declared = true;
        gcb.println("    " + getTypeForToken() + " xsp;");
      }
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      final Expansion nested_e = e_nrw.getExpansion();
      gcb.println("    xsp = jj_scanpos;");
      gcb.println("    if (" + genjj_3Call(nested_e) + ") {");
      gcb.println("      jj_scanpos = xsp;");
      gcb.println("    }");
    }
    if (!recursive_call) {
      gcb.println("    " + genReturn(false));
      genStackCheckEnd();
      gcb.println("  }");
      gcb.println();
    }
  }

  private int minimumSize(final Expansion e) {
    return minimumSize(e, Integer.MAX_VALUE);
  }

  /** Returns the minimum number of tokens that can parse to this expansion. */
  private int minimumSize(final Expansion e, final int oldMin) {
    int retval = 0; // should never be used. Will be bad if it is.
    if (e.inMinimumSize) {
      // recursive search for minimum size unnecessary.
      return Integer.MAX_VALUE;
    }
    e.inMinimumSize = true;
    if (e instanceof RegularExpression) {
      retval = 1;
    } else if (e instanceof NonTerminal) {
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        retval = Integer.MAX_VALUE;
        // Make caller think this is unending (for we do not go beyond JAVACODE
        // during
        // phase3 execution).
      } else {
        final Expansion ntexp = ntprod.getExpansion();
        retval = minimumSize(ntexp);
      }
    } else if (e instanceof Choice) {
      int min = oldMin;
      Expansion nested_e;
      final Choice e_nrw = (Choice) e;
      for (int i = 0; (min > 1) && (i < e_nrw.getChoices().size()); i++) {
        nested_e = e_nrw.getChoices().get(i);
        final int min1 = minimumSize(nested_e, min);
        if (min > min1) {
          min = min1;
        }
      }
      retval = min;
    } else if (e instanceof Sequence) {
      int min = 0;
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        final int mineseq = minimumSize(eseq);
        if ((min == Integer.MAX_VALUE) || (mineseq == Integer.MAX_VALUE)) {
          min = Integer.MAX_VALUE; // Adding infinity to something results in
          // infinity.
        } else {
          min += mineseq;
          if (min > oldMin) {
            break;
          }
        }
      }
      retval = min;
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      retval = minimumSize(e_nrw.exp);
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      retval = minimumSize(e_nrw.getExpansion());
    } else if (e instanceof ZeroOrMore) {
      retval = 0;
    } else if (e instanceof ZeroOrOne) {
      retval = 0;
    } else if (e instanceof Lookahead) {
      retval = 0;
    } else if (e instanceof Action) {
      retval = 0;
    }
    e.inMinimumSize = false;
    return retval;
  }

  private void genStackCheck(final boolean voidReturn) {
    if (Options.getDepthLimit() > 0) {
      gcb.println("if (++jj_depth > " + Options.getDepthLimit() + ") {");
      gcb.println("  jj_consume_token(-1);");
      gcb.println("  throw new ParseException();");
      gcb.println("}");
      gcb.println("try {");
    }
  }

  private void genStackCheckEnd() {
    if (Options.getDepthLimit() > 0) {
      gcb.println("  } finally {");
      gcb.println("    --jj_depth;");
      gcb.println("  }");
    }
  }

  private void build() {
    NormalProduction p;
    JavaCodeProduction jp;
    Token t = null;

    for (final Iterator<NormalProduction> prodIterator =
            context.globals().bnfproductions.iterator();
        prodIterator.hasNext(); ) {
      p = prodIterator.next();
      if (p instanceof JavaCodeProduction) {
        jp = (JavaCodeProduction) p;
        t = jp.getReturnTypeTokens().get(0);
        gcb.printTokenSetup(t);
        gcb.printLeadingComments(t, "  ");
        gcb.print(
            "  " + (p.getAccessMod() != null ? p.getAccessMod() + " " : "") + JavaUtil.getStatic());
        gcb.printTokenOnly(t);
        for (int i = 1; i < jp.getReturnTypeTokens().size(); i++) {
          t = jp.getReturnTypeTokens().get(i);
          gcb.printToken(t);
        }
        gcb.printTrailingComments(t);
        gcb.print(" " + jp.getLhs() + "(");
        if (jp.getParameterListTokens().size() != 0) {
          gcb.printTokenSetup(jp.getParameterListTokens().get(0));
          for (final Iterator<Token> it = jp.getParameterListTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            gcb.printToken(t);
          }
          gcb.printTrailingComments(t);
        }
        gcb.print(")");
        gcb.print(" throws ParseException");
        for (final List<Token> name : jp.getThrowsList()) {
          gcb.print(", ");
          for (final Iterator<Token> it2 = name.iterator(); it2.hasNext(); ) {
            t = it2.next();
            gcb.print(t.image);
          }
        }
        gcb.print(" {");
        if (Options.getDebugParser()) {
          gcb.println("");
          gcb.println("    trace_call(\"" + JavaCCGlobals.addUnicodeEscapes(jp.getLhs()) + "\");");
          gcb.print("    try {");
        }
        if (jp.getCodeTokens().size() != 0) {
          gcb.printTokenSetup(jp.getCodeTokens().get(0));
          gcb.printTokenList(jp.getCodeTokens());
        }
        gcb.println("");
        if (Options.getDebugParser()) {
          gcb.println("    } finally {");
          gcb.println(
              "      trace_return(\"" + JavaCCGlobals.addUnicodeEscapes(jp.getLhs()) + "\");");
          gcb.println("    }");
        }
        gcb.println("  }");
        gcb.println("");
      } else {
        buildPhase1Routine((BNFProduction) p);
        gcb.println();
      }
    }

    for (final Lookahead element : phase2list) {
      buildPhase2Routine(element);
    }

    int phase3index = 0;

    while (phase3index < phase3list.size()) {
      for (; phase3index < phase3list.size(); phase3index++) {
        setupPhase3Builds(phase3list.get(phase3index));
      }
    }

    for (final Enumeration<Phase3Data> enumeration = phase3table.elements();
        enumeration.hasMoreElements(); ) {
      buildPhase3Routine(enumeration.nextElement(), false);
    }
  }
}

/** This class stores information to pass from phase 2 to phase 3. */
class Phase3Data {

  /** The expansion to generate the jj3 method for. */
  Expansion exp;

  /**
   * The number of tokens that can still be consumed.<br>
   * This number is used to limit the number of jj3 methods generated.
   */
  int count;

  Phase3Data(final Expansion e, final int c) {
    exp = e;
    count = c;
  }
}
