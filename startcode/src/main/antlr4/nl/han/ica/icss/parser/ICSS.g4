grammar ICSS;

stylesheet
    : statement* EOF
    ;

variableAssignment
    : (LOWER_IDENT | CAPITAL_IDENT) ASSIGNMENT_OPERATOR calculatableValue SEMICOLON
    ;

statement
    : variableAssignment
    | ifStatement
    | styleRule
    | declaration
    ;

ifStatement
    : IF BOX_BRACKET_OPEN condition BOX_BRACKET_CLOSE block (ELSE block)?
    ;

condition
    : TRUE
    | FALSE
    | calculatableValue
    ;

block
    : OPEN_BRACE (variableAssignment |ifStatement | styleRule | declaration)* CLOSE_BRACE
    ;

styleRule
    : selector block
    ;

selector
    : ID_IDENT
    | CLASS_IDENT
    | LOWER_IDENT
    | CAPITAL_IDENT
    ;

declaration
    : LOWER_IDENT COLON calculatableValue SEMICOLON
    ;

calculatableValue
    : addition
    ;

addition
    : multiplication (op=(PLUS | MIN) multiplication)*
    ;

multiplication
    : atom (op=MUL atom)*
    ;

atom
    : value
    | BOX_BRACKET_OPEN calculatableValue BOX_BRACKET_CLOSE
    ;

value
    : COLOR
    | PIXELSIZE
    | PERCENTAGE
    | SCALAR
    | TRUE
    | FALSE
    | ID_IDENT
    | CLASS_IDENT
    | LOWER_IDENT
    | CAPITAL_IDENT
    ;

/* --- LEXER --- */

IF: 'if';
ELSE: 'else';
BOX_BRACKET_OPEN: '[';
BOX_BRACKET_CLOSE: ']';

/* boolean keywords must match the lowercase input and come before identifiers */
TRUE: 'true';
FALSE: 'false';

PIXELSIZE: [0-9]+ 'px';
PERCENTAGE: [0-9]+ '%';
SCALAR: [0-9]+;

/* accept uppercase hex as well */
COLOR: '#' [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ;

ID_IDENT: '#' [a-zA-Z0-9\-]+;
CLASS_IDENT: '.' [a-zA-Z0-9\-]+;

/* place VAR before generic identifier rules */
VAR: 'var';

LOWER_IDENT: [a-z] [a-z0-9\-]*;
CAPITAL_IDENT: [A-Z] [A-Za-z0-9_]*;

WS: [ \t\r\n]+ -> skip;

OPEN_BRACE: '{';
CLOSE_BRACE: '}';
SEMICOLON: ';';
COLON: ':';
PLUS: '+';
MIN: '-';
MUL: '*';
ASSIGNMENT_OPERATOR: ':=';