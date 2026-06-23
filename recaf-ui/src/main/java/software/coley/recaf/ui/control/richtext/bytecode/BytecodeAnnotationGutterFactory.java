package software.coley.recaf.ui.control.richtext.bytecode;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import software.coley.recaf.ui.control.FontIconView;
import software.coley.recaf.ui.control.richtext.Editor;
import software.coley.recaf.ui.control.richtext.linegraphics.AbstractLineGraphicFactory;
import software.coley.recaf.ui.control.richtext.linegraphics.LineContainer;
import software.coley.recaf.ui.control.richtext.linegraphics.LineGraphicFactory;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Gutter graphic factory that adds small indicator icons to lines that have
 * bytecode↔source mapping data available. Hovering the icon shows the
 * corresponding code from the other view in a tooltip.
 * <p>
 * Used in both the decompiler view (showing bytecode) and the assembler view (showing source).
 *
 * @author Recaf Contributors
 */
public class BytecodeAnnotationGutterFactory extends AbstractLineGraphicFactory {
	/**
	 * Priority for bytecode annotation gutter items. Placed after inheritance icons.
	 */
	public static final int P_BYTECODE_ANNOTATION = 250;

	private final NavigableMap<Integer, String> lineAnnotations = new TreeMap<>();
	private Editor editor;

	/**
	 * New bytecode annotation gutter factory.
	 */
	public BytecodeAnnotationGutterFactory() {
		super(P_BYTECODE_ANNOTATION);
	}

	/**
	 * Set the line-to-annotation mapping data.
	 *
	 * @param annotations
	 * 		Map of 1-indexed line numbers to annotation text (bytecode instructions or source lines).
	 */
	public void setAnnotations(@Nullable Map<Integer, String> annotations) {
		lineAnnotations.clear();
		if (annotations != null) {
			lineAnnotations.putAll(annotations);
		}
	}

	/**
	 * Clear all annotation data.
	 */
	public void clearAnnotations() {
		lineAnnotations.clear();
	}

	/**
	 * @return {@code true} if there are annotations available.
	 */
	public boolean hasAnnotations() {
		return !lineAnnotations.isEmpty();
	}

	@Override
	public void install(@Nonnull Editor editor) {
		this.editor = editor;
	}

	@Override
	public void uninstall(@Nonnull Editor editor) {
		this.editor = null;
		lineAnnotations.clear();
	}

	@Override
	public void apply(@Nonnull LineContainer container, int paragraph) {
		if (lineAnnotations.isEmpty()) return;

		int line = paragraph + 1;
		String annotation = lineAnnotations.get(line);
		if (annotation != null) {
			// Create a small dot indicator
			Circle dot = new Circle(3);
			dot.setFill(Color.rgb(100, 180, 255, 0.7));
			dot.setCursor(Cursor.HAND);

			// Create tooltip with the annotation text
			Tooltip tooltip = new Tooltip(annotation);
			tooltip.getStyleClass().add("mono-text");
			tooltip.setGraphic(new FontIconView(CarbonIcons.CODE, Color.rgb(100, 180, 255)));
			tooltip.setMaxWidth(600);
			tooltip.setWrapText(true);
			Tooltip.install(dot, tooltip);

			container.addHorizontal(dot);
		}
	}
}
