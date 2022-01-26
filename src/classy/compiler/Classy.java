package classy.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import classy.compiler.analyzing.Checker;
import classy.compiler.analyzing.Variable;
import classy.compiler.lexing.Lexer;
import classy.compiler.lexing.Token;
import classy.compiler.parsing.Parser;
import classy.compiler.parsing.Value;
import classy.compiler.translation.Translator;

public class Classy {
	
	public static void main(String args[]) {
		//The first argument should be the path to the file to compile
		if(args.length == 0)
			System.out.println("The path should be specified as the first argument to the compiler!");
		System.out.println("Compiling \"" + args[0] + "\"...");
		new Classy(args[0]);
	}
	
	public Classy(String pathName) {
		Scanner scan = null;
		try {
			scan = new Scanner(new File(pathName));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Could not find file \"" + pathName + "\"!");
		}
		// remove the file extension from the path name. This will be the module name
		String moduleName;
		if (pathName.indexOf('.') != -1) {
			moduleName = pathName.substring(0, pathName.lastIndexOf('.'));
		}else
			moduleName = pathName;
		
		List<String> lines = new ArrayList<>();
		while(scan.hasNextLine())
			lines.add(scan.nextLine());
		scan.close();
		
		Lexer lex = new Lexer(lines);
		List<Token> tokens = lex.getTokens();
		// strip the whitespace and comment tokens
		// convert all new lines into semicolons, removing excess
		cleanTokens(tokens);
		
		for(Token token: tokens)
			System.out.println(token);
		System.out.println();
		
		Parser parse = new Parser(tokens);
		Value program = parse.getProgram();
		System.out.println("Parsed:");
		System.out.println(program.pretty(0));
		System.out.println();
		
		// Just creating the checker object will run the checker and
		// try to catch any type errors that may be present.
		Checker check = new Checker(program, false); //TODO temporarily disabled optimization
		System.out.println("Optimized:");
		System.out.println(program.pretty(0));
		List<Variable> vars = check.getVariables();
		
		Translator translate = new Translator(program, vars);
		List<String> outLines = translate.getOutLines();
		// Output the lines to fileName.ll
		FileWriter fw = null;
		try {
			fw = new FileWriter(new File(moduleName + ".ll"));
			
			for (String line: outLines)
				fw.write(line + "\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		
		if (fw != null) {
			// Now we are going to want to compile to an executable.
			// We use llc first, then gcc.
			System.out.println(); // put a space between last output and this
			runProcess(List.of("llc", moduleName+".ll", "-o", moduleName+".s"), "Static compiling");
			runProcess(List.of("gcc", moduleName+".s", "-o", moduleName+".exe"), "Linking");
		}
	}
	
	protected void runProcess(List<String> cmd, String pName) {
		StringBuffer buf = new StringBuffer();
		for(String cmdp: cmd) {
			buf.append(cmdp);
			buf.append(" ");
		}
		System.out.println("Running: " + buf.toString());
		
		ProcessBuilder processBuilder = new ProcessBuilder(cmd);
		processBuilder.redirectErrorStream(true);

		try {
			Process process = processBuilder.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append('\n');
				output.append(line);
			}

			int exitCode = process.waitFor();
			if (exitCode != 0)
				throw new RuntimeException(pName + " failed, error code: " + exitCode);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void cleanTokens(List<Token> tokens) {
		boolean lastNewLine = true;
		for(int i=0; i<tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.getType() == Token.Type.SPACE || token.getType() == Token.Type.COMMENT) {
				tokens.remove(i);
				i--;
			}else if (token.getType() == Token.Type.NEW_LINE) {
				if (lastNewLine) {
					tokens.remove(i);
					i--;
				}else {
					lastNewLine = true;
					tokens.remove(i);
					tokens.add(i, new Token(token.getValue(), Token.Type.SEMICOLON,
							token.getLineNo(), token.getColNo()));
				}
			}else
				lastNewLine = false;
		}
	}

}
