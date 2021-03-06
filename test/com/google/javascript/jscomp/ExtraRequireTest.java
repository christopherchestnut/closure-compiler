/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.util.List;

/** Tests for the "extra requires" check in {@link CheckMissingAndExtraRequires}. */
public final class ExtraRequireTest extends CompilerTestCase {
  public ExtraRequireTest() {
    super();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingAndExtraRequires(
        compiler, CheckMissingAndExtraRequires.Mode.FULL_COMPILE);
  }

  public void testNoWarning() {
    testSame("goog.require('foo.Bar'); var x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); let x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); const x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); /** @type {foo.Bar} */ var x;");
    testSame("goog.require('foo.Bar'); /** @type {Array<foo.Bar>} */ var x;");
    testSame("goog.require('foo.Bar'); var x = new foo.Bar.Baz();");
    testSame("goog.require('foo.bar'); var x = foo.bar();");
    testSame("goog.require('foo.bar'); var x = /** @type {foo.bar} */ (null);");
    testSame("goog.require('foo.bar'); function f(/** foo.bar */ x) {}");
    testSame("goog.require('foo.bar'); alert(foo.bar.baz);");
    testSame("/** @suppress {extraRequire} */ goog.require('foo.bar');");
    testSame("goog.require('foo.bar'); goog.scope(function() { var bar = foo.bar; alert(bar); });");
    testSame("goog.require('foo'); foo();");
    testSame("goog.require('foo'); new foo();");
    testSame("/** @suppress {extraRequire} */ var bar = goog.require('foo.bar');");
  }

  public void testNoWarning_externsJsDoc() {
    String js = "goog.require('ns.Foo'); /** @type {ns.Foo} */ var f;";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    testSame(externs(externs), srcs(js));
  }

  public void testNoWarning_externsNew() {
    String js = "goog.require('ns.Foo'); new ns.Foo();";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    testSame(externs(externs), srcs(js));
  }

  public void testNoWarning_objlitShorthand() {
    testSame(
        LINE_JOINER.join(
            "goog.module('example.module');",
            "",
            "const X = goog.require('example.X');",
            "alert({X});"));

    testSame(
        LINE_JOINER.join(
            "goog.require('X');",
            "alert({X});"));
  }

  public void testNoWarning_objlitShorthand_withES6Modules() {
    testSame(
        LINE_JOINER.join(
            "import 'example.module';",
            "",
            "import X from 'example.X';",
            "alert({X});"));
  }

  public void testNoWarning_InnerClassInExtends() {
    String js =
        LINE_JOINER.join(
            "var goog = {};",
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}");
    testSame(js);
  }

  public void testWarning() {
    testError("goog.require('foo.bar');", EXTRA_REQUIRE_WARNING);

    testError(LINE_JOINER.join(
        "goog.require('Bar');",
        "function func( {a} ){}",
        "func( {a: 1} );"), EXTRA_REQUIRE_WARNING);
    testError(LINE_JOINER.join(
        "goog.require('Bar');",
        "function func( a = 1 ){}",
        "func(42);"), EXTRA_REQUIRE_WARNING);
  }

  public void testNoWarningMultipleFiles() {
    String[] js = new String[] {
      "goog.require('Foo'); var foo = new Foo();",
      "goog.require('Bar'); var bar = new Bar();"
    };
    testSame(js);
  }

  public void testPassModule() {
    testSame(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "new Foo();"));

    testSame(
        LINE_JOINER.join(
            "import Bar from 'bar';",
            "new Bar();"));

    testSame(
        LINE_JOINER.join(
            "import {CoolFeature as Foo} from 'bar';",
            "new Foo();"));

    testSame(
        LINE_JOINER.join(
            "import Bar, {CoolFeature as Foo, OtherThing as Baz} from 'bar';",
            "new Foo(); new Bar(); new Baz();"));
  }

  public void testFailModule() {
    testError(
        "import {Foo} from 'bar';",
        EXTRA_REQUIRE_WARNING);

    testError(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "goog.require('example.ExtraRequire');",
            "new Foo;"),
            EXTRA_REQUIRE_WARNING);
  }

  public void testPassForwardDeclareInModule() {
    testSame(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "",
            "/**",
            " * @param {!Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}",
            "",
            "exports = listener;"));
  }

  public void testUnusedForwardDeclareInModule() {
    // Reports extra require warning, but only in single-file mode.
    testSame(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "var Unused = goog.forwardDeclare('goog.events.Unused');",
            "",
            "/**",
            " * @param {!Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}",
            "",
            "exports = listener;"));
  }

  public void testPassForwardDeclare() {
    testSame(
        LINE_JOINER.join(
            "goog.forwardDeclare('goog.events.Event');",
            "",
            "/**",
            " * @param {!goog.events.Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}"));
  }

  public void testFailForwardDeclare() {
    // Reports extra require warning, but only in single-file mode.
    testSame(
        LINE_JOINER.join(
            "goog.forwardDeclare('goog.events.Event');",
            "goog.forwardDeclare('goog.events.Unused');",
            "",
            "/**",
            " * @param {!goog.events.Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}"));
  }

  public void testGoogModuleGet() {
    testSame(
        LINE_JOINER.join(
            "goog.provide('x.y');",
            "goog.require('foo.bar');",
            "",
            "goog.scope(function() {",
            "var bar = goog.module.get('foo.bar');",
            "x.y = function() {};",
            "});"));
  }

  public void testGoogModuleWithDestructuringRequire() {
    testError(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var dom = goog.require('goog.dom');",
            "var {assert} = goog.require('goog.asserts');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => dom.getElement(id));",
            "}",
            "",
            "exports = getElems;"),
        EXTRA_REQUIRE_WARNING);

     testSame(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  googAssert(true);",
            "};"));

     testError(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert, fail} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testError(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testError(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  public void testES6ModuleWithDestructuringRequire() {
    testError(
        LINE_JOINER.join(
            "import 'example';",
            "",
            "import {assert, fail} from 'goog.asserts';",
            "",
            "export default function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  public void testGoogModuleWithEmptyDestructuringRequire() {
    testError(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {} = goog.require('goog.asserts');"),
        EXTRA_REQUIRE_WARNING);
  }
}
