/*
 * Copyright 2007 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nicksantos@google.com (Nick Santos)
 */
public final class ProcessDefinesTest extends TypeICompilerTestCase {

  public ProcessDefinesTest() {
    super(DEFAULT_EXTERNS + "var externMethod;");
  }

  private Map<String, Node> overrides = new HashMap<>();
  private GlobalNamespace namespace;
  private boolean doReplacements;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    overrides.clear();
    doReplacements = true;

    // ProcessDefines emits warnings if the user tries to re-define a constant,
    // but the constant is not defined anywhere in the binary.
    allowSourcelessWarnings();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ProcessDefinesWithInjectedNamespace(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // Only do one repetition, so that we can make sure the first pass keeps
    // GlobalNamespace up to date.
    return 1;
  }

  /**
   * Helper for tests that expects definitions to remain unchanged, such
   * that {@code definitions+js} is converted to {@code definitions+expected}.
   */
  private void testWithPrefix(String definitions, String js, String expected) {
    test(definitions + js, definitions + expected);
  }

  public void testBasicDefine1() {
    test("/** @define {boolean} */ var DEF = true", "/** @define {boolean} */ var DEF=true");
  }

  public void testBasicDefine2() {
    test("/** @define {string} */ var DEF = 'a'", "/** @define {string} */ var DEF=\"a\"");
  }

  public void testBasicDefine3() {
    test("/** @define {number} */ var DEF = 0", "/** @define {number} */ var DEF=0");
  }

  public void testDefineBadType() {
    testError("/** @define {Object} */ var DEF = {}", ProcessDefines.INVALID_DEFINE_TYPE_ERROR);
  }

  public void testChecksOnlyProducesErrors() {
    doReplacements = false;
    testError("/** @define {Object} */ var DEF = {}", ProcessDefines.INVALID_DEFINE_TYPE_ERROR);
  }

  public void testChecksOnlyProducesUnknownDefineWarning() {
    doReplacements = false;
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {};", "var a = {};", warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  public void testDefineWithBadValue1() {
    testError("/** @define {boolean} */ var DEF = new Boolean(true);",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testDefineWithBadValue2() {
    testError("/** @define {string} */ var DEF = 'x' + y;",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testDefineInExterns() {
    testSame(
        DEFAULT_EXTERNS + "/** @define {boolean} */ var EXTERN_DEF;",
        "");
  }

  public void testDefineInExternsPlusUsage() {
    testSame(
        DEFAULT_EXTERNS + "/** @define {boolean} */ var EXTERN_DEF;",
        "/** @define {boolean} */ var DEF = EXTERN_DEF");
  }

  public void testNonDefineInExternsPlusUsage() {
    testError(
        DEFAULT_EXTERNS + "/** @const {boolean} */ var EXTERN_NON_DEF;",
        "/** @define {boolean} */ var DEF = EXTERN_NON_DEF",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testDefineCompiledInExterns() {
    testSame(
        DEFAULT_EXTERNS + "/** @define {boolean} */ var COMPILED;",
        "");
  }

  public void testDefineWithDependentValue() {
    test(
        LINE_JOINER.join(
            "/** @define {boolean} */ var BASE = false;",
            "/** @define {boolean} */ var DEF = !BASE;"),
        LINE_JOINER.join(
            "/** @define {boolean} */ var BASE = false;",
            "/** @define {boolean} */ var DEF = !BASE"));
    test(
        LINE_JOINER.join(
            "var a = {};",
            "/** @define {boolean} */ a.BASE = false;",
            "/** @define {boolean} */ a.DEF = !a.BASE;"),
        LINE_JOINER.join(
            "var a={};",
            "/** @define {boolean} */ a.BASE = false;",
            "/** @define {boolean} */ a.DEF = !a.BASE"));
  }


  public void testDefineWithInvalidDependentValue() {
    testError("var BASE = false;\n" +
        "/** @define {boolean} */ var DEF = !BASE;",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testOverriding1() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    overrides.put("DEF_OVERRIDE_TO_FALSE", new Node(Token.FALSE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false;"
            + "/** @define {boolean} */ var DEF_OVERRIDE_TO_FALSE = true",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true;"
            + "/** @define {boolean} */var DEF_OVERRIDE_TO_FALSE=false");
  }

  public void testOverriding2() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    String normalConst = "var DEF_OVERRIDE_TO_FALSE=true;";
    testWithPrefix(
        normalConst,
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true");
  }

  public void testOverriding3() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true;",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true");
  }

  public void testOverridingString0() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"x\"");
  }

  public void testOverridingString1() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"x\" + \"y\"");
  }

  public void testOverridingString2() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"foo\"");
  }

  public void testOverridingString3() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"foo\"");
  }

  public void testMisspelledOverride() {
    overrides.put("DEF_BAD_OVERIDE", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ var DEF_BAD_OVERRIDE = true",
        "/** @define {boolean} */ var DEF_BAD_OVERRIDE = true",
        warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  public void testCompiledIsKnownDefine() {
    overrides.put("COMPILED", new Node(Token.TRUE));
    testSame("");
  }

  public void testSimpleReassign1() {
    // Here and in other tests where we reassign to @defined values, we @suppress newCheckTypes
    // to suppress NTI_CONST_REASSIGNED warnings.
    test(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF = false;",
            "DEF = true;"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF=true;",
            "true"));
  }

  public void testSimpleReassign2() {
    test(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {number|boolean} */ var DEF=false;",
            "DEF=true;",
            "DEF=3"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {number|boolean} */ var DEF=3;",
            "true;3"));

    Name def = namespace.getNameIndex().get("DEF");
    assertThat(def.getRefs()).hasSize(1);
    assertEquals(1, def.globalSets);
    assertNotNull(def.getDeclaration());
  }

  public void testSimpleReassign3() {
    test(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF = false;",
            "var x;",
            "x = DEF = true;"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF = true;",
            "var x;",
            "x = true"));
  }

  public void testAssignBeforeDeclaration1() {
    testError("DEF=false;var b=false,/** @define {boolean} */DEF=true,c=false",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testAssignBeforeDeclaration2() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    testError(
        "DEF_OVERRIDE_TO_TRUE = 3;" +
        "/** @define {boolean|number} */ var DEF_OVERRIDE_TO_TRUE = false;",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testEmptyDeclaration() {
    testError("/** @define {boolean} */ var DEF;", ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testReassignAfterCall() {
    testError("/** @define {boolean} */var DEF=true;externMethod();DEF=false",
        ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testReassignAfterRef() {
    testError("/** @define {boolean} */var DEF=true;var x = DEF;DEF=false",
        ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testReassignWithExpr() {
    testError("/** @define {boolean} */var DEF=true;var x;DEF=x=false",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testReassignAfterNonGlobalRef() {
    test(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF=true;",
            "var x=function(){var y=DEF};",
            "DEF=false"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {newCheckTypes} */",
            "/** @define {boolean} */ var DEF = false;",
            "var x = function(){var y = DEF; };",
            "false"));

    Name def = namespace.getNameIndex().get("DEF");
    assertThat(def.getRefs()).hasSize(2);
    assertEquals(1, def.globalSets);
    assertNotNull(def.getDeclaration());
  }

  public void testReassignAfterRefInConditional() {
    testError(
        "/** @define {boolean} */var DEF=true;" +
        "if (false) {var x=DEF} DEF=false;",
        ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testAssignInNonGlobalScope() {
    testError("/** @define {boolean} */var DEF=true;function foo() {DEF=false};",
        ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testDeclareInNonGlobalScope() {
    testError("function foo() {/** @define {boolean} */var DEF=true;};",
        ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testDefineAssignmentInLoop() {
    testError("/** @define {boolean} */var DEF=true;var x=0;while (x) {DEF=false;}",
        ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testWithNoDefines() {
    testSame("var DEF=true;var x={};x.foo={}");
  }

  public void testNamespacedDefine1() {
    test("var a = {}; /** @define {boolean} */ a.B = false; a.B = true;",
         "var a = {}; /** @define {boolean} */ a.B = true; true;");

    Name aDotB = namespace.getNameIndex().get("a.B");
    assertThat(aDotB.getRefs()).hasSize(1);
    assertEquals(1, aDotB.globalSets);
    assertNotNull(aDotB.getDeclaration());
  }

  public void testNamespacedDefine2a() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; /** @define {boolean} */ a.B = true;");
  }

  public void testNamespacedDefine2b() {
    overrides.put("a.B", new Node(Token.TRUE));
    testError(
        "var a = { /** @define {boolean} */ B : false };",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testNamespacedDefine2c() {
    overrides.put("a.B", new Node(Token.TRUE));
    testError(
        "var a = { /** @define {boolean} */ get B() { return false } };",
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testNamespacedDefine3() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {};", "var a = {};", warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  public void testNamespacedDefine4() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; /** @define {boolean} */ a.B = true;");
  }


  public void testOverrideAfterAlias() {
    testError("var x; /** @define {boolean} */var DEF=true; x=DEF; DEF=false;",
        ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  private class ProcessDefinesWithInjectedNamespace implements CompilerPass {
    private final Compiler compiler;

    public ProcessDefinesWithInjectedNamespace(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node js) {
      namespace = new GlobalNamespace(compiler, externs, js);
      new ProcessDefines(compiler, overrides, doReplacements)
          .injectNamespace(namespace)
          .process(externs, js);
    }
  }
}
