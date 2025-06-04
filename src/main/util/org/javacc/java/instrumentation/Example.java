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

import java.util.ArrayList;
import java.util.List;

/**
 * @see <a
 *     href="https://www.baeldung.com/java-size-of-object">www.baeldung.com/java-size-of-object</a>
 */
public class Example {

  public static void printObjectSize(final String name, final Object object) {
    System.out.println(
        "Object name: "
            + name
            + ", type: "
            + object.getClass()
            + ", size: "
            + Agent.getObjectSize(object)
            + " bytes");
  }

  static Integer[] static_int = new Integer[100];

  public static void main(final String[] arguments) {
    final String emptyString = "";
    final String string = "Estimating Object Size Using Instrumentation";
    final String[] stringArray = {emptyString, string, "com.baeldung"};
    final String[] anotherStringArray = new String[100];
    final List<String> stringList = new ArrayList<>();
    final StringBuilder stringBuilder = new StringBuilder(100);
    final int maxIntPrimitive = Integer.MAX_VALUE;
    final int minIntPrimitive = Integer.MIN_VALUE;
    final Integer maxInteger = Integer.MAX_VALUE;
    final Integer minInteger = Integer.MIN_VALUE;
    final long zeroLong = 0L;
    final double zeroDouble = 0.0;
    final boolean falseBoolean = false;
    final Object object = new Object();

    class EmptyClass {}

    final EmptyClass emptyClass = new EmptyClass();

    class StringClass {
      public String s;
    }
    final StringClass stringClass = new StringClass();

    printObjectSize("static_int", static_int);
    printObjectSize("emptyString", emptyString);
    printObjectSize("string", string);
    printObjectSize("stringArray", stringArray);
    printObjectSize("anotherStringArray", anotherStringArray);
    printObjectSize("stringList", stringList);
    printObjectSize("stringBuilder", stringBuilder);
    printObjectSize("maxIntPrimitive", maxIntPrimitive);
    printObjectSize("minIntPrimitive", minIntPrimitive);
    printObjectSize("maxInteger", maxInteger);
    printObjectSize("minInteger", minInteger);
    printObjectSize("zeroLong", zeroLong);
    printObjectSize("zeroDouble", zeroDouble);
    printObjectSize("falseBoolean", falseBoolean);
    printObjectSize("Day.TUESDAY", Day.TUESDAY);
    printObjectSize("object", object);
    printObjectSize("emptyClass", emptyClass);
    printObjectSize("stringClass", stringClass);
    printObjectSize("stringClass.s", stringClass.s);
  }

  public enum Day {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
  }
}
