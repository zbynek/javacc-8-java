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
package vst;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/** Simple Programming Language parser. */
public class SPLMain {

  /** Main entry point. */
  public static void main(String args[]) {
    InputStream spl = null;
    InputStream input = System.in;
    PrintStream output = System.out;
    PrintStream error = System.err;
    InputStream prevInput = null;
    PrintStream prevOutput = null;
    PrintStream prevError = null;

    try {
      SPLParser parser;
      switch (args.length) {
        case 1:
          output.println(
              "Simple Programming Language Interpreter (VST):  Reading from file "
                  + args[0]
                  + " . . .");
          try {
            parser = new SPLParser(new FileInputStream(args[0]));
          } catch (FileNotFoundException e) {
            error.println(
                "Simple Programming Language Interpreter (VST):  File "
                    + args[0]
                    + " not found.");
            return;
          }
          break;
        case 4:
          spl = new FileInputStream(args[0]);
          prevInput = input;
          input = new FileInputStream(args[1]);
          System.setIn(input);
          prevOutput = output;
          output = new PrintStream(args[2]);
          System.setOut(output);
          prevError = error;
          error = new PrintStream(args[3]);
          System.setErr(error);
          parser = new SPLParser(spl);
          break;
        default:
          output.println("Simple Programming Language Interpreter (VST):  Usage :");
          output.println("         java SPLMain spl [in out err]");
          return;
      }
      parser.CompilationUnit();
      final InterpreterVisitor vis = new InterpreterVisitor(input, output, error);
      parser.jjtree.rootNode().jjtAccept(vis, new Object());
    } catch (ParseException e) {
      error.println(
          "Simple Programming Language Interpreter (VST):  Encountered errors during parse.");
      e.printStackTrace();
    } catch (Exception e1) {
      error.println(
          "Simple Programming Language Interpreter (VST):  Encountered errors during interpretation/tree building.");
      e1.printStackTrace();
    } finally {
      try {
        input.close();
        output.close();
        error.close();
      } catch (IOException e) {
      }
      if (prevInput != null) System.setIn(prevInput);
      if (prevOutput != null) System.setOut(prevOutput);
      if (prevError != null) System.setErr(prevError);
    }
  }
}
