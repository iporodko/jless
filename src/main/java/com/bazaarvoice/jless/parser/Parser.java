package com.bazaarvoice.jless.parser;

import com.bazaarvoice.jless.ast.CatchAllNode;
import com.bazaarvoice.jless.ast.ExpressionNode;
import com.bazaarvoice.jless.ast.ExpressionsNode;
import com.bazaarvoice.jless.ast.MultipleLineCommentNode;
import com.bazaarvoice.jless.ast.Node;
import com.bazaarvoice.jless.ast.PropertyNode;
import com.bazaarvoice.jless.ast.RuleSetNode;
import com.bazaarvoice.jless.ast.ScopeNode;
import com.bazaarvoice.jless.ast.SelectorNode;
import com.bazaarvoice.jless.ast.SelectorSegmentNode;
import com.bazaarvoice.jless.ast.SelectorsNode;
import com.bazaarvoice.jless.ast.SimpleNode;
import com.bazaarvoice.jless.ast.SingleLineCommentNode;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.support.Var;

/**
 * Transcribed from the <a href="http://github.com/cloudhead/less/blob/master/lib/less/engine/grammar">LESS Treetop grammar</a>
 * by Alexis Sellier into Parboiled.
 *
 * @author J. Ryan Stinnett
 */
@BuildParseTree
public class Parser extends BaseParser<Node> {

    boolean debug(Context context) {
        return true;
    }

    /*boolean push(Node node) {
        return set(node);
    }

    Node pop() {
        return value();
    }

    Node peek(int i) {
        return value();
    }

    Node peek() {
        return value();
    }
    
    String match() {
        return prevText();
    }*/

    // TODO: Remove Lower, Use Ident, etc.

//    @SuppressSubnodes
    public Rule Scope() {
        return Sequence(
                push(new ScopeNode()),
                ZeroOrMore(Sequence(
                        debug(getContext()), 
                        FirstOf(/*Import(), */Declaration(), RuleSet(), /*Mixin(), */Comment()),
                        peek(1).addChild(pop())
                ))
        );
    }

    Rule Comment() {
        return /*Sequence(debug(getContext()), */FirstOf(MultipleLineComment(), SingleLineComment())/*, debug(getContext()))*/;
    }

    /**
     * Ws0 '//' (!'\n' .)* '\n' Ws0
     */
    Rule SingleLineComment() {
        return Sequence(
                Ws0(), "//", ZeroOrMore(Sequence(TestNot('\n'), Any())),
                push(new SingleLineCommentNode(match())),
                '\n', Ws0()
        );
    }

    /**
     * Ws0 '/*' (!'*\/' .)* '*\/' Ws0
     */
    Rule MultipleLineComment() {
        return Sequence(
                Ws0(), "/*", ZeroOrMore(Sequence(TestNot("*/"), Any())),
                push(new MultipleLineCommentNode(match())),
                "*/", Ws0()
        );
    }

    // ********** CSS Rule Sets **********

    /**
     * Selectors '{' Ws0 Primary Ws0 '}' Sp0 ';'? Ws0
     *
     * TODO: What is hide for? Add mixin.
     *
     * Ex: div, .class, body > p {...}
     */
    Rule RuleSet() {
        return Sequence(
                //debug(getContext()),
                Selectors(), push(new RuleSetNode(pop())),
                '{', Ws0(), Scope(), peek(1).addChild(pop()), Ws0(), '}',
                Sp0(), Optional(';'), Ws0()
                //debug(getContext())
        );
    }

    // ********** CSS Selectors **********

    /**
     * Ws0 Selector (Sp0 ',' Ws0 Selector)* Ws0
     */
    Rule Selectors() {
        return Sequence(
                Ws0(),
                Selector(), push(new SelectorsNode(pop())),
                ZeroOrMore(Sequence(Sp0(), ',', Ws0(), Selector(), peek(1).addChild(pop()))),
                Ws0()
        );
    }

    /**
     * (Sp0 Select Element Sp0)+
     *
     * Ex: div > p a { ... }
     */
    Rule Selector() {
        Var<SelectorSegmentNode> selectorSegmentNode = new Var<SelectorSegmentNode>();
        return Sequence(
                push(new SelectorNode()),
                OneOrMore(Sequence(
                        Sp0(),
                        Select(), selectorSegmentNode.set(new SelectorSegmentNode(match())),
                        Element(), selectorSegmentNode.get().setElement(match()),
                        Sp0(),
                        peek().addChild(selectorSegmentNode.getAndClear())
                ))
        );
    }

    /**
     * (
     *     (Class / ID / Tag / Ident)
     *     Attribute*
     *     (
     *         '(' Alpha+ ')' / '(' (PseudoExp / Selector / Digit1) ')'
     *     )?
     * )+
     * / Attribute+ / '@media' / '@font-face'
     *
     * Ex: div / .class / #id / input[type="text"] / lang(fr)
     */
    Rule Element() {
        return FirstOf(
                OneOrMore(Sequence(
                        FirstOf(Class(), ID(), Tag(), Ident()),
                        ZeroOrMore(Attribute()),
                        Optional(FirstOf(
                                Sequence('(', OneOrMore(Alpha()), ')'),
                                Sequence('(', FirstOf(/*PseudoExp(), */Selector(), Digit1()), ')')
                        ))
                )),
                OneOrMore(Attribute()),
                "@media",
                "@font-face"
        );
    }

    /**
     * (Sp0 [+>~] Sp0 / '::' / Sp0 ':' / Sp1)?
     */
    Rule Select() {
        return Optional(FirstOf(
                Sequence(Sp0(), CharSet("+>~"), Sp0()),
                "::",
                Sequence(Sp0(), ':'),
                Sp1()
        ));
    }

    /**
     * '*'? '-'? [-_Alpha] [-_Alphanumeric]*
     */
    Rule Ident() {
        return Sequence(
                Optional('*'),
                Optional('-'),
                FirstOf(CharSet("-_"), Alpha()),
                ZeroOrMore(FirstOf(CharSet("-_"), Alphanumeric()))
        );
    }

    // ********** Variables & Expressions **********

    /**
     * Ws0 (Ident / Variable) Sp0 ':' Ws0 Expressions (Ws0 ',' Ws0 Expressions)* Sp0 (';' / Ws0 &'}') Ws0
     * / Ws0 Ident Sp0 ':' Sp0 ';' Ws0
     *
     * Ex: @my-var: 12px; height: 100%;
     */
    Rule Declaration() {
        return FirstOf(
                Sequence(
                        Ws0(),
                        /*FirstOf(*/Ident()/*, Variable())*/, push(new PropertyNode(match())),
                        Sp0(), ':', Ws0(),
                        Expressions(), peek(1).addChild(pop()),
                        ZeroOrMore(
                                Sequence(Ws0(), ',', Ws0(), Expressions(), peek(1).addChild(pop()))
                        ),
                        Sp0(), FirstOf(';', Sequence(Ws0(), Test('}'))), Ws0()
                ),
                // Empty rules are ignored (TODO: Remove?)
                Sequence(Ws0(), Ident(), push(new PropertyNode(match())), Sp0(), ':', Sp0(), ';', Ws0())
        );
    }

    /**
     * Expression (Operator Expression)+ TODO: Add me?
     * / Expression (Ws1 Expression)* Important?
     * / [-_.&*\/=:,+? []()#%Alphanumeric]+ TODO: What does this catch?
     */
    Rule Expressions() {
        return FirstOf(
                Sequence(
                        Expression(), push(new ExpressionsNode(pop())),
                        ZeroOrMore(Sequence(Ws1(), Expression(), peek(1).addChild(pop()))),
                        Optional(Sequence(Important(), peek(1).addChild(pop())))
                ),
                Sequence(OneOrMore(FirstOf(CharSet("-_.&*/=:,+? []()#%"), Alphanumeric())), push(new CatchAllNode(match())))
        );
    }

    /**
     * '(' Sp0 Expressions Sp0 ')' TODO: Add later
     * / Entity
     */
    Rule Expression() {
        return Sequence(Entity(), push(new ExpressionNode(pop())));
    }

    /**
     * Sp0 '!' Sp0 'important'
     */
    Rule Important() {
        return Sequence(Sp0(), '!', Sp0(), "important", push(new SimpleNode("!important")));
    }

    // ********** HTML Entities **********

    /**
     * '.' [_Alpha] [-_Alphanumeric]*
     */
    Rule Class() {
        return Sequence(
                '.',
                FirstOf('-', Alpha()),
                ZeroOrMore(FirstOf(CharSet("-_"), Alphanumeric()))
        );
    }

    /**
     * '#' [_Alpha] [-_Alphanumeric]*
     */
    Rule ID() {
        return Sequence(
                '#',
                FirstOf('-', Alpha()),
                ZeroOrMore(FirstOf(CharSet("-_"), Alphanumeric()))
        );
    }

    /**
     * Alpha [-Alpha]* Digit? / '*'
     */
    Rule Tag() {
        return FirstOf(
                Sequence(
                        Alpha(),
                        ZeroOrMore(FirstOf('-', Alpha())),
                        Optional(Digit())
                ),
                '*'
        );
    }

    /**
     * '[' AttributeName [|~*$^]? '=' (String / [-_Alphanumeric]+) ']' / '[' (AttributeName / String) ']'
     *
     * Ex: [type="text"]
     */
    Rule Attribute() {
        return FirstOf(
                Sequence(
                        '[',
                        AttributeName(),
                        Optional(CharSet("|~*$^")),
                        '=',
                        FirstOf(String(), OneOrMore(FirstOf(CharSet("-_"), Alphanumeric()))),
                        ']'
                ),
                Sequence('[', FirstOf(AttributeName(), String()), ']')
        );
    }

    /**
     * Alpha [-Alpha]* Digit? / '*' (This may not be needed here)
     */
    Rule AttributeName() {
        return Tag();
    }

    // ********** Functions & Arguments **********

    /**
     * [-_Alpha]+ Arguments
     */
    /*Rule Function() {
        return Sequence(
                OneOrMore(FirstOf(CharSet("-_"), Alpha())),
                Arguments()
        );
    }*/

    /**
     * '(' Sp0 Expressions Sp0 (',' Sp0 Expressions Sp0)* ')' / '(' Sp0 ')'
     */

    // ********** Entities **********

    // TODO: Check use of !Nd

    /**
     * Any whitespace delimited token (??)
     */
    Rule Entity() {
        return Sequence(
                FirstOf(URL(), /*AlphaFilter(), Function(), Accessor(), */Keyword(), /*Variable(), */Literal(), Font()),
                push(new SimpleNode(match()))
        );
    }

    /**
     * 'url(' (String / [-_%$/.&=:;#+?Alphanumeric]+) ')'
     * TODO: Function? Unescape?
     */
    Rule URL() {
        return Sequence("url(", FirstOf(String(), FirstOf(CharSet("-_%$/.&=:;#+?"), Alphanumeric())), ')');
    }

    /**
     * [-Alpha]+ !Nd
     *
     * Ex: blue, small, normal
     */
    Rule Keyword() {
        return Sequence(OneOrMore(FirstOf('-', Alpha())), TestNot(Nd()));
    }

    /**
     * Tokens that don't need to evaluated
     */
    Rule Literal() {
        return FirstOf(Color(), MultiDimension(), Dimension(), String());
    }

    /**
     * Alpha [-Alphanumeric]* !Nd / String
     */
    Rule Font() {
        return FirstOf(
                Sequence(Alpha(), ZeroOrMore(FirstOf('-', Alphanumeric())), TestNot(Nd())),
                String()
        );
    }

    /**
     * Ex: 'hello' / "hello"
     */
    Rule String() {
        return FirstOf(
                Sequence('\'', ZeroOrMore(Sequence(TestNot('\''), Any())), '\''),
                Sequence('"', ZeroOrMore(Sequence(TestNot('"'), Any())), '"')
        );
    }

    /**
     * Some CSS properties allow multiple dimensions separated by '/'
     *
     * (Dimension / [-a-z]+) '/' Dimension
     */
    Rule MultiDimension() {
        return Sequence(
                FirstOf(
                        Dimension(),
                        OneOrMore(FirstOf('-', this.Alpha()))
                ),
                '/',
                Dimension()
        );
    }

    /**
     * Number Unit
     */
    Rule Dimension() {
        return Sequence(Number(), Unit());
    }

    /**
     * '-'? Digit* '.' Digit+ / '-'? Digit+
     */
    Rule Number() {
        return FirstOf(
                Sequence(Optional('-'), Digit0(), '.', Digit1()),
                Sequence(Optional('-'), Digit1())
        );
    }

    /**
     * ('px' / 'em' / 'pc' / '%' / 'ex' / 'in' / 'deg' / 's' / 'pt' / 'cm' / 'mm')?
     */
    Rule Unit() {
        return Optional(FirstOf("px", "em", "pc", '%', "ex", "in", "deg", 's', "pt", "cm", "mm"));
    }

    /**
     * '#' RGB / (('hsl' / 'rgb') 'a'?) Arguments
     */
    Rule Color() {
        return /*FirstOf(*/
                Sequence('#', RGB())/*,
                Sequence(FirstOf("hsl", "rgb"), Optional('a'), Arguments())
        )*/;
    }

    /**
     * Ex: 0099dd / 09d
     */
    Rule RGB() {
        return FirstOf(
                Sequence(Hex(), Hex(), Hex(), Hex(), Hex(), Hex()),
                Sequence(Hex(), Hex(), Hex())
        );
    }

    // ********** Characters & Simple Character Groups **********

    Rule Alphanumeric() {
        return FirstOf(Alpha(), Digit());
    }

    Rule Alpha() {
        return FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'));
    }

    Rule Digit0() {
        return ZeroOrMore(Digit());
    }

    Rule Digit1() {
        return OneOrMore(Digit());
    }

    Rule Digit() {
        return CharRange('0', '9');
    }

    Rule Hex() {
        return FirstOf(CharRange('a', 'f'), CharRange('A', 'F'), Digit());
    }

    Rule Sp0() {
        return ZeroOrMore(' ');
    }

    Rule Sp1() {
        return OneOrMore(' ');
    }

    Rule Ws0() {
        return ZeroOrMore(Whitespace());
    }

    Rule Ws1() {
        return OneOrMore(Whitespace());
    }

    Rule Nd() {
        return Sequence(TestNot(Delimiter()), Any());
    }

    Rule Whitespace() {
        return CharSet(" \n");
    }

    Rule Delimiter() {
        return CharSet(" ;,!})\n");
    }
}
