package me.coley.recaf.compile.javac;

import me.coley.recaf.compile.CompileOption;
import me.coley.recaf.compile.Compiler;
import me.coley.recaf.compile.CompilerDiagnostic;
import me.coley.recaf.compile.CompilerResult;
import me.coley.recaf.util.Directories;
import me.coley.recaf.util.JavaVersion;
import me.coley.recaf.util.logging.Logging;
import me.coley.recaf.workspace.resource.Resource;
import org.slf4j.Logger;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Wrapper for {@link JavaCompiler}.
 *
 * @author Matt Coley
 */
public class JavacCompiler extends Compiler {
	private static final String KEY_TARGET = "target";
	private static final String KEY_CLASSPATH = "classpath";
	private static final String KEY_DEBUG = "debug";
	private static final Logger logger = Logging.get(JavacCompiler.class);
	private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	private final List<Resource> classpath = new ArrayList<>();
	private boolean logging = true;
	private JavacListener listener;

	/**
	 * New javac compiler wrapper.
	 */
	public JavacCompiler() {
		super("javac", System.getProperty("java.version"));
	}

	@Override
	public CompilerResult compile(String className, String classSource, Map<String, CompileOption<?>> options) {
		if (compiler == null)
			return new CompilerResult(this, new IllegalStateException("Cannot load 'javac' compiler."));
		// Class input map
		VirtualUnitMap unitMap = new VirtualUnitMap();
		unitMap.addSource(className, classSource);
		// Create a file manager to track files in-memory rather than on-disk
		List<CompilerDiagnostic> errors = new ArrayList<>();
		JavacListener listenerWrapper = createRecordingListener(errors);
		JavaFileManager fmFallback = compiler.getStandardFileManager(listenerWrapper, Locale.getDefault(), UTF_8);
		JavaFileManager fm = new VirtualFileManager(unitMap, classpath, fmFallback);
		// Populate arguments
		//  - Classpath
		//  - Target bytecode level
		//  - Debug info
		List<String> args = new ArrayList<>();
		if (options.containsKey(KEY_CLASSPATH)) {
			String cp = options.get(KEY_CLASSPATH).getValue().toString();
			args.add("-classpath");
			args.add(cp);
			if (logging) logger.trace("Compiler classpath: {}", cp);
		}
		if (options.containsKey(KEY_TARGET)) {
			int value = (int) options.get(KEY_TARGET).getValue();
			String target = Integer.toString(value);
			if (JavaVersion.get() > 8 && value > 8) {
				args.add("--release");
				args.add(target);
			} else {
				args.add("-source");
				args.add(target);
				args.add("-target");
				args.add(target);
			}
			if (logging) logger.debug("Compiler target: {}", target);
		}
		if (options.containsKey(KEY_DEBUG)) {
			String value = options.get(KEY_DEBUG).getValue().toString();
			if (value.isEmpty())
				value = "none";
			args.add("-g:" + value);
			if (logging) logger.debug("Compiler debug: {}", value);
		} else {
			args.add("-g:none");
			if (logging) logger.debug("Compiler debug: none");
		}
		// Invoke compiler
		try {
			JavaCompiler.CompilationTask task =
					compiler.getTask(null, fm, listenerWrapper, args, null, unitMap.getFiles());
			if (task.call()) {
				if (logging) logger.info("Compilation of '{}' finished", className);
				return new CompilerResult(this, unitMap.getCompilations());
			} else {
				if (logging) logger.error("Compilation of '{}' failed", className);
				return new CompilerResult(this, errors);
			}
		} catch (RuntimeException ex) {
			if (logging) logger.error("Compilation of '{}' crashed: {}", className, ex);
			return new CompilerResult(this, ex);
		}
	}

	@Override
	public boolean isAvailable() {
		return compiler != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setTarget(Map<String, CompileOption<?>> options, int version) {
		CompileOption<Integer> opt = (CompileOption<Integer>) options.get(KEY_TARGET);
		opt.setValue(version);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setDebug(Map<String, CompileOption<?>> options, String debug) {
		CompileOption<String> opt = (CompileOption<String>) options.get(KEY_DEBUG);
		opt.setValue(debug);
	}

	@Override
	protected Map<String, CompileOption<?>> createDefaultOptions() {
		Map<String, CompileOption<?>> options = new HashMap<>();
		options.put(KEY_TARGET,
				new CompileOption<>(int.class, "--release", "target", "Target Java version", JavaVersion.get()));
		options.put(KEY_DEBUG,
				new CompileOption<>(String.class, "-g", "debug", "Debug information",
						createDebugValue(true, true, true)));
		options.put(KEY_CLASSPATH,
				new CompileOption<>(String.class, "-cp", "classpath", "Classpath", createClassPath()));
		return options;
	}

	/**
	 * @param errors
	 * 		List to add diagnostics to.
	 *
	 * @return Listener to encompass recording behavior and the user defined {@link #listener}.
	 */
	private JavacListener createRecordingListener(List<CompilerDiagnostic> errors) {
		return new ForwardingListener(listener) {
			@Override
			public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
				// pass to delegate
				super.report(diagnostic);
				// record
				if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
					errors.add(new CompilerDiagnostic((int) diagnostic.getLineNumber(),
							diagnostic.getMessage(Locale.getDefault())));
				}
			}
		};
	}

	/**
	 * @return Generated classpath.
	 */
	private String createClassPath() {
		// Ensure the default path is included
		String pathDefault = System.getProperty("java.class.path");
		StringBuilder sb = new StringBuilder(pathDefault);
		char separator = File.pathSeparatorChar;
		// Add phantoms and classpath extension directories
		try {
			Files.walk(Directories.getClasspathDirectory())
					.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
					.filter(p -> p.toFile().length() < 10_000_000)
					.forEach(p -> sb.append(separator).append(p.toAbsolutePath()));
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return sb.toString();
	}

	@Override
	public void addVirtualClassPath(Resource resource) {
		if (!classpath.contains(resource))
			classpath.add(resource);
	}

	@Override
	public void clearVirtualClassPath() {
		classpath.clear();
	}

	/**
	 * @param listener
	 * 		Listener that receives compiler error information.
	 */
	public void setCompileListener(JavacListener listener) {
		this.listener = listener;
	}

	/**
	 * @param variables
	 * 		Include variable symbols.
	 * @param lineNumbers
	 * 		Include line numbers.
	 * @param sourceName
	 * 		Include source file attribute.
	 *
	 * @return String value to use with {@link #setDebug(Map, String)}.
	 */
	public static String createDebugValue(boolean variables, boolean lineNumbers, boolean sourceName) {
		StringBuilder s = new StringBuilder();
		if (variables)
			s.append("vars,");
		if (lineNumbers)
			s.append("lines,");
		if (sourceName)
			s.append("source");
		// substr off dangling comma
		String value = s.toString();
		if (value.endsWith(",")) {
			value = s.substring(0, value.length() - 1);
		}
		return value;
	}

	/**
	 * @param logging
	 * 		Flag to enable/disable logging.
	 */
	public void setLogging(boolean logging) {
		this.logging = logging;
	}
}
