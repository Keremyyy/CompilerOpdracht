package nl.han.ica.icss.transforms;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;

import java.util.HashMap;
import java.util.List;

public class Evaluator implements Transform {

    private IHANLinkedList<HashMap<String, Literal>> variableValues;

    public Evaluator() {}

    @Override
    public void apply(AST ast) {
        if (ast == null || ast.root == null) return;

        variableValues = new HANLinkedList<>();
        variableValues.addFirst(new HashMap<>());

        processNodes(ast.root.body, true);
    }

    private void processNodes(List<ASTNode> nodes, boolean isTopLevel) {
        if (nodes == null) return;

        for (int i = 0; i < nodes.size(); ) {
            ASTNode node = nodes.get(i);

            if (node instanceof VariableAssignment) {
                evaluateVariableAssignment((VariableAssignment) node);
                i++;
            } else if (node instanceof Declaration) {
                evaluateDeclaration((Declaration) node);
                i++;
            } else if (node instanceof Stylerule) {
                processStylerule((Stylerule) node);
                i++;
            } else if (node instanceof IfClause) {
                IfClause ifc = (IfClause) node;

                // IfClause at top-level should not be evaluated by the evaluator
                if (isTopLevel) {
                    ifc.setError("If-expressions are only allowed inside style rules");
                    i++;
                    continue;
                }

                // evaluate the conditional expression
                Literal condLit = null;
                if (ifc.getConditionalExpression() != null) {
                    condLit = evaluateExpression(ifc.getConditionalExpression());
                }

                boolean condTrue = false;
                if (condLit instanceof BoolLiteral) {
                    condTrue = ((BoolLiteral) condLit).value;
                } else if (condLit == null && ifc.getConditionalExpression() instanceof VariableReference) {
                    // try to look up variable value
                    String name = ((VariableReference) ifc.getConditionalExpression()).name;
                    Literal looked = lookupVariableValue(name);
                    if (looked instanceof BoolLiteral) condTrue = ((BoolLiteral) looked).value;
                }

                if (condTrue) {
                    variableValues.addFirst(new HashMap<>());
                    processNodes(ifc.body, false);
                    variableValues.removeFirst();

                    // replace the IfClause node with its body
                    nodes.remove(i);
                    if (!ifc.body.isEmpty()) {
                        nodes.addAll(i, ifc.body);
                        i += ifc.body.size();
                    }
                } else {
                    ElseClause elseC = ifc.getElseClause();
                    if (elseC != null) {
                        variableValues.addFirst(new HashMap<>());
                        processNodes(elseC.body, false);
                        variableValues.removeFirst();

                        nodes.remove(i);
                        if (!elseC.body.isEmpty()) {
                            nodes.addAll(i, elseC.body);
                            i += elseC.body.size();
                        }
                    } else {
                        // no else: remove the IfClause entirely
                        nodes.remove(i);
                        // don't increment i, next element shifts in
                    }
                }

            } else {
                // unknown node type - just skip
                i++;
            }
        }
    }

    private void processStylerule(Stylerule rule) {
        if (rule == null) return;
        // push a new scope for this stylerule
        variableValues.addFirst(new HashMap<>());
        processNodes(rule.body, false);
        // pop stylerule scope
        variableValues.removeFirst();
    }

    private void evaluateVariableAssignment(VariableAssignment va) {
        if (va == null) return;
        Expression expr = va.expression;
        Literal evaluated = evaluateExpression(expr);
        if (evaluated != null) {
            HashMap<String, Literal> scope = variableValues.getFirst();
            if (va.name != null && va.name.name != null && scope != null) {
                scope.put(va.name.name, evaluated);
            }
            // replace the expression in the assignment with the literal
            va.expression = evaluated;
        }
    }

    private void evaluateDeclaration(Declaration decl) {
        if (decl == null) return;
        Expression expr = decl.expression;
        Literal evaluated = evaluateExpression(expr);
        if (evaluated != null) {
            decl.expression = evaluated;
        }
    }

    private Literal evaluateExpression(ASTNode node) {
        if (node == null) return null;

        // If it's already a literal, return it
        if (node instanceof Literal) return (Literal) node;

        // Variable reference: look up value in scopes
        if (node instanceof VariableReference) {
            String name = ((VariableReference) node).name;
            Literal val = lookupVariableValue(name);
            return val; // may be null if undefined
        }

        // Operations: evaluate children
        if (node instanceof AddOperation) {
            AddOperation op = (AddOperation) node;
            Literal left = evaluateExpression(op.lhs);
            Literal right = evaluateExpression(op.rhs);
            return evaluateAdd(left, right);
        }
        if (node instanceof SubtractOperation) {
            SubtractOperation op = (SubtractOperation) node;
            Literal left = evaluateExpression(op.lhs);
            Literal right = evaluateExpression(op.rhs);
            return evaluateSubtract(left, right);
        }
        if (node instanceof MultiplyOperation) {
            MultiplyOperation op = (MultiplyOperation) node;
            Literal left = evaluateExpression(op.lhs);
            Literal right = evaluateExpression(op.rhs);
            return evaluateMultiply(left, right);
        }

        // other nodes not evaluable
        return null;
    }

    private Literal evaluateAdd(Literal a, Literal b) {
        if (a == null || b == null) return null;
        // Pixel + Pixel -> Pixel
        if (a instanceof PixelLiteral && b instanceof PixelLiteral) {
            int sum = ((PixelLiteral) a).value + ((PixelLiteral) b).value;
            return new PixelLiteral(sum);
        }
        // Scalar + Scalar -> Scalar
        if (a instanceof ScalarLiteral && b instanceof ScalarLiteral) {
            int sum = ((ScalarLiteral) a).value + ((ScalarLiteral) b).value;
            return new ScalarLiteral(sum);
        }
        // Percentage + Percentage -> Percentage
        if (a instanceof PercentageLiteral && b instanceof PercentageLiteral) {
            int sum = ((PercentageLiteral) a).value + ((PercentageLiteral) b).value;
            return new PercentageLiteral(sum);
        }
        // unsupported combinations
        return null;
    }

    private Literal evaluateSubtract(Literal a, Literal b) {
        if (a == null || b == null) return null;
        if (a instanceof PixelLiteral && b instanceof PixelLiteral) {
            int res = ((PixelLiteral) a).value - ((PixelLiteral) b).value;
            return new PixelLiteral(res);
        }
        if (a instanceof ScalarLiteral && b instanceof ScalarLiteral) {
            int res = ((ScalarLiteral) a).value - ((ScalarLiteral) b).value;
            return new ScalarLiteral(res);
        }
        if (a instanceof PercentageLiteral && b instanceof PercentageLiteral) {
            int res = ((PercentageLiteral) a).value - ((PercentageLiteral) b).value;
            return new PercentageLiteral(res);
        }
        return null;
    }

    private Literal evaluateMultiply(Literal a, Literal b) {
        if (a == null || b == null) return null;
        // Scalar * Pixel -> Pixel
        if (a instanceof ScalarLiteral && b instanceof PixelLiteral) {
            int res = ((ScalarLiteral) a).value * ((PixelLiteral) b).value;
            return new PixelLiteral(res);
        }
        if (b instanceof ScalarLiteral && a instanceof PixelLiteral) {
            int res = ((ScalarLiteral) b).value * ((PixelLiteral) a).value;
            return new PixelLiteral(res);
        }
        // Scalar * Scalar -> Scalar
        if (a instanceof ScalarLiteral && b instanceof ScalarLiteral) {
            int res = ((ScalarLiteral) a).value * ((ScalarLiteral) b).value;
            return new ScalarLiteral(res);
        }
        return null;
    }

    private Literal lookupVariableValue(String name) {
        if (name == null) return null;
        int scopes = variableValues.getSize();
        for (int i = 0; i < scopes; i++) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope != null && scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }
}

