package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import classy.compiler.Classy;
import classy.compiler.analyzing.CheckException;

class IntegrationTest {
	
	private void expectFromProgram(List<String> lines, Object result) {
		Map<String, String> flags = new HashMap<>();
		// Run the program in optimized and unoptimized
		flags.put("O0", null);
		expectFromProgram(lines, result, flags);
		flags.clear();
		expectFromProgram(lines, result, flags);
	}
	
	private void expectFromProgram(List<String> lines, Object result, Map<String, String> flags) {
		new Classy("a.exe", lines, flags);
		ProcessResult res = runProcess(List.of("a.exe"));
		// If everything went well, then the exit code should be 0
		assertEquals(0, res.exitCode);
		// Also verify that we got the result we were looking for
		assertEquals("\n"+result, res.output);
		File file = new File("a.exe");
		file.delete(); // dispose of the file we made
	}
	
	@Test
	void literal() {
		List<String> lines = List.of(
			"-2"
		);
		expectFromProgram(lines, -2);
	}
	
	@Test
	void nestedFunction() {
		List<String> lines = List.of(
			"let foo(x, y, z) = {",
			"	let bar(x, y, z) = {",
			"		x + y + z",
			"	}",
			"	bar(x, y, z)",
			"}",
			"foo (1, 2, 3)"
		);
		expectFromProgram(lines, 6);
	}
	
	@Test
	void varBlock() {
		List<String> lines = List.of(
			"let foo = 10",
			"foo"
		);
		expectFromProgram(lines, 10);
	}
	
	@Test
	void function() {
		List<String> lines = List.of(
			"let negate(num) = -num",
			"negate(-1)"
		);
		expectFromProgram(lines, 1);
	}
	
	@Test
	void operators() {
		List<String> lines = List.of(
			"3 - 6 / 2 <> 1"
		);
		expectFromProgram(lines, true);
	}
	
	@Test
	void parentheses() {
		List<String> lines = List.of(
			"2 * (3 - 5) >= 0"
		);
		expectFromProgram(lines, false);
	}
	
	@Test
	void undefinedVar() {
		List<String> lines = List.of(
			"let fx(a) = {",
			"	a + 3",
			"}",
			"fx(1) - a"
		);
		assertThrows(CheckException.class, () -> {expectFromProgram(lines, 1);});
	}
	
	@Test
	void voidCall() {
		List<String> lines = List.of(
			"let voidcaller() = {",
			"	7",
			"}",
			"voidcaller void"
		);
		expectFromProgram(lines, 7);
	}
	
	@Test
	void fxPrecedence() {
		List<String> lines = List.of(
			"let sum3or5(max) = {",
			"	if max < 3",
			"		0",
			"	sum3or5(max - 1) + \\",
			"	if (max % 3 == 0) | (max % 5 == 0)",
			"		max",
			"	else",
			"		0",
			"}",
			"sum3or5 10"
		);
		expectFromProgram(lines, 33);
	}
	
	@Test
	void recursiveFx() {
		List<String> lines = List.of(
			"let factorial(num) = {",
			"	if num <= 0",
			"		1",
			"	num * self(num - 1)",
			"}",
			"factorial 4"
		);
		expectFromProgram(lines, 24);
	}
	
	@Test
	void functionalExternality() {
		List<String> lines = List.of(
			"let global = 1",
			"let foo() = global",
			"foo void"
		);
		expectFromProgram(lines, 1);
	}
	
	@Test
	void paramDefaultValue() {
		List<String> lines = List.of(
			"let fx(baz = 3, foo = 2) = baz + foo",
			"fx()"
		);
		expectFromProgram(lines, 5);
	}
	
	@Test
	void labeledArguments() {
		List<String> lines = List.of(
			"let divide(dividend, divisor) = dividend / divisor",
			"let first = divide (6, 2)",
			"let second = divide (divisor = 2, 6)",
			"let third = divide (2, dividend=6)",
			"let fourth = divide (divisor = 2, dividend = 6)",
			"first + second + third + fourth"
		);
		expectFromProgram(lines, 3*4);
	}
	
	@Test
	void typeAnnotatedFx() {
		List<String> lines = List.of(
			"let subFive(value: Int): Int = value - 5",
			"subFive 8"
		);
		expectFromProgram(lines, 3);
	}
	
	@Test
	void dynamicIfTrue() {
		List<String> lines = List.of(
			"if true",
			"	false",
			"else",
			"	5"
		);
		expectFromProgram(lines, "false");
	}
	@Test
	void dynamicIfFalse() {
		List<String> lines = List.of(
			"if false",
			"	false",
			"else",
			"	5"
		);
		expectFromProgram(lines, "5");
	}
	
	protected ProcessResult runProcess(List<String> cmd) {
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
			//System.out.println(output.toString());

			int exitCode = process.waitFor();
			return new ProcessResult(exitCode, output.toString());
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return new ProcessResult(-1, null);
	}
	
	protected static class ProcessResult {
		public final int exitCode;
		public final String output;
		
		public ProcessResult(int exitCode, String output) {
			this.exitCode = exitCode;
			this.output = output;
		}
	}
}
