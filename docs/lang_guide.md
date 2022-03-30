# Classy Language Guide
All Classy programs evaluate to a value. Being a pure language, the job of the program is to calculate this value for printing at termination. To perform this calculation, programs consist of expressions as simple as assignments or as complex as blocks of recursive operations.

Subexpressions are a subset of expressions which must result in a value. The vast majority of all expressions are technically considered subexpressions. Some common subexpressions include literals, operations, references, if-else constructs, and function calls. Subexpressions can be chained together and used interchangeably. A chain of one or more subexpressions is often referred to as a single value, since one result will be produced from their complete evaluation.

## Topics
* [Assignments](#assignments)
	+ [Naming](#naming)
	+ [Functions](#functions)
* [References](#references)
* [Operators](#operators)
* [Conditionals](#conditionals)
* [Blocks](#blocks)
* [Types](#types)
	+ [Inheritance](#inheritance)
	+ [Generics](#generics)
* [Documentation](#documentation)

## Assignments
Assignments, which consist of both variable and function definitions, are very useful in conjunction with subexpressions. All assignments follow the form:
```
let DEFINED = VALUE
```
where "DEFINED" is a variable name or function name and parameters, and the value is what the variable or function should result in. For example, the definition of variable `foo` to a value of `1` would be written as: `let foo = 1`.

Common values to use for assignments include literals (such as `6` or `-13`), operators (see [Operators](#operators)), blocks (see [Blocks](#blocks)), and conditionals (see [Conditionals](#conditionals)).

Assignments are not subexpressions, and thus, cannot be used directly in values. However, they can be defined in blocks, and will be usable for all statements beneath them in that block.

### Naming
The name used for a value assignment must be a single identifier. Indentifiers are arbitrary sequences of symbols beginning with a non-numeric symbol and terminating at the first prohibited symbol, most prominent of which is whitespace. Other prohibited symbols include punctuation (such as `.`, `,`, `?`, and `!`), braces and parentheses (`(`, `)`, `{`, `}`), and common math and logical operators (`+`, `-`, `*`, `/`, `&`, `|`). 

### Functions
Function definitions are a specific type of assignment which include function parameters. A function can have any nonnegative number of arguments, including 0. Each argument name must be separated from others by a comma in the parameter list. For example, a function definition with three arguments would look like:
```
let NAME(A, B, C) = VALUE
```
where `NAME` is the function name, and the three parameters are named `A`, `B`, and `C`. These three parameter names can be used in the function value. Thus, a function that will simply sum its three arguments may be defined as:
```
let sum3(x, y, z) = x + y + z
```
Furthermore, the function itself may be used recursively in the value definition. To reduce redundancy, `self` may be used instead of the function name.

The value of the function operates in the same scope of the assignment. This means that function values can use references to assignments earlier on in the scope. If the definition for function B appears after the definition for variable A in the same scope, the value of function B will be able to use variable A. This is illustrated below:
```
let A = 2
let B(x) = A + x
```
Calling B() would yield the proper result of A + x.

Default values may be specified for any parameter in the parameter list. Then, the function caller may omit the corresponding argument if no specification is desired. Consider the refinement of the previous sum function:
```
let sum(first = 0, second = 0, third = 0) = first + second + third
```

## References
At their simplest, a reference is a single identifier which refers to the value defined by an earlier assignment in the same or an enclosing scope. The identifier of the reference must be identical to the identifier of the assignment to ensure connection.

References to function assignments may include arguments, which will be used in place of the assignment parameters when evaluating the function value. The reference's arguments, if any, follow the reference identifier. By default, arguments given will correspond to the parameters in the parameter list by sequential order.

If a single argument is given, no surrounding parentheses are needed. However, if more than one argument is given, each argument must be separated by a comma and all arguments must be encapsulated in a set of parentheses. For example, calling the previously described function `sum` may be done by:

`sum 3`, `sum (4, 2)`, or `sum (7, 1, 4)`

which would yield 3, 6, and 13, respectively.

Calling a function without any arguments requires an empty argument list. This can be done by using the `void` keyword (such as `sum void`), or by using an empty set of parentheses (such as `sum()`).

For clarity, and especially with use of default parameter values in a functional assignment, it may be useful to label an argument with the intended parameter. To do so, use the value preceded by the paremeter name and the assignment symbol `=`. When using argument labeling, arguments do not need to be given in the same order that they are defined in the function parameter list. For example, consider the program excerpt below:
```
let divide(dividend, divisor) = dividend / divisor
let first = divide (6, 2)
let second = divide (divisor = 2, 6)
let third = divide (2, dividend=6)
let fourth = divide (divisor = 2, dividend = 6)
```
The result of all four calls would be 3, since 6 / 2 = 3.

## Operators
Classy supports many common math and logical operators. Note that all logical operators consider any non-zero value as true, and will return 1 if true or 0 if false.

Binary operators include:
- `x + y`: adds x and y
- `x - y`: subtracts y from x
- `x * y`: multiplies x and y
- `x / y`: divides x by y
- `x & y`: short circuiting logical x and y
- `x | y`: short circuiting logical x or y
- `x == y`: logical equivalence comparison of x and y
- `x <> y`: logical nonequivalence comparison of x and y
- `x < y`: true if x is strictly less than y
- `x <= y`: true if x is less than or equal to y
- `x > y`: true if x is strictly greater than y
- `x >= y`: true if x is greater than or equal to y

Unary operators include:
- `! x`: logical not of x
- `- x`: negation of x

Operations can be arbitrarily nested within each other. To dictate a precedence, use parentheses. Otherwise, precendence rules are implied based on the operator type, and left to right for equal precedence. Note that functions receive their arguments before even the highest operator precedence.

## Conditionals
If-then-else is the primary conditional construction. If the condition results to nonzero, then the "then" condition will be evaluated. Otherwise, the else condition will be evaluated. The general form is:
```
if CONDITION
	THEN
else
	ELSE
```
where `CONDITION`, `THEN`, and `ELSE` are all values.

There are different conditional brace styles, and Classy accommodates them. If the THEN or ELSE value occurs on the same line as the CONDITION, then there must be a semicolon to divide between the condition and the value. Consider the following program excerpts which set `threshold` to 0 if `num` is no greater than 5, or 1 otherwise:

K&R:
```
if num <= 5; {
	let threshold = 0
	...
}else; {
	let threshold = 1
	...
}
```
Allman:
```
if num <= 5
{
	let threshold = 0
	...
}
else
{
	let threshold = 1
	...
}
```
...

An multi-branched conditional can be formed by putting another if, condition, and value before the else keyword. The general form is:
```
if CONDITION1
	THEN
if CONDITION2
	THEN2
else
	ELSE
```
where `CONDITION1`, `CONDITION2`, `THEN1`, `THEN2`, and `ELSE` are all values. An arbitrary number of branches may be used with the same multi-branching form. 

In fact, a conditional construct does not need to have more than one explicit branch. In imperative programming languages, there is a construct known as the "one-armed if", or simply the "if without an else". Such a construct is not technically possible in Classy, but a mimicking construct is possible by the following form:
```
if CONDITION
	value
EXPRESSIONS
```
where "EXPRESSIONS" is any positive number of expressions. For example, consider the program excerpt below, which returns 1 if `foo` is true, but otherwise calculates `bar` and returns `bar / 5`:
```
if foo
	1
let bar = 3 + 6 * foo
bar / 5
```

## Blocks
Blocks are subexpressions which contain exactly one value, and may contain any number of assignments. Blocks are defined explicitly with open and close braces ({ ... }). Since blocks are regular subexpressions, they can substitute any value. For example, function definitions may regularly employ blocks for clarity and to define and use temporary variables. Consider the following program excerpt, which defines a function named `pascal`:
```
#|  Returns an array holding the values of
 |  Pascal's triangle at the specified row.
 |  Rows are calculated beginning with row
 |# 0 yielding [1].
let pascal(row) = {
	if row == 0
		[1]
	let previous = pascal(row - 1)
	# pad previous with 0's on both sides
	let padded = [0] + previous + [0]
	let build(prev) = {
		if prev len == 1
			sofar
		prev at 0 + prev at 1 + build prev.rest
	}
	build padded
}
```
Observe that the value of the function is a block. Within that block, there are conditional constructs, variable definitions, and a recursive function definition (which itself is a block).

## Types
Classy is a statically typed language with nominally-typed inheritance and dynamic dispatch. 

Types are automatically assumed in assignments. Type annotations may optionally be provided that will require the type of the value to be a subtype of the specified type. For example, `let myVar: T = value` will set the type of myVar to T, assuming that T is a supertype of value's type.

Type annotations may also be provided for parameters of function declarations (`let foo(a: Num, b: Num) = a + b`) or for function returns (`let foo(a): Num = 10`).

New types may be specified with the syntax:
```
type NAME = FIELD_LIST
```
where NAME is any valid identifier (though convention dictates the type should be capitalized), and the field list is a list of zero or more variables in the same format as a function parameter list. For example, a type `Foo` with fields a and b could be specified thus:
```
type Foo = (a: A, b: B)
```
If only one field is given, no parentheses are required:
```
type Bar = a: A
```

To construct a type, all fields without default values must be provided. For the example type `Foo`, a construction may look like:
```
Foo (myA, myB)
```
where myA is a value of type A and myB is a value of type B.

If `Foo` was defined with default values such as:
```
type Foo = (a = 2, b = 3)
```
then a void construction call may be used for Foo. As with regular functions, the fields may be provided anyway, or fields may be given in a different order if they have the correct labels (see [Functions](#functions)).

Object members may be accessed by the dot notation or separated by a space. If `foo` is some instance of the previously defined type, Foo, then `foo.a` or `foo a` would give the value of `a` in `foo`. 

Methods can be defined for a type with a syntax similar to a regular function definition. Assuming that type `Dog` was previously defined, a function `makeNoise` can be defined as such:
```
let Dog.makeNoise() = 0
```

In a method definition, the period dividing the type name and method name is necessary. However, the method can be accessed from the object in the same way that a field can be: Given some dog object, `fido`, the `makeNoise()` method can be called by either `fido.makeNoise()` or `fido makeNoise()`.

### Inheritance
Subtyping may be specified using the `isa` keyword. Suppose that A is a subtype of some pre-defined type B. In A's declaration, this relationship could be represented as such:
```
type A isa B = ...
```

If A is a subtype of B, then all members (fields and methods) belonging to B are available to A. In short, an object A can be used where any object B is expected. 

The built-in types of Any, Int, and Bool can be extended as expected. For example, an if expression requires a Bool condition. Thus, using subtyping, the following code will result in 5:
```
type MyBool isa Bool = void
let mb = MyBool true
if mb
	5
-1
```

Subtyping also provides the ability to overload functions, where more specific variants can be used for the subtype.
...

Classy ~~supports~~ *will support* complex inheritance relations, including multiple inheritance and default parent values. Each super type may be listed, delimited by commas. `type A isa B, C = ...` Default parent values may be provided literally (`type A isa 5 = ...`) or with a type label (`type A isa (Bool = true) = ...`). 

### Generics
...

## Documentation
It is good practice to include comments to document the reasoning behind program approach and execution. All comments begin with the pound character (`#`). By default, a comment will go from the `#` until the end of the line. However, a comment can be made to span multiple lines with `#|` `|#` pairs, where `#|` is the opening, and `|#` is the closing. For example:
```
# Single-line comment goes until the end of the line
#| Multi-line comments cover from the open
and go until the line of the close |#
```

It is important to note that multi-line comments go from the open `#|` to the end of the line the close `#|`. The purpose of this is twofold: First, documentation can be more compact and symmetrical. Second, all statements are the first non-whitespace characters of the line, which increases readability and precludes statements hiding after long comments.

Two different documentation styles are recommended:
```
#|  explanation that may span several lines
 |  and would not easily fit in a single line,
 |# yet still obey line length guidelines.
```
or
```
#| explanation that may span several lines
   and would not easily fit in a single line,
|# yet still obey line length guidelines.
```

Multi-line comments may be nested within each other, with the entire comment closing only after the last matching close. This can prove very helpful when commenting out large sections of code for debugging purposes, since multi-line comments in the commented portion will not interfere unexpectedly with the encapsulating comment. 


