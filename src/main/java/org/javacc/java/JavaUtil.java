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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javacc.parser.Context;
import org.javacc.parser.JavaCCParserConstants;
import org.javacc.parser.Options;
import org.javacc.parser.Token;

abstract class JavaUtil {

  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("package[^a-z]+([^;]+)", Pattern.CASE_INSENSITIVE);

  private JavaUtil() {}

  /**
   * Parses the package from the insertion points.
   *
   * @return
   */
  public static String parsePackage(final Context context) {
    Token t = null;
    final StringWriter writer = new StringWriter();
    try (PrintWriter printer = new PrintWriter(writer)) {
      if ((context.globals().cu_to_insertion_point_1.size() != 0)
          && (context.globals().cu_to_insertion_point_1.get(0).kind
              == JavaCCParserConstants.PACKAGE)) {
        for (int i = 1; i < context.globals().cu_to_insertion_point_1.size(); i++) {
          if (context.globals().cu_to_insertion_point_1.get(i).kind
              == JavaCCParserConstants.SEMICOLON) {
            JavaUtil.printTokenSetup(context.globals().cu_to_insertion_point_1.get(0), context);
            for (int j = 0; j <= i; j++) {
              t = context.globals().cu_to_insertion_point_1.get(j);
              JavaUtil.printToken(t, printer, true, context);
            }
            JavaUtil.printTrailingComments(t, printer, true, context);
            printer.println("");
            printer.println("");
            break;
          }
        }
      }
    }

    final String text = writer.toString();
    if (text == null) {
      return "";
    }

    final Matcher matcher = JavaUtil.PACKAGE_PATTERN.matcher(text);
    return matcher.find() ? matcher.group(1) : "";
  }

  public static String getStatic() {
    return (Options.getStatic() ? "static " : "");
  }

  //  public static String getBooleanType() {
  //    return "boolean";
  //  }

  private static void printTokenSetup(final Token t, final Context context) {
    Token tt = t;
    while (tt.specialToken != null) {
      tt = tt.specialToken;
    }
    context.globals().cline = tt.beginLine;
    context.globals().ccol = tt.beginColumn;
  }

  private static void printToken(
      final Token t, final java.io.PrintWriter ostr, final boolean escape, final Context context) {
    Token tt = t.specialToken;
    if (tt != null) {
      while (tt.specialToken != null) {
        tt = tt.specialToken;
      }
      while (tt != null) {
        ostr.append(tt.printTokenOnly(context.globals(), escape));
        tt = tt.next;
      }
    }
    ostr.append(t.printTokenOnly(context.globals(), escape));
  }

  private static void printTrailingComments(
      final Token t, final java.io.PrintWriter ostr, final boolean escape, final Context context) {
    if (t.next == null) {
      return;
    }

    JavaUtil.printLeadingComments(t.next, escape, context);
  }

  private static String printLeadingComments(
      final Token t, final boolean escape, final Context context) {
    String retval = "";
    if (t.specialToken == null) {
      return retval;
    }
    Token tt = t.specialToken;
    while (tt.specialToken != null) {
      tt = tt.specialToken;
    }
    while (tt != null) {
      retval += tt.printTokenOnly(context.globals(), escape);
      tt = tt.next;
    }
    if ((context.globals().ccol != 1) && (context.globals().cline != t.beginLine)) {
      retval += "\n";
      context.globals().cline++;
      context.globals().ccol = 1;
    }
    return retval;
  }
}
