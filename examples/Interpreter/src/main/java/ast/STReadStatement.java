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
package ast;

import java.io.IOException;

public class STReadStatement extends Node {

  String name;

  public STReadStatement(int id) {
    super(id);
  }

  public STReadStatement(SPLParser p, int id) {
    super(p, id);
  }

  public void interpret() {
    Object o = symtab.get(name);
    byte[] b = new byte[64];

    if (o == null) {
      System.out.flush();
      System.err.println("Undefined variable : " + name);
      System.err.flush();
      System.exit(1);
    }

    try {
      System.out.flush();
      if (o instanceof Boolean) {
        System.out.println("Enter a value for \'" + name + "\' (boolean) : ");
        System.in.read(b);
        Boolean bb = new Boolean((new String(b)).trim());
        System.out.println("Read this value for \'" + name + "\' (boolean) : " + bb);
        symtab.put(name, bb);
      } else if (o instanceof Integer) {
        System.out.println("Enter a value for \'" + name + "\' (int) : ");
        System.in.read(b);
        Integer bi = new Integer((new String(b)).trim());
        System.out.println("Read this value for \'" + name + "\' (int) : " + bi);
        symtab.put(name, bi);
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
      System.exit(2);
    } catch (NumberFormatException nfe) {
      nfe.printStackTrace();
      System.exit(4);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(32);
    }
  }
}
