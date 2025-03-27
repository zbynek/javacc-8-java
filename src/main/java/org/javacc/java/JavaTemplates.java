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

import org.javacc.jjtree.JJTreeGlobals;
import org.javacc.parser.Options;

/** The {@link JavaTemplates} class. */
abstract class JavaTemplates {

  private static final JavaTemplates RESOURCES_JAVA_CLASSIC = new JavaClassicTemplates();
  private static final JavaTemplates RESOURCES_JAVA_MODERN = new JavaModernTemplates();

  public abstract String getJavaCharStreamTemplateResourceUrl();

  public abstract String getSimpleCharStreamTemplateResourceUrl();

  public abstract String getParseExceptionTemplateResourceUrl();

  static String getTokenMgrErrorClass() {
    return Options.getLegacyExceptionHandling() ? "TokenMgrError" : "TokenMgrException";
  }

  static String nodeConstants() {
    return JJTreeGlobals.parserName + "TreeConstants";
  }

  static String visitorClass() {
    return JJTreeGlobals.parserName + "Visitor";
  }

  static String defaultVisitorClass() {
    return JJTreeGlobals.parserName + "DefaultVisitor";
  }

  /** The {@link JavaClassicTemplates} class. */
  private static class JavaClassicTemplates extends JavaTemplates {

    @Override
    public String getJavaCharStreamTemplateResourceUrl() {
      return "/templates/java/JavaCharStream.template";
    }

    @Override
    public String getSimpleCharStreamTemplateResourceUrl() {
      return "/templates/java/SimpleCharStream.template";
    }

    @Override
    public String getParseExceptionTemplateResourceUrl() {
      return "/templates/java/ParseException.template";
    }
  }

  /** The {@link JavaModernTemplates} class. */
  private static class JavaModernTemplates extends JavaTemplates {

    @Override
    public String getJavaCharStreamTemplateResourceUrl() {
      return "/templates/gwt/JavaCharStream.template";
    }

    @Override
    public String getSimpleCharStreamTemplateResourceUrl() {
      return "/templates/gwt/SimpleCharStream.template";
    }

    @Override
    public String getParseExceptionTemplateResourceUrl() {
      return "/templates/gwt/ParseException.template";
    }
  }

  static boolean isJavaModern() {
    return Options.getJavaTemplateType().equals(Options.UOV__JAVA_TEMPLATE_TYPE__MODERN);
  }

  static JavaTemplates getTemplates() {
    return JavaTemplates.isJavaModern()
        ? JavaTemplates.RESOURCES_JAVA_MODERN
        : JavaTemplates.RESOURCES_JAVA_CLASSIC;
  }
}
