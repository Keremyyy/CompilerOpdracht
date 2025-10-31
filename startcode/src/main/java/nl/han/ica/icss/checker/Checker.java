// java
package nl.han.ica.icss.checker;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;
import nl.han.ica.icss.ast.types.ExpressionType;

import java.util.HashMap;

public class Checker {

    private IHANLinkedList<HashMap<String, ExpressionType>> variableTypes;

    public void check(AST ast) {
        variableTypes = new HANLinkedList<>();
        // push global scope (lege map)
        variableTypes.addFirst(new HashMap<>());

        if (ast == null || ast.root == null) return;

        // Als ee nif hier komt dan is dat en illegale if
        for (ASTNode child : ast.root.getChildren()) {
            if (child instanceof IfClause) {
                // if expressies mogen alleen in style rules
                ((IfClause) child).setError("If-expressions are only allowed inside style rules");
                continue;
            }
            checkNode(child);
        }
    }

    // Node checker die bepaald wat voor node het is en de juiste handler aanroept
    private void checkNode(ASTNode child) {
        if (child instanceof VariableAssignment) {
            handleVariableAssignment((VariableAssignment) child);
        } else if (child instanceof Stylerule) {
            checkStyleRule((Stylerule) child);
        } else if (child instanceof Declaration) {
            checkDeclaration((Declaration) child);
        } else if (child instanceof IfClause) {
            handleIfClause((IfClause) child);
        }
    }

    private void handleIfClause(IfClause ifc) {
        if (ifc == null) return;

        // Kijkt of de if wel een boolean terug geeft
        Expression cond = ifc.getConditionalExpression();
        ExpressionType condType = inferExpressionType(cond);
        if (condType != ExpressionType.BOOL) {
            ifc.setError("Dit is geen BOOL. en de IF gemeenschap accepteerd geen" + condType);
        }

        // checkt nieuwe scope voor if-body
        variableTypes.addFirst(new HashMap<>());
        // checkt body nodes
        for (ASTNode node : ifc.body) {
            checkNode(node);
        }
        // pop if-body scope
        variableTypes.removeFirst();

        // else (if present) krijgt zijn eigen scope
        ElseClause elseC = ifc.getElseClause();
        if (elseC != null) {
            variableTypes.addFirst(new HashMap<>());
            for (ASTNode node : elseC.body) {
                checkNode(node);
            }
            variableTypes.removeFirst();
        }
    }

    private void handleVariableAssignment(VariableAssignment var) {
        if (var == null) return;
        String name = (var.name != null) ? var.name.name : null;
        Expression expr = var.expression;
        ExpressionType type = inferExpressionType(expr);

        if (type == ExpressionType.UNDEFINED) {
            // Als er niet duidelijk is wat voor type het is (Of als het geen type is) dan word deze foutmelding aangemaakt
            var.setError("Dit is of een lege variable of een variable van het type'" + name + "'");
        }

        // store in current (global) scope als een map entry
        HashMap<String, ExpressionType> scope = variableTypes.getFirst();
        if (name != null && scope != null) {
            scope.put(name, type);
        }
    }

    private void checkStyleRule(Stylerule rule) {
        if (rule == null) return;

        variableTypes.addFirst(new HashMap<>());
        for (ASTNode child : rule.getChildren()) {
            // style regels mogen bestaan uit declaraties, if of variable assignments
            checkNode(child);
        }

        variableTypes.removeFirst();
    }

    private void checkDeclaration(Declaration decl) {
        if (decl == null) return;
        Expression expr = decl.expression;
        ExpressionType type = inferExpressionType(expr);
        if (type == ExpressionType.UNDEFINED) {
            decl.setError("Undefined expression in declaration '" + ((decl.property != null) ? decl.property.name : "?") + "'");
            return;
        }

        // forceert dat alleen de toegestaande properties en types worden gebruikt
        String propName = (decl.property != null) ? decl.property.name : null;
        if (propName == null) {
            decl.setError("Missing property name in declaration");
            return;
        }
        String normalized = propName.toLowerCase();
        switch (normalized) {
            case "color":
            case "background-color":
                if (type != ExpressionType.COLOR) {
                    decl.setError("Property '" + propName + "' requires a color value (hex #rrggbb)");
                }
                break;
            case "width":
            case "height":
                if (!(type == ExpressionType.PIXEL || type == ExpressionType.PERCENTAGE)) {
                    decl.setError("Property '" + propName + "' requires a size in pixels (px) or percentage (%)");
                }
                break;
            default:
                decl.setError("Property '" + propName + "' is not allowed");
                break;
        }
    }

    private ExpressionType inferExpressionType(ASTNode node) {
        if (node == null) return ExpressionType.UNDEFINED;

        if (node instanceof PixelLiteral) return ExpressionType.PIXEL;
        if (node instanceof PercentageLiteral) return ExpressionType.PERCENTAGE;
        if (node instanceof ColorLiteral) return ExpressionType.COLOR;
        if (node instanceof ScalarLiteral) return ExpressionType.SCALAR;
        if (node instanceof nl.han.ica.icss.ast.literals.BoolLiteral) return ExpressionType.BOOL;

        if (node instanceof VariableReference) {
            VariableReference varRef = (VariableReference) node;
            String name = varRef.name;
            ExpressionType found = lookupVariableType(name);
            if (found == null) {
                varRef.setError("Undefined variable '" + name + "'");
                return ExpressionType.UNDEFINED;
            }
            return found;
        }

        if (node instanceof AddOperation) {
            AddOperation addOp = (AddOperation) node;
            ExpressionType left = inferExpressionType(addOp.lhs);
            ExpressionType right = inferExpressionType(addOp.rhs);
            if (left == ExpressionType.UNDEFINED || right == ExpressionType.UNDEFINED) return ExpressionType.UNDEFINED;
            if (left == right) return left;
            return ExpressionType.UNDEFINED;
        }
        if (node instanceof nl.han.ica.icss.ast.operations.SubtractOperation) {
            SubtractOperation subOp = (SubtractOperation) node;
            ExpressionType left = inferExpressionType(subOp.lhs);
            ExpressionType right = inferExpressionType(subOp.rhs);
            if (left == ExpressionType.UNDEFINED || right == ExpressionType.UNDEFINED) return ExpressionType.UNDEFINED;
            if (left == right) return left;
            return ExpressionType.UNDEFINED;
        }
        if (node instanceof nl.han.ica.icss.ast.operations.MultiplyOperation) {
            MultiplyOperation mulOp = (MultiplyOperation) node;
            ExpressionType left = inferExpressionType(mulOp.lhs);
            ExpressionType right = inferExpressionType(mulOp.rhs);
            if (left == ExpressionType.UNDEFINED || right == ExpressionType.UNDEFINED) return ExpressionType.UNDEFINED;
            // Scalar en scalar mag wel maar pixels mogen niet rekenen met pixels
            if ((left == ExpressionType.SCALAR && right == ExpressionType.PIXEL) ||
                    (left == ExpressionType.PIXEL && right == ExpressionType.SCALAR)) {
                return ExpressionType.PIXEL;
            }

            if (left == ExpressionType.PIXEL && right == ExpressionType.PIXEL){
                mulOp.setError("REKEN POLITIE. u mag hier helemaal niet rekenen met pixels!");
            }
            if (left == ExpressionType.SCALAR && right == ExpressionType.SCALAR) return ExpressionType.SCALAR;
            return ExpressionType.UNDEFINED;
        }
        return ExpressionType.UNDEFINED;
    }

    private ExpressionType lookupVariableType(String name) {
        if (name == null) return null;
        int scopes = variableTypes.getSize();
        for (int i = 0; i < scopes; i++) {
            HashMap<String, ExpressionType> scope = variableTypes.get(i);
            if (scope == null) continue;
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }
}