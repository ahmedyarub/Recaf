package software.coley.recaf.ui.pane.search;

import atlantafx.base.controls.Popover;
import atlantafx.base.theme.Styles;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.reactfx.EventStreams;
import software.coley.collections.Lists;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.FileInfo;
import software.coley.recaf.path.IncompletePathException;
import software.coley.recaf.path.PathNode;
import software.coley.recaf.path.PathNodes;
import software.coley.recaf.path.WorkspacePathNode;
import software.coley.recaf.services.cell.CellConfigurationService;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.navigation.Navigable;
import software.coley.recaf.services.search.CancellableSearchFeedback;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.search.query.Query;
import software.coley.recaf.services.search.result.Result;
import software.coley.recaf.services.search.result.Results;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.ui.control.ActionButton;
import software.coley.recaf.ui.control.BoundCheckBox;
import software.coley.recaf.ui.control.BoundLabel;
import software.coley.recaf.ui.control.BoundTextField;
import software.coley.recaf.ui.control.PathNodeTree;
import software.coley.recaf.ui.control.richtext.Editor;
import software.coley.recaf.ui.control.tree.TreeItems;
import software.coley.recaf.ui.control.tree.WorkspaceTreeNode;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.util.Lang;
import software.coley.recaf.util.threading.Batch;
import software.coley.recaf.workspace.model.Workspace;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import software.coley.recaf.ui.control.ActionMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextField;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.scene.input.KeyCode;

/**
 * Common base capabilities for search panels.
 *
 * @author Matt Coley
 */
public abstract class AbstractSearchPane extends BorderPane implements Navigable {
	private static final org.slf4j.Logger logger = software.coley.recaf.analytics.logging.Logging.get(AbstractSearchPane.class);
	private static final ObservableList<String> pastSearches = FXCollections.observableArrayList();
	private static final int MAX_HISTORY = 10;
	private final WorkspaceManager workspaceManager;
	private final SearchService searchService;
	private final CellConfigurationService configurationService;
	private final Actions actions;
	private final WorkspacePathNode workspacePath;
	private final SearchOptions searchOptions = new SearchOptions();
	protected final PathNodeTree liveResultsTree;
	protected final BooleanProperty liveResults = new SimpleBooleanProperty(true);
	private ActionButton searchOptionsButton;
	private Popover searchOptionsPopover;
	private CancellableSearchFeedback lastSearchFeedback;
	private final java.util.Map<PathNode<?>, Result<?>> resultMapping = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Create the base outline of a search panel capabilities.
	 *
	 * @param workspaceManager
	 * 		Manager to pull current workspace from.
	 * @param searchService
	 * 		Search service to initiate searches with.
	 * @param configurationService
	 * 		Cell configuration service to stylize the output tree model.
	 * @param actions
	 * 		Action service to assist stylizing the output tree model.
	 */
	public AbstractSearchPane(@Nonnull WorkspaceManager workspaceManager,
	                          @Nonnull SearchService searchService,
	                          @Nonnull CellConfigurationService configurationService,
	                          @Nonnull Actions actions) {
		this.workspaceManager = workspaceManager;
		this.searchService = searchService;
		this.configurationService = configurationService;
		this.actions = actions;

		liveResultsTree = newTree();

		workspacePath = PathNodes.workspacePath(Objects.requireNonNull(workspaceManager.getCurrent(),
				"Cannot open search if no workspace is open"));
	}

	@Override
	public boolean isTrackable() {
		// We want this type to be navigable to benefit from automatic close support.
		return false;
	}

	@Nonnull
	@Override
	public PathNode<?> getPath() {
		return workspacePath;
	}

	@Nonnull
	@Override
	public Collection<Navigable> getNavigableChildren() {
		return Collections.emptyList();
	}

	@Override
	public void disable() {
		cancelLastSearch();
		liveResultsTree.setRoot(null);
		getChildren().clear();
		setDisable(true);
	}

	/**
	 * Set up the UI with the given inputs.
	 *
	 * @param input
	 * 		Node to handle user input.
	 */
	protected void setInputs(@Nonnull Node input) {
		Node liveResultsDisplay = createLiveResultsDisplay();

		setTop(input);
		setCenter(liveResultsDisplay);

		sceneProperty().addListener((ob, old, cur) -> {
			if (cur != null) {
				software.coley.recaf.util.FxThreadUtil.delayedRun(10, () -> {
					Node field = input.lookup(".text-field");
					if (field != null) field.requestFocus();
				});
			}
		});

		liveResults.addListener((ob, old, cur) -> {
			if (cur) {
				setCenter(liveResultsDisplay);
			} else {
				setCenter(null);
			}
		});
		setupSearchOptionsListener();
	}

	/**
	 * Setup auto-complete dropdown for the given text field.
	 *
	 * @param input
	 * 		Text field to apply autocomplete to.
	 */
	protected void setupAutoComplete(@Nonnull TextField input) {
		ContextMenu autoCompletePopup = new ContextMenu();
		autoCompletePopup.setAutoHide(true);

		input.textProperty().addListener((ob, old, cur) -> {
			if (cur == null || cur.isEmpty() || !input.isFocused()) {
				autoCompletePopup.hide();
				return;
			}
			List<ActionMenuItem> items = pastSearches.stream()
					.filter(text -> text.toLowerCase().contains(cur.toLowerCase()) && !text.equals(cur))
					.distinct()
					.map(text -> new ActionMenuItem(text, () -> {
						input.setText(text);
						input.requestFocus();
						input.positionCaret(text.length());
					}))
					.toList();

			if (items.isEmpty()) {
				autoCompletePopup.hide();
			} else {
				autoCompletePopup.getItems().setAll(items);
				if (!autoCompletePopup.isShowing() && input.getScene() != null && input.getScene().getWindow() != null) {
					javafx.geometry.Point2D p = input.localToScreen(0.0, input.getHeight());
					if (p != null) {
						autoCompletePopup.show(input, p.getX(), p.getY());
					}
				}
			}
		});

		input.focusedProperty().addListener((ob, old, cur) -> {
			if (!cur) autoCompletePopup.hide();
		});

		input.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				String text = input.getText();
				if (text != null && !text.isBlank()) {
					pastSearches.remove(text);
					pastSearches.add(0, text);
					while (pastSearches.size() > MAX_HISTORY)
						pastSearches.removeLast();
				}
			}
		});
	}

	/**
	 * @return {@code true} when this search type can visit files.
	 */
	protected boolean supportsFileSearchOptions() {
		return false;
	}

	/**
	 * @return Node wrapping the live results tree with overlay controls.
	 */
	@Nonnull
	private Node createLiveResultsDisplay() {
		if (!supportsFileSearchOptions())
			searchOptions.searchFilesProperty().set(false);

		searchOptionsButton = new ActionButton(CarbonIcons.SETTINGS, this::showSearchOptionsPopover);
		searchOptionsButton.withTooltip("dialog.search.options");
		searchOptionsButton.setFocusTraversable(false);
		searchOptionsButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ACCENT, Styles.FLAT);
		StackPane.setAlignment(searchOptionsButton, Pos.BOTTOM_RIGHT);
		StackPane.setMargin(searchOptionsButton, new Insets(7));

		return new StackPane(liveResultsTree, searchOptionsButton);
	}

	/**
	 * Refreshes search results when advanced options change.
	 */
	private void setupSearchOptionsListener() {
		EventStreams.changesOf(searchOptions.searchClassesProperty()).map(unused -> new Object())
				.or(EventStreams.changesOf(searchOptions.searchFilesProperty()).map(unused -> new Object()))
				.or(EventStreams.changesOf(searchOptions.includedPackagesProperty()).map(unused -> new Object()))
				.or(EventStreams.changesOf(searchOptions.excludedPackagesProperty()).map(unused -> new Object()))
				.or(EventStreams.changesOf(searchOptions.includedDirectoriesProperty()).map(unused -> new Object()))
				.or(EventStreams.changesOf(searchOptions.excludedDirectoriesProperty()).map(unused -> new Object()))
				.reduceSuccessions(Collections::singletonList, Lists::add, Duration.ofMillis(Editor.SHORT_DELAY_MS))
				.addObserver(unused -> search());
	}

	/**
	 * Shows the advanced search options popover.
	 */
	private void showSearchOptionsPopover() {
		if (searchOptionsPopover == null) {
			searchOptionsPopover = new Popover(createSearchOptionsContent());
			searchOptionsPopover.setArrowLocation(Popover.ArrowLocation.BOTTOM_RIGHT);
		}
		searchOptionsPopover.show(searchOptionsButton);
	}

	/**
	 * @return Content for the advanced search options popover.
	 */
	@Nonnull
	private GridPane createSearchOptionsContent() {
		GridPane content = new GridPane();
		ColumnConstraints labelColumn = new ColumnConstraints();
		ColumnConstraints controlColumn = new ColumnConstraints();
		controlColumn.setFillWidth(true);
		controlColumn.setHgrow(Priority.ALWAYS);
		controlColumn.setHalignment(HPos.RIGHT);
		content.getColumnConstraints().addAll(labelColumn, controlColumn);
		content.setHgap(10);
		content.setVgap(5);

		int row = 0;
		row = addCustomSearchOptions(content, row);
		if (supportsFileSearchOptions()) {
			// Can only disable class searching if file searching is enabled.
			content.add(new BoundCheckBox(Lang.getBinding("dialog.search.options.search-classes"),
					searchOptions.searchClassesProperty()), 0, row++, 2, 1);
		}
		row = addTextOption(content, row, "dialog.search.options.include-packages",
				"dialog.search.options.package-prefixes.tooltip", searchOptions.includedPackagesProperty());
		row = addTextOption(content, row, "dialog.search.options.exclude-packages",
				"dialog.search.options.package-prefixes.tooltip", searchOptions.excludedPackagesProperty());

		if (supportsFileSearchOptions()) {
			content.add(new BoundCheckBox(Lang.getBinding("dialog.search.options.search-files"),
					searchOptions.searchFilesProperty()), 0, row++, 2, 1);
			row = addTextOption(content, row, "dialog.search.options.include-directories",
					"dialog.search.options.directory-prefixes.tooltip", searchOptions.includedDirectoriesProperty());
			row = addTextOption(content, row, "dialog.search.options.exclude-directories",
					"dialog.search.options.directory-prefixes.tooltip", searchOptions.excludedDirectoriesProperty());
		}

		return content;
	}

	/**
	 * @param content
	 * 		Grid to add the option to.
	 * @param row
	 * 		Grid row to add the option to.
	 * @param labelKey
	 * 		Translation key for the option label.
	 * @param tooltipKey
	 * 		Translation key for the option tooltip.
	 * @param property
	 * 		Property to bind the option value to.
	 *
	 * @return Next row index after the added option.
	 */
	protected static int addTextOption(@Nonnull GridPane content,
	                                   int row,
	                                   @Nonnull String labelKey,
	                                   @Nonnull String tooltipKey,
	                                   @Nonnull StringProperty property) {
		BoundTextField field = new BoundTextField(property).withTooltip(tooltipKey);
		content.add(new BoundLabel(Lang.getBinding(labelKey)), 0, row);
		content.add(fixed(field), 1, row);
		return row + 1;
	}

	/**
	 * Hook for search pane children to add custom search controls to the options popover.
	 *
	 * @param content
	 * 		Grid to add options to.
	 * @param row
	 * 		Grid row to begin at.
	 *
	 * @return Next row index after the added options.
	 */
	protected int addCustomSearchOptions(@Nonnull GridPane content, int row) {
		return row;
	}

	/**
	 * @param control
	 * 		Control to set to fill horizontal space.
	 *
	 * @return Given control with max width set to fill horizontal space.
	 */
	@Nonnull
	protected static Control fixed(@Nonnull Control control) {
		control.setMaxWidth(Double.MAX_VALUE);
		GridPane.setFillWidth(control, true);
		return control;
	}

	/**
	 * @return New path-node tree.
	 */
	@Nonnull
	protected PathNodeTree newTree() {
		PathNodeTree tree = new PathNodeTree(configurationService, actions);
		tree.contextSourceObjectPropertyProperty().set(SearchContextSource.SEARCH_INSTANCE);
		tree.setOnMousePressed(e -> {
			if (e.getClickCount() == 2 && e.isPrimaryButtonDown()) {
				var item = tree.getSelectionModel().getSelectedItem();
				logger.debug("Double clicked on search result item: {}", item);
				if (item != null && item.isLeaf()) {
					try {
						PathNode<?> path = item.getValue();
						logger.debug("Item path: {}", path);
						Navigable navigable = actions.gotoDeclaration(path);
						logger.debug("Navigable returned from actions.gotoDeclaration: {}", navigable);
						Result<?> result = resultMapping.get(path);
						logger.debug("Result from resultMapping: {}", result);

						if (result != null) {
							String targetString = null;
							if (result instanceof software.coley.recaf.services.search.result.StringResult stringResult)
								targetString = stringResult.getValue();
							else if (result instanceof software.coley.recaf.services.search.result.NumberResult numberResult)
								targetString = numberResult.getValue().toString();

							logger.debug("Extracted targetString: {}", targetString);

							if (targetString != null && navigable instanceof software.coley.recaf.ui.pane.editing.jvm.JvmClassPane classPane) {
								classPane.setEditorType(software.coley.recaf.ui.pane.editing.jvm.JvmClassEditorType.DECOMPILE);
								if (classPane.getDisplay() instanceof software.coley.recaf.ui.pane.editing.AbstractDecompilePane decompilePane) {
									logger.info("AbstractSearchPane: dispatching select('{}') to decompilePane", targetString);
									decompilePane.getContextActionSupport().select(targetString);
								}
							} else {
								logger.debug("targetString is null or navigable is not JvmClassPane (is {})", navigable);
							}
						}
					} catch (IncompletePathException ignored) {
						logger.error("IncompletePathException during double-click navigation", ignored);
					}
				}
			}
		});
		return tree;
	}

	/**
	 * @return The built query from current search inputs,
	 * or {@code null} if the inputs were invalid for any reason.
	 */
	@Nullable
	protected abstract Query buildQuery();

	/**
	 * @return Predicate that determines which search results are accepted for the current search run.
	 */
	@Nonnull
	protected Predicate<Result<?>> createResultFilter() {
		return this::doAcceptResult;
	}

	/**
	 * @param result
	 * 		Result to consider.
	 *
	 * @return {@code true} when the result should be accepted.
	 */
	protected boolean doAcceptResult(@Nonnull Result<?> result) {
		return true;
	}

	/**
	 * Initiates the search with current search inputs. Updates the output display.
	 */
	protected final void search() {
		// Skip if the panel has been disabled (occurs when closing it).
		// Sometimes the delay between searching and the user closing will initiate a search after closing.
		if (isDisabled()) return;

		// Must have a current workspace to search in.
		if (!workspaceManager.hasCurrentWorkspace())
			return;

		// Create a new root.
		Workspace workspace = workspaceManager.getCurrent();
		PathNodeTree tree = liveResults.get() ? liveResultsTree : newTree();
		WorkspaceTreeNode root = new WorkspaceTreeNode(PathNodes.workspacePath(workspace));
		root.setExpanded(true);
		tree.setRoot(root);

		// Cancel last search before we start a new one.
		cancelLastSearch();
		resultMapping.clear();

		// Skip if the query couldn't be built (invalid inputs most likely)
		Query query = buildQuery();
		if (query == null)
			return;

		// Run new search.
		SearchOptions.Snapshot optionsSnapshot = searchOptions.snapshot();
		Predicate<Result<?>> resultFilter = createResultFilter();
		CancellableSearchFeedback feedback;
		if (liveResults.get()) {
			feedback = new LiveOnlySearchFeedback(optionsSnapshot, resultFilter, result -> {
				WorkspaceTreeNode node = WorkspaceTreeNode.getOrInsertIntoTree(root, result.getPath());
				TreeItems.expandParents(node);
			});
			CompletableFuture.runAsync(() -> searchService.search(workspace, query, feedback));
		} else {
			feedback = new FilteringSearchFeedback(optionsSnapshot, resultFilter);
			CompletableFuture.supplyAsync(() -> searchService.search(workspace, query, feedback))
					.thenAccept(this::handleSearchResults);
		}
		lastSearchFeedback = feedback;
	}

	/**
	 * Called when a search completes that is not {@link #liveResults live}.
	 *
	 * @param results
	 * 		Results of a non-live search.
	 */
	protected void handleSearchResults(@Nonnull Results results) {
		// TODO: Handle displaying the results for non-live search
		//  - put display in center, should be a PathNodeTree model, ideally dockable so user can move it around
		//  - maybe have the toggle for "[x] live" be an overlay like the "(i)" in decompile UI
	}

	/**
	 * Stops the prior search.
	 */
	private void cancelLastSearch() {
		if (lastSearchFeedback != null) {
			lastSearchFeedback.cancel();
			lastSearchFeedback = null;
		}
	}

	/**
	 * Feedback that filters which workspace items are visited.
	 */
	private class FilteringSearchFeedback extends CancellableSearchFeedback {
		private final SearchOptions.Snapshot optionsSnapshot;
		private final Predicate<Result<?>> resultFilter;

		private FilteringSearchFeedback(@Nonnull SearchOptions.Snapshot optionsSnapshot,
		                                @Nonnull Predicate<Result<?>> resultFilter) {
			this.optionsSnapshot = optionsSnapshot;
			this.resultFilter = resultFilter;
		}

		@Override
		public boolean doVisitClass(@Nonnull ClassInfo cls) {
			return optionsSnapshot.shouldVisitClass(cls);
		}

		@Override
		public boolean doVisitFile(@Nonnull FileInfo file) {
			return optionsSnapshot.shouldVisitFile(file);
		}

		@Override
		public boolean doAcceptResult(@Nonnull Result<?> result) {
			boolean accepted = resultFilter.test(result);
			if (accepted) {
				resultMapping.put(result.getPath(), result);
			}
			return accepted;
		}
	}

	/**
	 * Feedback that incrementally updates the search results tree.
	 * <br>
	 * Disables the collection of results into a single wrapper at the end of a search.
	 * Since this is for live-only feedback, we won't use the resulting collection anyways, so we don't need to do
	 * the extra work.
	 */
	private class LiveOnlySearchFeedback extends FilteringSearchFeedback {
		private final Batch batch = FxThreadUtil.batch();
		private final AnimationTimer batchTimer = new AnimationTimer() {
			private static final long BATCH_INTERVAL_MS = 1000 / 4;
			private long last;

			@Override
			public void handle(long now) {
				if (now - last > BATCH_INTERVAL_MS) {
					publishResults();
					last = now;
				}
			}
		};
		private final Consumer<Result<?>> resultConsumer;

		private LiveOnlySearchFeedback(@Nonnull SearchOptions.Snapshot optionsSnapshot,
		                               @Nonnull Predicate<Result<?>> resultFilter,
		                               @Nonnull Consumer<Result<?>> resultConsumer) {
			super(optionsSnapshot, resultFilter);
			this.resultConsumer = resultConsumer;
			batchTimer.start();
		}

		@Override
		public boolean doAcceptResult(@Nonnull Result<?> result) {
			if (!super.doAcceptResult(result))
				return false;
			batch.add(() -> resultConsumer.accept(result));
			return false;
		}

		@Override
		public void onCompletion() {
			batchTimer.stop();
			publishResults();
		}

		private void publishResults() {
			batch.execute();
		}
	}
}
