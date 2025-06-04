/*
 * Copyright (c) 2025, Marc Mazas <mazas.marc@gmail.com>.
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
package org.javacc.java.instrumentation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Utility to print a class's static fields sizes, using reflection and instrumentation.
 *
 * @author Marc Mazas - 06/2025
 */
public class StaticFieldsSizes {

  /**
   * @param field - must be static, otherwise will throw a NullPointerException
   * @throws IllegalArgumentException - will not occur as no instance object
   * @throws IllegalAccessException - if setAccessible(true) has not worked
   */
  public static void printStaticFieldSize(final Field field)
      throws IllegalArgumentException, IllegalAccessException {
    field.setAccessible(true);
    final Object obj = field.get(null);
    System.out.printf(
        "%20s %40s %10d bytes\n", field.getName(), obj.getClass(), Agent.getObjectSize(obj));
  }

  /**
   * Standard main. Give it the qualified class name as the command line argument.
   *
   * @param args - the command line arguments; will use the first one as the class name to load and
   *     look for its static fields
   * @throws ClassNotFoundException - if the given class is not on the classpath
   * @throws IllegalArgumentException - see {@link #printStaticFieldSize(Field)}
   * @throws IllegalAccessException - see {@link #printStaticFieldSize(Field)}
   */
  public static void main(final String[] args)
      throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException {
    final List<Field> allFields = Arrays.asList(Class.forName(args[0]).getDeclaredFields());
    for (final Field field : allFields) {
      if (Modifier.isStatic(field.getModifiers())) {
        printStaticFieldSize(field);
      }
    }
  }
}
