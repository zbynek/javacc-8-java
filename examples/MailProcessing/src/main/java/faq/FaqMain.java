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
 *     * Neither the name of the Sun Microsystems, Inc. nor the names of its
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
package faq;

import java.io.*;

public class FaqMain {

  static int count = 0;

  static int beginAt = 1;

  static String outdir;

  static PrintWriter indexpw;

  static String fix(String s) {
    String retval = "";
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '<') {
        retval += "&lt;";
      } else if (c == '>') {
        retval += "&gt;";
      } else {
        retval += c;
      }
    }
    return retval;
  }

  public static void main(String args[]) throws ParseException, IOException, FileNotFoundException {
    if (args.length < 2) {
      System.err.println("Error: bad number of arguments (" + args.length + " instead of 3)");
      System.err.println("Usage: FaqMain index infile outdir");
      System.exit(4);
    }
    beginAt = Integer.parseInt(args[0]);
    outdir = args[2];
    indexpw = new PrintWriter(new FileWriter(outdir + "/index.out.html"));
    indexpw.println("<title>Selected list of emails from the JavaCC mailing list</title>");
    indexpw.println("<h2>Selected list of emails from the JavaCC mailing list</h2>");
    Faq parser = new Faq(new FileInputStream(args[1]));
    parser.MailFile();
  }
}
