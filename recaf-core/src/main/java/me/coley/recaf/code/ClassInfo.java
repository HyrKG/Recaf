package me.coley.recaf.code;

import me.coley.recaf.RecafConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.*;

/**
 * Class info for resource. Provides some basic information about the class.
 *
 * @author Matt Coley
 */
public class ClassInfo implements ItemInfo, LiteralInfo, CommonClassInfo {
	private final byte[] value;
	private final String name;
	private final String superName;
	private final String signature;
	private final List<String> interfaces;
	private final int version;
	private final int access;
	private final List<FieldInfo> fields;
	private final List<MethodInfo> methods;
	private ClassReader classReader;
	private int hashCode;

	private ClassInfo(String name, String superName, String signature, List<String> interfaces, int version, int access,
					  List<FieldInfo> fields, List<MethodInfo> methods, byte[] value) {
		this.value = value;
		this.name = name;
		this.signature = signature;
		this.superName = superName;
		this.interfaces = interfaces;
		this.version = version;
		this.access = access;
		this.fields = fields;
		this.methods = methods;
	}

	@Override
	public byte[] getValue() {
		return value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getSuperName() {
		return superName;
	}

	@Override
	public String getSignature() {
		return signature;
	}

	@Override
	public List<String> getInterfaces() {
		return interfaces;
	}

	@Override
	public int getAccess() {
		return access;
	}

	@Override
	public List<FieldInfo> getFields() {
		return fields;
	}

	@Override
	public List<MethodInfo> getMethods() {
		return methods;
	}

	/**
	 * @return Class major version.
	 */
	public int getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ClassInfo info = (ClassInfo) o;
		return access == info.access &&
				name.equals(info.name) &&
				Objects.equals(superName, info.superName) &&
				Objects.equals(signature, info.signature) &&
				interfaces.equals(info.interfaces) &&
				fields.equals(info.fields) &&
				methods.equals(info.methods);
	}

	@Override
	public int hashCode() {
		int hashCode = this.hashCode;
		if (hashCode == 0) {
			hashCode = name.hashCode();
			hashCode = 31 * hashCode + Objects.hashCode(superName);
			hashCode = 31 * hashCode + Objects.hashCode(signature);
			hashCode = 31 * hashCode + interfaces.hashCode();
			hashCode = 31 * hashCode + access;
			hashCode = 31 * hashCode + fields.hashCode();
			hashCode = 31 * hashCode + methods.hashCode();
			this.hashCode = hashCode + 1;
		}
		return hashCode;
	}

	@Override
	public String toString() {
		return "ClassInfo{'" + name + "'}";
	}

	/**
	 * @return ASM class reader.
	 */
	public ClassReader getClassReader() {
		ClassReader classReader = this.classReader;
		if (classReader == null) {
			return this.classReader = new ClassReader(value);
		}
		return classReader;
	}

	/**
	 * Create a class info unit from the given class bytecode.
	 *
	 * @param value
	 * 		Class bytecode.
	 *
	 * @return Parsed class information unit.
	 */
	@SuppressWarnings("unchecked")
	public static ClassInfo read(byte[] value) {
		ClassReader reader = new ClassReader(value);
		String className = reader.getClassName();
		String superName = reader.getSuperName();
		String[] signatureWrapper = new String[1];
		List<String>[] interfacesWrapper = new List[1];
		int access = reader.getAccess();
		int[] versionWrapper = new int[1];
		List<FieldInfo> fields = new ArrayList<>();
		List<MethodInfo> methods = new ArrayList<>();
		reader.accept(new ClassVisitor(RecafConstants.ASM_VERSION) {
			@Override
			public void visit(int version, int access, String name, String signature,
							  String superName, String[] interfaces) {
				versionWrapper[0] = version;
				interfacesWrapper[0] = Arrays.asList(interfaces);
				signatureWrapper[0] = signature;
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String sig, Object value) {
				fields.add(new FieldInfo(className, name, descriptor, sig, access, value));
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] ex) {
				List<String> exceptions = ex == null ? Collections.emptyList() : Arrays.asList(ex);
				methods.add(new MethodInfo(className, name, descriptor, sig, access, exceptions));
				return null;
			}
		}, ClassReader.SKIP_CODE);
		return new ClassInfo(
				className,
				superName,
				signatureWrapper[0],
				interfacesWrapper[0],
				versionWrapper[0],
				access,
				fields,
				methods,
				value);
	}
}
