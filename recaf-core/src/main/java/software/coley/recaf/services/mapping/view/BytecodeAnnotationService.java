package software.coley.recaf.services.mapping.view;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;
import software.coley.recaf.info.JvmClassInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds line-level annotation data for bytecode↔source mapping.
 * <p>
 * For a given class, produces a map of source line numbers to bytecode instruction summaries.
 * This data can be displayed as inline comments or gutter tooltips in the decompiler or assembler views.
 *
 * @author Recaf Contributors
 */
@ApplicationScoped
public class BytecodeAnnotationService {
	private final ViewMappingConfig config;

	@Inject
	public BytecodeAnnotationService(@Nonnull ViewMappingConfig config) {
		this.config = config;
	}

	/**
	 * Build a map of source line numbers to bytecode instruction summaries for the given class.
	 * Each source line that corresponds to bytecode instructions gets a human-readable summary
	 * of those instructions.
	 *
	 * @param classInfo
	 * 		Class to build annotations for.
	 *
	 * @return Map of 1-indexed source line numbers to bytecode instruction summaries,
	 * or an empty map if no line number data is available.
	 */
	@Nonnull
	public Map<Integer, String> buildSourceLineAnnotations(@Nonnull JvmClassInfo classInfo) {
		if (!config.getEnabled().getValue())
			return Map.of();

		Map<Integer, StringBuilder> lineToInstructions = new HashMap<>();

		ClassReader reader = classInfo.getClassReader();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
			                                 String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					private int currentSourceLine = -1;

					@Override
					public void visitLineNumber(int line, Label start) {
						currentSourceLine = line;
					}

					@Override
					public void visitInsn(int opcode) {
						appendInstruction(Printer.OPCODES[opcode]);
					}

					@Override
					public void visitIntInsn(int opcode, int operand) {
						appendInstruction(Printer.OPCODES[opcode] + " " + operand);
					}

					@Override
					public void visitVarInsn(int opcode, int varIndex) {
						appendInstruction(Printer.OPCODES[opcode] + " " + varIndex);
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						appendInstruction(Printer.OPCODES[opcode] + " " + shortenType(type));
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
						appendInstruction(Printer.OPCODES[opcode] + " " + shortenType(owner) + "." + fieldName);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String methodName,
					                            String methodDesc, boolean isInterface) {
						appendInstruction(Printer.OPCODES[opcode] + " " + shortenType(owner) + "." + methodName);
					}

					@Override
					public void visitInvokeDynamicInsn(String dynName, String dynDesc,
					                                   org.objectweb.asm.Handle bsm, Object... bsmArgs) {
						appendInstruction("INVOKEDYNAMIC " + dynName);
					}

					@Override
					public void visitJumpInsn(int opcode, Label label) {
						appendInstruction(Printer.OPCODES[opcode]);
					}

					@Override
					public void visitLdcInsn(Object value) {
						String repr = value instanceof String s ? "\"" + truncate(s, 30) + "\"" : String.valueOf(value);
						appendInstruction("LDC " + repr);
					}

					@Override
					public void visitIincInsn(int varIndex, int increment) {
						appendInstruction("IINC " + varIndex + " " + increment);
					}

					@Override
					public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
						appendInstruction("TABLESWITCH " + min + "-" + max);
					}

					@Override
					public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
						appendInstruction("LOOKUPSWITCH [" + keys.length + " cases]");
					}

					@Override
					public void visitMultiANewArrayInsn(String descriptor1, int numDimensions) {
						appendInstruction("MULTIANEWARRAY " + shortenType(descriptor1) + " " + numDimensions);
					}

					private void appendInstruction(String instruction) {
						if (currentSourceLine <= 0)
							return;
						lineToInstructions.computeIfAbsent(currentSourceLine, k -> new StringBuilder())
								.append(instruction).append('\n');
					}
				};
			}
		}, classInfo.getClassReaderFlags());

		// Convert to final map with trimmed strings
		Map<Integer, String> result = new HashMap<>();
		lineToInstructions.forEach((line, sb) -> {
			String text = sb.toString().trim();
			if (!text.isEmpty())
				result.put(line, text);
		});

		return result;
	}

	@Nonnull
	private static String shortenType(@Nonnull String internalName) {
		int slash = internalName.lastIndexOf('/');
		return slash >= 0 ? internalName.substring(slash + 1) : internalName;
	}

	@Nonnull
	private static String truncate(@Nonnull String s, int maxLen) {
		return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
	}
}

