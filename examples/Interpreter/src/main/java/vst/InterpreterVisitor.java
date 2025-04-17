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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

public class InterpreterVisitor implements SPLParserVisitor {

  private final InputStream in;
  private final PrintStream out;
  private final PrintStream err;

  /** Symbol table: key = name, val = type */
  Hashtable<String, Object> symtab = new Hashtable<>();

  /** "Stack" for calculations. */
  Object[] stack = new Object[10];

  //  Stack<Tree> nodestack;

  /** Top of the "stack". */
  int top = -1;

  InterpreterVisitor(final InputStream in, final PrintStream out, final PrintStream err) {
    this.in = in;
    this.out = out;
    this.err = err;
  }

  @Override
  public void visit(final Node node, Object data) {}

  @Override
  public void visit(final ST_CompilationUnit node, Object data) {
    if (node.children != null) {
      for (Node child : node.children) {
        out.print("Executing:");
        final Token first = child.jjtGetFirstToken();
        final Token last = child.jjtGetLastToken();
        for (Token t = first; t != null; t = t.next) {
          out.print(" " + t);
          if (t == last) break;
        }
        out.println();
        // VarDeclaration() | Statement()
        child.jjtAccept(this, data);
      }
    }
  }

  @Override
  public void visit(final ST_VarDeclaration node, Object data) {
    if (node.type == SPLParserConstants.BOOL) {
      symtab.put(node.name, Boolean.FALSE);
    } else {
      symtab.put(node.name, Integer.valueOf(0));
    }
  }

  @Override
  public void visit(final ST_Assignment node, Object data) {
    // Expression()
    node.jjtGetChild(1).jjtAccept(this, data);

    // PrimaryExpression()
    // note that here we do not visit the child!
    final String name = ((ST_Id) node.jjtGetChild(0)).name;
    symtab.put(name, stack[top]);
  }

  @Override
  public void visit(final ST_OrNode node, Object data) {
    // ConditionalAndExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    if (((Boolean) stack[top]).booleanValue()) {
      stack[top] = new Boolean(true);
      return;
    }

    // ConditionalAndExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(
            ((Boolean) stack[top]).booleanValue() || ((Boolean) stack[top + 1]).booleanValue());
  }

  @Override
  public void visit(final ST_AndNode node, Object data) {
    // InclusiveOrExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    if (!((Boolean) stack[top]).booleanValue()) {
      stack[top] = new Boolean(false);
      return;
    }

    // InclusiveOrExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(
            ((Boolean) stack[top]).booleanValue() && ((Boolean) stack[top + 1]).booleanValue());
  }

  @Override
  public void visit(final ST_BitwiseOrNode node, Object data) {
    // ExclusiveOrExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // ExclusiveOrExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    if (stack[top] instanceof Boolean)
      stack[--top] =
          new Boolean(
              ((Boolean) stack[top]).booleanValue() | ((Boolean) stack[top + 1]).booleanValue());
    else if (stack[top] instanceof Integer)
      stack[--top] =
          new Integer(((Integer) stack[top]).intValue() | ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_BitwiseXorNode node, Object data) {
    // AndExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // AndExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    if (stack[top] instanceof Boolean)
      stack[--top] =
          new Boolean(
              ((Boolean) stack[top]).booleanValue() ^ ((Boolean) stack[top + 1]).booleanValue());
    else if (stack[top] instanceof Integer)
      stack[--top] =
          new Integer(((Integer) stack[top]).intValue() ^ ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_BitwiseAndNode node, Object data) {
    // EqualityExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // EqualityExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    if (stack[top] instanceof Boolean)
      stack[--top] =
          new Boolean(
              ((Boolean) stack[top]).booleanValue() & ((Boolean) stack[top + 1]).booleanValue());
    else if (stack[top] instanceof Integer)
      stack[--top] =
          new Integer(((Integer) stack[top]).intValue() & ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_EQNode node, Object data) {
    // RelationalExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // RelationalExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    if (stack[top] instanceof Boolean)
      stack[--top] =
          new Boolean(
              ((Boolean) stack[top]).booleanValue() == ((Boolean) stack[top + 1]).booleanValue());
    else if (stack[top] instanceof Integer)
      stack[--top] =
          new Boolean(((Integer) stack[top]).intValue() == ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_NENode node, Object data) {
    // RelationalExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // RelationalExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    if (stack[top] instanceof Boolean)
      stack[--top] =
          new Boolean(
              ((Boolean) stack[top]).booleanValue() != ((Boolean) stack[top + 1]).booleanValue());
    else if (stack[top] instanceof Integer)
      stack[--top] =
          new Boolean(((Integer) stack[top]).intValue() != ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_LTNode node, Object data) {
    // AdditiveExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // AdditiveExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(((Integer) stack[top]).intValue() < ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_GTNode node, Object data) {
    // AdditiveExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // AdditiveExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(((Integer) stack[top]).intValue() > ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_LENode node, Object data) {
    // AdditiveExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // AdditiveExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(((Integer) stack[top]).intValue() <= ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_GENode node, Object data) {
    // AdditiveExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // AdditiveExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Boolean(((Integer) stack[top]).intValue() >= ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_AddNode node, Object data) {
    // MultiplicativeExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // MultiplicativeExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Integer(((Integer) stack[top]).intValue() + ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_SubtractNode node, Object data) {
    // MultiplicativeExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // MultiplicativeExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Integer(((Integer) stack[top]).intValue() - ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_MulNode node, Object data) {
    // UnaryExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // UnaryExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Integer(((Integer) stack[top]).intValue() * ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_DivNode node, Object data) {
    // UnaryExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // UnaryExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Integer(((Integer) stack[top]).intValue() / ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_ModNode node, Object data) {
    // UnaryExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    // UnaryExpression()
    node.jjtGetChild(1).jjtAccept(this, data);

    stack[--top] =
        new Integer(((Integer) stack[top]).intValue() % ((Integer) stack[top + 1]).intValue());
  }

  @Override
  public void visit(final ST_BitwiseComplNode node, Object data) {
    // UnaryExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    stack[top] = new Integer(~((Integer) stack[top]).intValue());
  }

  @Override
  public void visit(final ST_NotNode node, Object data) {
    // UnaryExpression()
    node.jjtGetChild(0).jjtAccept(this, data);

    stack[top] = new Boolean(!((Boolean) stack[top]).booleanValue());
  }

  @Override
  public void visit(final ST_Id node, Object data) {
    stack[++top] = symtab.get(node.name);
  }

  @Override
  public void visit(final ST_IntConstNode node, Object data) {
    stack[++top] = new Integer(node.val);
  }

  @Override
  public void visit(final ST_TrueNode node, Object data) {
    stack[++top] = new Boolean(true);
  }

  @Override
  public void visit(final ST_FalseNode node, Object data) {
    stack[++top] = new Boolean(false);
  }

  @Override
  public void visit(final ST_Block node, Object data) {
    for (Node child : node.children) {
      // Statement()
      child.jjtAccept(this, data);
    }
  }

  @Override
  public void visit(final ST_StatementExpression node, Object data) {
    // Assignment()
    node.jjtGetChild(0).jjtAccept(this, data);

    top--; // just throw away the value.
  }

  @Override
  public void visit(final ST_IfStatement node, Object data) {
    // Expression()
    node.jjtGetChild(0).jjtAccept(this, data);

    if (((Boolean) stack[top--]).booleanValue()) {
      // Statement()
      node.jjtGetChild(1).jjtAccept(this, data);
    } else if (node.jjtGetNumChildren() == 3) {
      // Statement()
      node.jjtGetChild(2).jjtAccept(this, data);
    }
  }

  @Override
  public void visit(final ST_WhileStatement node, Object data) {
    do {
      // Expression()
      node.jjtGetChild(0).jjtAccept(this, data);
      
      if (((Boolean) stack[top--]).booleanValue()) {
        // Statement()
        node.jjtGetChild(1).jjtAccept(this, data);
      } else {
        break;
      }
    } while (true);
  }

  @Override
  public void visit(final ST_ReadStatement node, Object data) {
    final String name = node.name;
    final Object o = symtab.get(name);
    byte[] b = new byte[64];

    if (o == null) {
      out.flush();
      err.println("Undefined variable : " + name);
      err.flush();
      System.exit(1);
    }

    try {
      out.flush();
      if (o instanceof Boolean) {
        out.println("Enter a value for \'" + name + "\' (boolean) : ");
        in.read(b);
        Boolean bb = new Boolean((new String(b)).trim());
        out.println("Read this value for \'" + name + "\' (boolean) : " + bb);
        symtab.put(name, bb);
      } else if (o instanceof Integer) {
        out.println("Enter a value for \'" + name + "\' (int) : ");
        in.read(b);
        Integer bi = new Integer((new String(b)).trim());
        out.println("Read this value for \'" + name + "\' (int) : " + bi);
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

  @Override
  public void visit(final ST_WriteStatement node, Object data) {
    final String name = node.name;
    final Object o = symtab.get(name);

    if (o == null) {
      out.flush();
      err.println("Undefined variable : " + name);
      err.flush();
      System.exit(-1);
    } else {
      out.println("Value of " + name + " : " + o);
    }
  }
}
