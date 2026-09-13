grammar AnomalyDSL;

// ==========================================
// PARSER RULES
// ==========================================

script
    : statement* EOF
    ;

statement
    : bindClause
    | initialStateClause
    | stateBlock
    ;

// state idle --> "zharka/idle.json"
bindClause
    : STATE stateName=ID BIND_ARROW jsonPath=STRING_LITERAL ';'?
    ;

// initial = idle
initialStateClause
    : INITIAL ASSIGN stateName=ID ';'?
    ;

// state idle { ... }
stateBlock
    : STATE stateName=ID '{' transitionRule* '}'
    ;

// when timer(200) and player.entered_zone("trigger") -> warmup
transitionRule
    : WHEN expr=expression TRANSITION_ARROW targetState=ID ';'?
    ;

// Логические выражения с явным приоритетом операций (NOT > AND > OR)
expression
    : '(' expr=expression ')'                           # ParenExpr
    | NOT expr=expression                              # NotExpr
    | left=expression op=AND right=expression          # AndExpr
    | left=expression op=OR right=expression           # OrExpr
    | primaryCondition                                 # PrimaryExpr
    ;

// Атомарные условия DSL
primaryCondition
    : TIMER '(' ticks=INT ')'                           # TimerCondition
    | CHANCE '(' chanceVal=NUMBER ')'                   # ChanceCondition
    | PLAYER '.' eventName=ID '(' zone=STRING_LITERAL ')' # PlayerZoneCondition
    ;

// ==========================================
// LEXER RULES
// ==========================================

STATE   : 'state' ;
INITIAL : 'initial' ;
WHEN    : 'when' ;
TIMER   : 'timer' ;
CHANCE  : 'chance' ;
PLAYER  : 'player' ;

AND     : 'and' | '&&' ;
OR      : 'or'  | '||' ;
NOT     : 'not' | '!' ;

ASSIGN           : '=' ;
BIND_ARROW       : '-->' ;
TRANSITION_ARROW : '->' ;

ID      : [a-zA-Z_][a-zA-Z0-9_]* ;
INT     : [0-9]+ ;
NUMBER  : [0-9]+ ('.' [0-9]+)? ;

STRING_LITERAL
    : '"' ( '\\"' | ~["\r\n] )* '"'
    ;

WS            : [ \t\r\n]+ -> skip ;
LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;