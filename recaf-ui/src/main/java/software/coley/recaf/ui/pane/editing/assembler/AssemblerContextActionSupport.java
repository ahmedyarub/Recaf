package software.coley.recaf.ui.pane.editing.assembler;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import me.darknet.assembler.query.AssemblyQueries;
import me.darknet.assembler.query.resolution.Resolution;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import software.coley.recaf.analytics.logging.DebuggingLogger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.path.AssemblerPathData;
import software.coley.recaf.path.AssemblerPathNode;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.path.IncompletePathException;
import software.coley.recaf.services.cell.CellConfigurationService;
import software.coley.recaf.services.cell.context.ContextMenuProviderService;
import software.coley.recaf.services.cell.context.ContextSource;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.navigation.Navigable;
import software.coley.recaf.ui.control.FontIconView;
import software.coley.recaf.ui.control.richtext.Editor;
import software.coley.recaf.ui.control.richtext.source.JavaContextActionSupport;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.util.Lang;

/**
 * Enables context actions on an {@link Editor} within an {@link AssemblerPane}.
 * The AST of the last successful parse from the assembler is used to query for menus offered by {@link ContextMenuProviderService}.
 *
 * @author Matt Coley
 * @see JavaContextActionSupport Alternative for context actions on Java source.
 */
@Dependent
public class AssemblerContextActionSupport extends AstBuildConsumerComponent {
	private static final DebuggingLogger logger = Logging.get(AssemblerContextActionSupport.class);
	private final CellConfigurationService cellConfigurationService;
	private final Actions actions;
	private ContextMenu menu;

	@Inject
	public AssemblerContextActionSupport(@Nonnull CellConfigurationService cellConfigurationService,
	                                     @Nonnull Actions actions) {
		this.cellConfigurationService = cellConfigurationService;
		this.actions = actions;
	}

	@Override
	public void install(@Nonnull Editor editor) {
		super.install(editor);
		CodeArea area = editor.getCodeArea();
		area.setOnContextMenuRequested(e -> {
			if (menu != null) {
				menu.hide();
				menu = null;
			}

			// Check AST model has been generated
			if (astElements == null) {
				logger.warn("Could not request context menu, AST model not available");
				return;
			}

			// Convert the event position to line/column
			CharacterHit hit = area.hit(e.getX(), e.getY());
			TwoDimensional.Position hitPos = area.offsetToPosition(hit.getInsertionIndex(),
					TwoDimensional.Bias.Backward);
			int line = hitPos.getMajor() + 1; // 1-indexed

			// Sync caret
			area.moveTo(hit.getInsertionIndex());

			// Create menu
			Resolution resolution = AssemblyQueries.resolveAt(astElements, hit.getInsertionIndex());
			AssemblerPathData data = new AssemblerPathData(editor, resolution);
			menu = cellConfigurationService.contextMenuOf(ContextSource.DECLARATION, new AssemblerPathNode(path, data));

			// Add "View in Decompiler at this line" item
			if (path != null) {
				ClassPathNode classPathNode = path.getPathOfType(ClassInfo.class);
				if (classPathNode != null) {
					// Create a fallback menu if none was created
					if (menu == null) {
						menu = new ContextMenu();
					} else {
						menu.getItems().add(new SeparatorMenuItem());
					}
					MenuItem viewInDecompiler = new MenuItem();
					viewInDecompiler.textProperty().bind(Lang.getBinding("menu.view.decompiler-at-line"));
					viewInDecompiler.setGraphic(new FontIconView(CarbonIcons.CODE));
					int currentLine = line; // capture for lambda
					viewInDecompiler.setOnAction(ev -> {
						try {
							// Find the nearest "line N" directive at or above the current cursor position
							int sourceLine = findNearestSourceLine(area, currentLine);
							Navigable classNavigable = actions.gotoDeclaration(classPathNode);
							if (sourceLine > 0) {
								// Navigate the decompiler to the source line after a brief delay
								FxThreadUtil.delayedRun(200, () -> {
									// Search the class pane's children for the actual decompiler pane
									software.coley.recaf.ui.pane.editing.AbstractDecompilePane decompilePane = null;
									for (Navigable child : classNavigable.getNavigableChildren()) {
										if (child instanceof software.coley.recaf.ui.pane.editing.AbstractDecompilePane adp) {
											decompilePane = adp;
											break;
										}
									}
									
									if (decompilePane != null) {
										CodeArea decompArea = decompilePane.getEditor().getCodeArea();
										int targetLine = Math.min(sourceLine - 1, decompArea.getParagraphs().size() - 1);
										if (targetLine >= 0) {
											decompArea.moveTo(targetLine, 0);
											decompArea.showParagraphAtCenter(targetLine);
											decompArea.requestFocus();
										}
									}
								});
							}
						} catch (IncompletePathException ex) {
							logger.warn("Cannot open decompiler at line, path incomplete", ex);
						}
					});
					menu.getItems().add(viewInDecompiler);
				}
			}

			// Show menu
			if (menu != null && !menu.getItems().isEmpty()) {
				menu.setAutoHide(true);
				menu.setHideOnEscape(true);
				menu.show(area.getScene().getWindow(), e.getScreenX(), e.getScreenY());
				menu.requestFocus();
			}
		});
	}

	@Override
	public void uninstall(@Nonnull Editor editor) {
		super.uninstall(editor);
		editor.getCodeArea().setOnContextMenuRequested(null);
	}

	/**
	 * Scans the assembler text backwards from the given line to find the nearest
	 * {@code line N} directive, returning the source line number N.
	 *
	 * @param area
	 * 		The code area containing assembler text.
	 * @param fromLine
	 * 		1-indexed line number to start searching from (searches backwards).
	 *
	 * @return The source line number from the nearest {@code line N} directive,
	 * or -1 if no directive is found.
	 */
	private int findNearestSourceLine(@Nonnull CodeArea area, int fromLine) {
		// Search backwards from the current line for a "line N" directive
		for (int i = fromLine - 1; i >= 0; i--) {
			String paragraphText = area.getParagraph(i).getText().trim();
			if (paragraphText.startsWith("line ") && !paragraphText.startsWith("line_")) {
				try {
					return Integer.parseInt(paragraphText.substring(5).trim());
				} catch (NumberFormatException ignored) {}
			}
		}
		// If not found backwards, try searching forward
		int paragraphCount = area.getParagraphs().size();
		for (int i = fromLine; i < paragraphCount; i++) {
			String paragraphText = area.getParagraph(i).getText().trim();
			if (paragraphText.startsWith("line ") && !paragraphText.startsWith("line_")) {
				try {
					return Integer.parseInt(paragraphText.substring(5).trim());
				} catch (NumberFormatException ignored) {}
			}
		}
		return -1;
	}
}
