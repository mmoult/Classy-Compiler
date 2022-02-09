# Classy-Compiler
Compiler for the "Classy" programming language.

Classy is concise and consistent pure functional language that operates on immutable variables, functions, and user-defined types with a familiar imperative-style syntax. To learn more about the Classy language, read [Language Guide](./docs/lang_guide.md).

## Table of contents
* [Technologies](#technologies)
* [Setup](#setup)
* [Use](#use)

## Technologies
* Java version of at least 1.9 to compile
* LLVM 13.0.0 (for translation from LLVM IR to assembly)
* GNU Compiler Collection (for assembly and linking of LLVM output)

## Setup
For downloading LLVM, consult [LLVM Releases](https://github.com/llvm/llvm-project/releases/tag/llvmorg-13.0.0). The Classy Compiler uses LLVM from the command line, meaning that "llc" needs to be on the path.

GCC is also used to assemble the output from LLVM. It is also used on the command line, thus, "gcc" needs to be on the path.

## Use
Specify the path of a file or directory to compile in the command line program. Customization flags are available for use, a complete and updated list of which can be shown by using the -help flag, i.e. "jar Classy.jar -help".
