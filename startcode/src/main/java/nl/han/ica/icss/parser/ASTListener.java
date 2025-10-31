package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;
import nl.han.ica.icss.ast.selectors.ClassSelector;
import nl.han.ica.icss.ast.selectors.IdSelector;
import nl.han.ica.icss.ast.selectors.TagSelector;

import java.util.ArrayList;

public class ASTListener extends ICSSBaseListener {

    private final AST ast;
    private final IHANStack<ASTNode> currentContainer;
    // stack used to build expressions (literals, variable references, operations)
    private final IHANStack<ASTNode> exprStack;

    public ASTListener() {
        ast = new AST();
        currentContainer = new HANStack<>();
        exprStack = new HANStack<>();
    }

    public AST getAST() {
        if (ast != null && ast.root != null) {
            normalizeBooleanReferences(ast.root);
        }
        return ast;
    }

    private void normalizeBooleanReferences(ASTNode node) {
        if (node == null) return;
        ArrayList<ASTNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            ASTNode child = children.get(i);
            // recurse first
            normalizeBooleanReferences(child);
            if (child instanceof VariableReference) {
                String name = ((VariableReference) child).name;
                if (name != null && (name.equalsIgnoreCase("true") || name.equalsIgnoreCase("false"))) {
                    BoolLiteral bool = new BoolLiteral(name.equalsIgnoreCase("true"));
                    node.removeChild(child);
                    node.addChild(bool);
                    children.set(i, bool);
                }
            }
        }
    }

    private void attachToParent(ASTNode child) {
        try {
            ASTNode parent = currentContainer.peek();
            if (parent != null) {
                parent.addChild(child);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void enterStylesheet(ICSSParser.StylesheetContext ctx) {
        Stylesheet stylesheet = new Stylesheet();
        ast.setRoot(stylesheet);
        currentContainer.push(stylesheet);
    }

    @Override
    public void exitStylesheet(ICSSParser.StylesheetContext ctx) {
        try { currentContainer.pop(); } catch (Exception ignored) { }
    }

    @Override
    public void enterStyleRule(ICSSParser.StyleRuleContext ctx) {
        Stylerule rule = new Stylerule();
        attachToParent(rule);
        currentContainer.push(rule);
    }

    @Override
    public void exitStyleRule(ICSSParser.StyleRuleContext ctx) {
        try { currentContainer.pop(); } catch (Exception ignored) { }
    }

    // --- Level 2/3: if/else and blocks ---
    @Override
    public void enterIfStatement(ICSSParser.IfStatementContext ctx) {
        IfClause ifc = new IfClause();
        attachToParent(ifc);
        currentContainer.push(ifc);
    }

    @Override
    public void exitIfStatement(ICSSParser.IfStatementContext ctx) {
        try {
            // condition expression should be built on exprStack
            ASTNode cond = null;
            try { cond = exprStack.peek(); } catch (Exception ignored) { }
            if (cond != null) {
                exprStack.pop();
                ASTNode parent = currentContainer.peek();
                if (parent instanceof IfClause) {
                    ((IfClause) parent).addChild((ASTNode) cond);
                }
            }
            // pop de IfClause die gepushed is in enterIfStatement
            currentContainer.pop();
        } catch (Exception ignored) { }
    }

    @Override
    public void enterBlock(ICSSParser.BlockContext ctx) {
        // als er een else blok wordt aangeroepen na een if maakt hij een ElseClause aan
        if (ctx.getParent() instanceof ICSSParser.IfStatementContext) {
            int idx = -1;
            for (int i = 0; i < ctx.getParent().getChildCount(); i++) {
                if (ctx.getParent().getChild(i) == ctx) {
                    idx = i; break;
                }
            }
            // in grammar the first block is child index 4, the else-block (if present) is child index 6
            if (idx == 6) {
                ElseClause elseC = new ElseClause();
                attachToParent(elseC);
                currentContainer.push(elseC);
            }
        } else {
        }
    }

    @Override
    public void exitBlock(ICSSParser.BlockContext ctx) {
        // Als er een elseclause in een enter hebben gepushed word deze hier weer gepopt
        try {
            ASTNode top = currentContainer.peek();
            if (top instanceof ElseClause) currentContainer.pop();
        } catch (Exception ignored) { }
    }

    @Override
    public void enterSelector(ICSSParser.SelectorContext ctx) {
        String txt = ctx.getText();
        if (txt != null) txt = txt.toLowerCase();
        if (txt.startsWith(".")) {
            attachToParent(new ClassSelector(txt));
        } else if (txt.startsWith("#")) {
            attachToParent(new IdSelector(txt));
        } else {
            attachToParent(new TagSelector(txt));
        }
    }

    @Override
    public void enterDeclaration(ICSSParser.DeclarationContext ctx) {
        Declaration decl = new Declaration();
        attachToParent(decl);
        currentContainer.push(decl);

        if (ctx.LOWER_IDENT() != null) {
            PropertyName prop = new PropertyName(ctx.LOWER_IDENT().getText());
            attachToParent(prop);
        }
    }

    @Override
    public void exitDeclaration(ICSSParser.DeclarationContext ctx) {
        try {
            // wanneer uit een declaration gaan wordt de expresion (als ie er echt is) gebouwd op exprStack
            ASTNode expr = null;
            try { expr = exprStack.peek(); } catch (Exception ignored) { }
            if (expr != null) {
                exprStack.pop();
                ASTNode parent = currentContainer.peek();
                if (parent instanceof Declaration) {
                    ((Declaration) parent).addChild((ASTNode) expr);
                }
            }
            currentContainer.pop();
        } catch (Exception ignored) { }
    }

    @Override
    public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
        VariableAssignment varAssign = new VariableAssignment();
        attachToParent(varAssign);
        currentContainer.push(varAssign);

        if (ctx.LOWER_IDENT() != null) {
            VariableReference name = new VariableReference(ctx.LOWER_IDENT().getText());
            attachToParent(name);
        } else if (ctx.CAPITAL_IDENT() != null) {
            VariableReference name = new VariableReference(ctx.CAPITAL_IDENT().getText());
            attachToParent(name);
        }
    }

    @Override
    public void exitVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
        try {
            // maakt een expressie en voegt er een variable aan toe
            ASTNode expr = null;
            try { expr = exprStack.peek(); } catch (Exception ignored) { }
            if (expr != null) {
                exprStack.pop();
                ASTNode parent = currentContainer.peek();
                if (parent instanceof VariableAssignment) {
                    ((VariableAssignment) parent).addChild((ASTNode) expr);
                }
            }
            currentContainer.pop();
        } catch (Exception ignored) { }
    }

    @Override
    public void enterValue(ICSSParser.ValueContext ctx) {
        String txt = ctx.getText();

        ASTNode node = null;
        if (ctx.TRUE() != null) {
            node = new BoolLiteral(true);
        } else if (ctx.FALSE() != null) {
            node = new BoolLiteral(false);
        } else if (txt.equalsIgnoreCase("true")) {
            node = new BoolLiteral(true);
        } else if (txt.equalsIgnoreCase("false")) {
            node = new BoolLiteral(false);
        } else if (txt.matches("^#[0-9a-fA-F]{6}$")) {
            node = new ColorLiteral(txt);
        } else if (txt.endsWith("px")) {
            String num = txt.substring(0, txt.length() - 2);
            try { node = new PixelLiteral(Integer.parseInt(num)); } catch (NumberFormatException ignored) { }
        } else if (txt.endsWith("%")) {
            String num = txt.substring(0, txt.length() - 1);
            try { node = new PercentageLiteral(Integer.parseInt(num)); } catch (NumberFormatException ignored) { }
        } else if (txt.matches("^[0-9]+$")) {
            try { node = new ScalarLiteral(Integer.parseInt(txt)); } catch (NumberFormatException ignored) { }
        } else if (txt.startsWith(".")) {
            node = new ClassSelector(txt.toLowerCase());
        } else if (txt.startsWith("#")) {
            node = new IdSelector(txt.toLowerCase());
        } else if (txt.matches("^[a-z][a-z0-9\\-]*$") || txt.matches("^[A-Z][A-Za-z0-9_]*$")) {
            node = new VariableReference(txt);
        } else {
            node = new PropertyName(txt);
        }

        if (node != null) exprStack.push(node);
    }

    @Override
    public void exitMultiplication(ICSSParser.MultiplicationContext ctx) {
        int count = ctx.atom().size();
        if (count == 0) return;
        ASTNode[] parts = new ASTNode[count];
        for (int i = count - 1; i >= 0; i--) {
            parts[i] = exprStack.pop();
        }

        if (count == 1) {
            exprStack.push(parts[0]);
            return;
        }

        ASTNode result = parts[0];
        for (int i = 0; i < count - 1; i++) {
            String opText = ctx.getChild(2 * i + 1).getText();
            MultiplyOperation mul = new MultiplyOperation();
            mul.addChild((ASTNode) result);
            mul.addChild((ASTNode) parts[i + 1]);
            result = mul;
        }
        exprStack.push(result);
    }

    @Override
    public void exitAddition(ICSSParser.AdditionContext ctx) {
        int count = ctx.multiplication().size();
        if (count == 0) return;
        ASTNode[] parts = new ASTNode[count];
        for (int i = count - 1; i >= 0; i--) {
            parts[i] = exprStack.pop();
        }
        if (count == 1) {
            exprStack.push(parts[0]);
            return;
        }
        ASTNode result = parts[0];
        for (int i = 0; i < count - 1; i++) {
            String opText = ctx.getChild(2 * i + 1).getText();
            if (opText.equals("+")) {
                AddOperation add = new AddOperation();
                add.addChild((ASTNode) result);
                add.addChild((ASTNode) parts[i + 1]);
                result = add;
            } else {
                SubtractOperation sub = new SubtractOperation();
                sub.addChild((ASTNode) result);
                sub.addChild((ASTNode) parts[i + 1]);
                result = sub;
            }
        }
        exprStack.push(result);
    }

    // exits die niks doen, maar er wel zijn voor de volledigheid (een beetje net als ik tijdens de les :) )
    @Override
    public void enterCalculatableValue(ICSSParser.CalculatableValueContext ctx) {
    }

    @Override
    public void exitCalculatableValue(ICSSParser.CalculatableValueContext ctx) {
    }

    @Override
    public void enterAtom(ICSSParser.AtomContext ctx) {
    }

    @Override
    public void exitAtom(ICSSParser.AtomContext ctx) {
    }
}