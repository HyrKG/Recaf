package me.coley.recaf.assemble;

import javassist.ClassPool;
import me.coley.recaf.assemble.ast.PrintContext;
import me.coley.recaf.assemble.ast.Unit;
import me.coley.recaf.assemble.transformer.AstToMethodTransformer;
import me.coley.recaf.assemble.transformer.BytecodeToAstTransformer;
import me.coley.recaf.assemble.util.ClassSupplier;
import me.coley.recaf.assemble.validation.ValidationMessage;
import me.coley.recaf.assemble.validation.ast.AstValidator;
import me.coley.recaf.util.AccessFlag;
import me.coley.recaf.util.IOUtil;
import me.coley.recaf.util.Types;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic setup to validate all bytecode assembly files in the test resources directory complete the entire
 * assembly process, generating verifiable methods.
 */
public class MethodParseTests extends JasmUtils {
	private static final boolean DEBUG_WRITE = true;
	private static final String SELF_CLASS = "com/example/FooBar";
	private static final ClassSupplier CLASS_SUPPLIER = runtimeSupplier();

	@ParameterizedTest
	@MethodSource("paths")
	public void test(Path file) {
		handle(read(file), pass());
	}

	private static Consumer<MethodNode> pass() {
		return method -> {
			if (!AccessFlag.isAbstract(method.access))
				assertTrue(method.instructions.size() > 0, "Must have instructions if not abstract!");
			// The method should be compilable and verifiable
			ClassNode node = new ClassNode();
			node.name = SELF_CLASS;
			node.superName = Types.OBJECT_TYPE.getInternalName();
			node.version = Opcodes.V11;
			node.methods.add(method);
			try {
				// Verify method and see if it can be written
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
				node.accept(new CheckClassAdapter(cw));
				debugWrite(method, cw.toByteArray());
			} catch (Throwable t) {
				BytecodeToAstTransformer dumper = new BytecodeToAstTransformer(method);
				dumper.visit();
				System.err.println(dumper.getUnit().print(PrintContext.DEFAULT_CTX));
				fail("Method failed verification: " + method.name, t);
			}
		};
	}

	private static void handle(String original, Consumer<MethodNode> handler) {
		// JASM parse
		Unit unit = null;
		try {
			unit = createUnit(DEFAULT_KEYWORDS, original);
		} catch (Throwable ex) {
			fail(ex);
		}

		assertNotNull(unit, "Unit must not be null!");

		// Validate
		AstValidator validator = new AstValidator(unit);
		try {
			validator.visit();
		} catch (AstException ex) {
			fail(ex);
		}
		for (ValidationMessage message : validator.getMessages())
			System.err.println(message);
		assertEquals(0, validator.getMessages().size());

		// Generate
		AstToMethodTransformer generator = new AstToMethodTransformer(CLASS_SUPPLIER, SELF_CLASS);
		generator.setDefinition(unit.getDefinitionAsMethod());
		try {
			generator.visit();
			handler.accept(generator.buildMethod());
		} catch (MethodCompileException ex) {
			fail(ex);
		}
	}

	private static String read(Path path) {
		try {
			return new String(Files.readAllBytes(path));
		} catch (IOException e) {
			fail(e);
			throw new IllegalStateException();
		}
	}

	public static List<Path> paths() throws IOException {
		return Files.walk(Paths.get("src/test/resources/passing"))
				.filter(p -> p.toString().endsWith(".txt"))
				.collect(Collectors.toList());
	}

	private static void debugWrite(MethodNode method, byte[] data) throws IOException {
		if (!DEBUG_WRITE)
			return;
		Path p = Paths.get("debug", method.name + ".class");
		if (!Files.exists(p.getParent()))
			Files.createDirectory(p.getParent());
		Files.write(p, data);
	}

	private static ClassSupplier runtimeSupplier() {
		return name -> {
			try {
				return IOUtil.toByteArray(ClassLoader.getSystemResourceAsStream(name + ".class"));
			} catch (Exception e) {
				// We can just make the class ourselves for the purposes of these tests
				try {
					System.out.println("Making empty class for type: " + name);
					return ClassPool.getDefault().makeClass(name.replace('/', '.')).toBytecode();
				} catch (Exception ex) {
					fail(ex);
					throw new IllegalStateException(ex);
				}
			}
		};
	}
}
