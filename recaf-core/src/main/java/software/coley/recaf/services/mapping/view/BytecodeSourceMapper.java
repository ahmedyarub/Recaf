package software.coley.recaf.services.mapping.view;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.member.MethodMember;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Maps between source line numbers and bytecode instruction indices using the {@code LineNumberTable}
 * attribute in class files. This enables bidirectional navigation between decompiled source and
 * assembler views.
 * <p>
 * For each method, maintains two mappings:
 * <ul>
 *     <li>Source line → first bytecode instruction offset at that line</li>
 *     <li>Bytecode instruction offset → source line</li>
 * </ul>
 *
 * @author Recaf Contributors
 */
public class BytecodeSourceMapper {
	private final NavigableMap<Integer, Integer> sourceLineToBytecodeOffset = new TreeMap<>();
	private final NavigableMap<Integer, Integer> bytecodeOffsetToSourceLine = new TreeMap<>();
	private final String methodName;
	private final String methodDesc;

	private BytecodeSourceMapper(@Nonnull String methodName, @Nonnull String methodDesc) {
		this.methodName = methodName;
		this.methodDesc = methodDesc;
	}

	/**
	 * Create a mapper for a specific method in the given class.
	 *
	 * @param classInfo
	 * 		Class containing the method.
	 * @param method
	 * 		Method to create a mapping for.
	 *
	 * @return Mapper instance, or {@code null} if the method has no line number information.
	 */
	@Nullable
	public static BytecodeSourceMapper fromMethod(@Nonnull JvmClassInfo classInfo, @Nonnull MethodMember method) {
		BytecodeSourceMapper mapper = new BytecodeSourceMapper(method.getName(), method.getDescriptor());

		ClassReader reader = classInfo.getClassReader();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (name.equals(method.getName()) && descriptor.equals(method.getDescriptor())) {
					return new MethodVisitor(Opcodes.ASM9) {
						private int currentOffset = 0;

						@Override
						public void visitLineNumber(int line, Label start) {
							// Map source line to bytecode offset (first instruction at this line)
							mapper.sourceLineToBytecodeOffset.putIfAbsent(line, currentOffset);
							// Map bytecode offset to source line
							mapper.bytecodeOffsetToSourceLine.put(currentOffset, line);
						}

						@Override
						public void visitInsn(int opcode) {
							currentOffset++;
						}

						@Override
						public void visitIntInsn(int opcode, int operand) {
							currentOffset++;
						}

						@Override
						public void visitVarInsn(int opcode, int varIndex) {
							currentOffset++;
						}

						@Override
						public void visitTypeInsn(int opcode, String type) {
							currentOffset++;
						}

						@Override
						public void visitFieldInsn(int opcode, String owner, String name1, String descriptor1) {
							currentOffset++;
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String name1, String descriptor1, boolean isInterface) {
							currentOffset++;
						}

						@Override
						public void visitInvokeDynamicInsn(String name1, String descriptor1, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
							currentOffset++;
						}

						@Override
						public void visitJumpInsn(int opcode, Label label) {
							currentOffset++;
						}

						@Override
						public void visitLdcInsn(Object value) {
							currentOffset++;
						}

						@Override
						public void visitIincInsn(int varIndex, int increment) {
							currentOffset++;
						}

						@Override
						public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
							currentOffset++;
						}

						@Override
						public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
							currentOffset++;
						}

						@Override
						public void visitMultiANewArrayInsn(String descriptor1, int numDimensions) {
							currentOffset++;
						}
					};
				}
				return null;
			}
		}, classInfo.getClassReaderFlags());

		return mapper.sourceLineToBytecodeOffset.isEmpty() ? null : mapper;
	}

	/**
	 * Create mappers for all methods in the given class.
	 *
	 * @param classInfo
	 * 		Class to create mappers for.
	 *
	 * @return Map of method key ({@code name + desc}) to mapper instance.
	 */
	@Nonnull
	public static Map<String, BytecodeSourceMapper> fromClass(@Nonnull JvmClassInfo classInfo) {
		Map<String, BytecodeSourceMapper> mappers = new TreeMap<>();

		ClassReader reader = classInfo.getClassReader();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				BytecodeSourceMapper mapper = new BytecodeSourceMapper(name, descriptor);

				return new MethodVisitor(Opcodes.ASM9) {
					private int currentOffset = 0;

					@Override
					public void visitLineNumber(int line, Label start) {
						mapper.sourceLineToBytecodeOffset.putIfAbsent(line, currentOffset);
						mapper.bytecodeOffsetToSourceLine.put(currentOffset, line);
					}

					@Override
					public void visitInsn(int opcode) { currentOffset++; }
					@Override
					public void visitIntInsn(int opcode, int operand) { currentOffset++; }
					@Override
					public void visitVarInsn(int opcode, int varIndex) { currentOffset++; }
					@Override
					public void visitTypeInsn(int opcode, String type) { currentOffset++; }
					@Override
					public void visitFieldInsn(int opcode, String owner, String n, String d) { currentOffset++; }
					@Override
					public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) { currentOffset++; }
					@Override
					public void visitInvokeDynamicInsn(String n, String d, org.objectweb.asm.Handle bsm, Object... args) { currentOffset++; }
					@Override
					public void visitJumpInsn(int opcode, Label label) { currentOffset++; }
					@Override
					public void visitLdcInsn(Object value) { currentOffset++; }
					@Override
					public void visitIincInsn(int varIndex, int increment) { currentOffset++; }
					@Override
					public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { currentOffset++; }
					@Override
					public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { currentOffset++; }
					@Override
					public void visitMultiANewArrayInsn(String d, int numDimensions) { currentOffset++; }

					@Override
					public void visitEnd() {
						if (!mapper.sourceLineToBytecodeOffset.isEmpty()) {
							mappers.put(name + descriptor, mapper);
						}
					}
				};
			}
		}, classInfo.getClassReaderFlags());

		return mappers;
	}

	/**
	 * Given a source line number, find the closest bytecode instruction offset.
	 *
	 * @param sourceLine
	 * 		Source line number (1-indexed).
	 *
	 * @return Bytecode instruction offset, or -1 if no mapping exists.
	 */
	public int sourceToBytecodeOffset(int sourceLine) {
		Integer offset = sourceLineToBytecodeOffset.get(sourceLine);
		if (offset != null)
			return offset;

		// Find the nearest source line that has a mapping
		Map.Entry<Integer, Integer> floor = sourceLineToBytecodeOffset.floorEntry(sourceLine);
		return floor != null ? floor.getValue() : -1;
	}

	/**
	 * Given a bytecode instruction offset, find the source line number.
	 *
	 * @param bytecodeOffset
	 * 		Bytecode instruction offset (0-indexed).
	 *
	 * @return Source line number (1-indexed), or -1 if no mapping exists.
	 */
	public int bytecodeToSourceLine(int bytecodeOffset) {
		Integer line = bytecodeOffsetToSourceLine.get(bytecodeOffset);
		if (line != null)
			return line;

		// Find the nearest preceding offset that has a mapping
		Map.Entry<Integer, Integer> floor = bytecodeOffsetToSourceLine.floorEntry(bytecodeOffset);
		return floor != null ? floor.getValue() : -1;
	}

	/**
	 * @return Unmodifiable view of the source line to bytecode offset mapping.
	 */
	@Nonnull
	public NavigableMap<Integer, Integer> getSourceLineToBytecodeOffset() {
		return Collections.unmodifiableNavigableMap(sourceLineToBytecodeOffset);
	}

	/**
	 * @return Unmodifiable view of the bytecode offset to source line mapping.
	 */
	@Nonnull
	public NavigableMap<Integer, Integer> getBytecodeOffsetToSourceLine() {
		return Collections.unmodifiableNavigableMap(bytecodeOffsetToSourceLine);
	}

	/**
	 * @return Method name this mapper is for.
	 */
	@Nonnull
	public String getMethodName() {
		return methodName;
	}

	/**
	 * @return Method descriptor this mapper is for.
	 */
	@Nonnull
	public String getMethodDesc() {
		return methodDesc;
	}

	/**
	 * @return {@code true} if there are any line number mappings available.
	 */
	public boolean hasMappings() {
		return !sourceLineToBytecodeOffset.isEmpty();
	}

	/**
	 * @return The minimum source line number that has a mapping.
	 */
	public int getMinSourceLine() {
		return sourceLineToBytecodeOffset.isEmpty() ? -1 : sourceLineToBytecodeOffset.firstKey();
	}

	/**
	 * @return The maximum source line number that has a mapping.
	 */
	public int getMaxSourceLine() {
		return sourceLineToBytecodeOffset.isEmpty() ? -1 : sourceLineToBytecodeOffset.lastKey();
	}
}
