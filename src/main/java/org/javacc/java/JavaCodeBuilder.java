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

import java.util.ArrayList;
import java.util.List;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Context;
import org.javacc.utils.CodeBuilder;

/** The {@link JavaCodeBuilder} class. */
class JavaCodeBuilder extends CodeBuilder<JavaCodeBuilder> {

  private final StringBuffer buffer = new StringBuffer();

  private String packageName;
  private final List<String> imports = new ArrayList<>();

  /**
   * Constructs an instance of {@link CodeBuilder}.
   *
   * @param options
   */
  private JavaCodeBuilder(final Context context, final CodeGeneratorSettings options) {
    super(context, options);
  }

  /** Get the {@link StringBuffer} */
  @Override
  protected final StringBuffer getBuffer() {
    return buffer;
  }

  /**
   * Set the Java package name
   *
   * @param packageName
   */
  JavaCodeBuilder setPackageName(final String packageName) {
    this.packageName = packageName;
    return this;
  }

  /**
   * Set the Java import name
   *
   * @param importName
   */
  JavaCodeBuilder addImportName(final String importName) {
    this.imports.add(importName);
    return this;
  }

  @Override
  protected final void build() {
    final StringBuffer buffer = new StringBuffer("\n");

    if (packageName.length() > 0) {
      buffer.append("package ").append(packageName).append(";\n\n");
    }
    if (!imports.isEmpty()) {
      for (final String importName : imports) {
        buffer.append("import ").append(importName).append(";\n\n");
      }
    }

    buffer.append(getBuffer());

    store(getFile(), buffer);
  }

  /**
   * Constructs an instance of {@link JavaCodeBuilder}.
   *
   * @param options
   */
  static JavaCodeBuilder of(final Context context, final CodeGeneratorSettings options) {
    return new JavaCodeBuilder(context, options);
  }
}
