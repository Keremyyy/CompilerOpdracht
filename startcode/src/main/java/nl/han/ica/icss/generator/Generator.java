package nl.han.ica.icss.generator;

import java.util.stream.Collectors;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.BoolLiteral;
import nl.han.ica.icss.ast.literals.ColorLiteral;
import nl.han.ica.icss.ast.literals.PercentageLiteral;
import nl.han.ica.icss.ast.literals.PixelLiteral;
import nl.han.ica.icss.ast.literals.ScalarLiteral;

public class Generator {

    public String generate(AST ast) {
        if (ast == null || ast.root == null) return "";
        return generateStylesheet(ast.root);
    }

    private String generateStylesheet(Stylesheet stylesheet) {
        StringBuilder css = new StringBuilder();
        for (ASTNode child : stylesheet.getChildren()) {
            if (child instanceof Stylerule) {
                css.append(generateStylerule((Stylerule) child)).append("\n");
            }
        }
        return css.toString();
    }

    private String generateStylerule(Stylerule stylerule) {
        StringBuilder css = new StringBuilder();
        String selectors = stylerule.selectors.stream()
                .map(ASTNode::toString)
                .collect(Collectors.joining(", ")) + " {\n";
        css.append(selectors);
        css.append(generateDeclarations(stylerule));
        css.append("}\n");
        return css.toString();
    }

    private String generateDeclarations(Stylerule stylerule) {
        StringBuilder css = new StringBuilder();
        for (ASTNode child : stylerule.getChildren()) {
            if (child instanceof Declaration) {
                css.append("  ");
                css.append(generateDeclaration((Declaration) child));
            }
        }
        return css.toString();
    }

    private String generateDeclaration(Declaration declaration) {
        String prop = declaration.property != null ? declaration.property.name : "";
        String val = declaration.expression != null ? generateExpression(declaration.expression) : "";
        return prop + ": " + val + ";\n";
    }

    private String generateExpression(Expression expression) {
        if (expression == null) return "";

        String className = expression.getClass().getSimpleName();
        switch (className) {
            case "PercentageLiteral":
                return ((PercentageLiteral) expression).value + "%";
            case "PixelLiteral":
                return ((PixelLiteral) expression).value + "px";
            case "ColorLiteral":
                return ((ColorLiteral) expression).value;
            case "ScalarLiteral":
                return Integer.toString(((ScalarLiteral) expression).value);
            case "BoolLiteral":
                return ((BoolLiteral) expression).value ? "true" : "false";
            default:
                return expression.toString();
        }
    }
}
