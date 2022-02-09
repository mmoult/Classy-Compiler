# Classy Language Guide
All Classy programs evaluate to a value. Being a pure language, the job of the program is to calculate this value for printing at termination. To perform this calculation, programs consist of expressions as simple as assignments or as complex as blocks of recursive operations.

Subexpressions are a subset of expressions which must result in a value. The vast majority of all expressions are technically considered subexpressions. Some common subexpressions include literals, references, if-else constructs, and function calls. Subexpressions can be chained together and used interchangeably. A chain of one or more subexpressions is often referred to as a single value, since one result will be produced from their complete evaluation.

## Topics
* [Assignments](#assignments)
* [Blocks](#blocks)
* [Comments](#comments)
* [Conditionals](#conditionals)

## Assignments
Assignments, which consist of both variable and function definitions, are very useful in conjunction with subexpressions. All assignments follow the form:
```
let DEFINED = VALUE
```
where "DEFINED" is a variable name or function name and parameters, and the value is what the variable or function should result in. For example, the definition of variable `foo` to a value of `1` would be written as: `let foo = 1`.

Function definitions follow the same pattern but also include the function parameters. A function can have any nonnegative number of arguments, including 0. Each argument name should be separated from others by a comma in the parameter list. For example, a function definition with three arguments would look like:
```
let NAME(A, B, C) = VALUE
```
where "NAME" is the function name, and the three parameters are named "A", "B", and "C". These three parameter names can be used in the function value. Thus, a function that will simply sum its three arguments may be defined as:
```
let sum3(x, y, z) = x + y + z
```

Assignments are not subexpressions, and thus, cannot be used directly in values. However, they can be defined in blocks, and will be usable for all statements beneath them in that block.

## Blocks
Blocks are subexpressions which contain exactly one value, and may contain any number of assignments. Blocks are defined explicitly with open and close braces ({ ... }). Since blocks are regular subexpressions, they can substitute any value. For example, function definitions may regularly employ blocks for clarity and to define and use temporary variables. Consider the following program excerpt, which defines a function named `pascal`:
```
#|  Returns an array holding the values of
 |  Pascal's triangle at the specified row.
 |  Rows are calculated beginning with row
 |# 0 yielding [1].
let pascal(row: Nat) = {
	if row == 0
		[1]
	let previous = pascal(row - 1)
	# pad previous with 0's on both sides
	let padded = [0] + previous + [0]
	let build(prev) = {
		if prev len == 1
			sofar
		prev at 0 + prev at 1 + build (prev rest)
	}
	build padded
}
```
Observe that the value of the function is a block. Within that block, there are conditional constructs, variable definitions, and a recursive function definition (which itself is a block).

## Comments
It is good practice to include comments to document the reasoning behind program approach and execution. All comments begin with the pound character (#). By default, a comment will go from the "#" until the end of the line. However, a comment can be made to span multiple lines with "#|" "|#" pairs, where "#|" is the opening, and "|#" is the closing. For example:
```
# Single-line comment goes until the end of the line
#| Multi-line comments cover from the open
and go until the line of the close |#
```

It is important to note that multi-line comments go from the open "#|" to the end of the line the close "#|". The purpose of this is twofold: First, documentation can be more compact and symmetrical. Second, all statements are the first non-whitespace characters of the line, which increases readability and precludes statements hiding after long comments.

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

## Conditionals
If-then-else is the primary conditional construction. If the condition results to nonzero, then the "then" condition will be evaluated. Otherwise, the else condition will be evaluated. The general form is:
```
if CONDITION
	THEN
else
	ELSE
```
where "CONDITION", "THEN", and "ELSE" are all values.

There are many different conditional brace styles, and Classy cleanly accommodates all of them. Consider the following program excerpts which set `threshold` to 0 if `num` begins no more than 5, or 1 if `num` begins greater than 5:

K&R:
```
if myNum <= 5 {
	let threshold = 0
	...
}else {
	let threshold = 1
	...
}
```
Allman:
```
if myNum <= 5
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

If the "THEN" or "ELSE" value is not a block, then it must occur on the line after the `if` or `else`, or after a semicolon:
```
if myCondition
	value
```
or
```
if myCondition; value
```

### One-Armed If
The if-else construct is a subexpression, and as such, it must evaluate to a single value in all cases. However, it is common to encounter a case where more processing is needed if the condition is not met. In imperative programming languages, there is a construct known as the "one-armed if", or simply the "if without an else". Such a construct is not technically possible in Classy, but a mimicking construct is possible by using an implicit else and block:
```
if myCondition
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
