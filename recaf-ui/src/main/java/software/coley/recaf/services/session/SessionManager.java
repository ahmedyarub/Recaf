package software.coley.recaf.services.session;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javafx.scene.Node;
import org.slf4j.Logger;
import software.coley.bentofx.dockable.Dockable;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.cdi.UiInitializationEvent;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.path.FilePathNode;
import software.coley.recaf.path.PathNode;
import software.coley.recaf.services.Service;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.navigation.Navigable;
import software.coley.recaf.services.navigation.NavigationManager;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.services.workspace.WorkspaceOpenListener;
import software.coley.recaf.ui.config.RecentFilesConfig;
import software.coley.recaf.ui.config.SessionConfig;
import software.coley.recaf.ui.config.SessionConfig.TabState;
import software.coley.recaf.ui.docking.DockingManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.PathLoadingManager;
import software.coley.recaf.workspace.model.Workspace;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to manage session state tracking and restoration.
 *
 * @author Matt Coley
 */
import software.coley.recaf.services.window.WindowManager;
import javafx.stage.Stage;

@ApplicationScoped
public class SessionManager implements Service, WorkspaceOpenListener {
	public static final String ID = "session";
	private static final Logger logger = Logging.get(SessionManager.class);

	private final SessionConfig config;
	private final RecentFilesConfig recentFilesConfig;
	private final DockingManager dockingManager;
	private final PathLoadingManager pathLoadingManager;
	private final WorkspaceManager workspaceManager;
	private final Actions actions;
	private final NavigationManager navigationManager;
	private final WindowManager windowManager;

	private boolean isRestoring = false;

	@Inject
	public SessionManager(@Nonnull SessionConfig config,
	                      @Nonnull RecentFilesConfig recentFilesConfig,
	                      @Nonnull DockingManager dockingManager,
	                      @Nonnull PathLoadingManager pathLoadingManager,
	                      @Nonnull WorkspaceManager workspaceManager,
	                      @Nonnull Actions actions,
	                      @Nonnull NavigationManager navigationManager,
	                      @Nonnull WindowManager windowManager) {
		this.config = config;
		this.recentFilesConfig = recentFilesConfig;
		this.dockingManager = dockingManager;
		this.pathLoadingManager = pathLoadingManager;
		this.workspaceManager = workspaceManager;
		this.actions = actions;
		this.navigationManager = navigationManager;
		this.windowManager = windowManager;

		workspaceManager.addWorkspaceOpenListener(this);
	}

	/**
	 * Called when the UI is initialized. Begins the session restore process if enabled.
	 *
	 * @param event
	 * 		Initialization event.
	 */
	public void onUiInit(@Observes UiInitializationEvent event) {
		if (!config.getRestoreSession().getValue())
			return;

		if (config.getWindowMaximized().getValue()) {
			FxThreadUtil.delayedRun(100, () -> {
				Stage mainWindow = windowManager.getMainWindow();
				if (mainWindow != null)
					mainWindow.setMaximized(true);
			});
		}

		List<RecentFilesConfig.WorkspaceModel> workspaces = recentFilesConfig.getRecentWorkspaces().getValue();
		if (workspaces.isEmpty())
			return;

		RecentFilesConfig.WorkspaceModel lastWorkspace = workspaces.get(0);
		if (lastWorkspace.canLoadWorkspace()) {
			logger.info("Restoring last session workspace: {}", lastWorkspace.primary().path());
			isRestoring = true;
			loadWorkspace(lastWorkspace);
		}
	}

	private void loadWorkspace(@Nonnull RecentFilesConfig.WorkspaceModel model) {
		Path primaryPath = Paths.get(model.primary().path());
		List<Path> supportingPaths = model.libraries().stream()
				.map(resource -> Paths.get(resource.path()))
				.toList();

		pathLoadingManager.asyncNewWorkspace(primaryPath, supportingPaths, ex -> {
			logger.error("Failed to restore recent workspace for '{}'", model.primary().getSimpleName(), ex);
			isRestoring = false;
		});
	}

	@Override
	public void onWorkspaceOpened(@Nonnull Workspace workspace) {
		if (isRestoring) {
			isRestoring = false;
			List<TabState> tabsToRestore = new ArrayList<>(config.getActiveTabs().getValue());
			if (tabsToRestore.isEmpty())
				return;

			FxThreadUtil.delayedRun(500, () -> {
				for (TabState tab : tabsToRestore) {
					try {
						if ("summary".equals(tab.type())) {
							actions.openSummary();
						} else if ("class".equals(tab.type())) {
							ClassPathNode classPath = workspace.findClass(tab.name());
							if (classPath != null) {
								Navigable nav = actions.gotoDeclaration(classPath);
								restoreCaretAndScroll(nav, tab);
							}
						} else if ("assembler_class".equals(tab.type())) {
							if (config.getVerboseLogging().getValue())
								logger.info("SessionManager: Restoring assembler_class {} at line {}", tab.name(), tab.line());
							ClassPathNode classPath = workspace.findClass(tab.name());
							if (classPath != null) {
								try {
									Navigable nav = actions.openAssembler(classPath);
									restoreCaretAndScroll(nav, tab);
								} catch (software.coley.recaf.path.IncompletePathException ignored) {}
							}
						} else if ("assembler_member".equals(tab.type())) {
							if (config.getVerboseLogging().getValue())
								logger.info("SessionManager: Restoring assembler_member {} at line {}", tab.name(), tab.line());
							String[] parts = tab.name().split(" ");
							if (parts.length >= 3) {
								String className = parts[0];
								String memberName = parts[1];
								String memberDesc = parts[2];
								ClassPathNode classPath = workspace.findClass(className);
								if (classPath != null) {
									software.coley.recaf.info.member.ClassMember member = classPath.getValue().getMethods().stream()
											.filter(m -> m.getName().equals(memberName) && m.getDescriptor().equals(memberDesc))
											.findFirst().orElse(null);
									if (member == null) {
										member = classPath.getValue().getFields().stream()
												.filter(m -> m.getName().equals(memberName) && m.getDescriptor().equals(memberDesc))
												.findFirst().orElse(null);
									}
									if (member != null) {
										try {
											Navigable nav = actions.openAssembler(classPath.child(member));
											restoreCaretAndScroll(nav, tab);
										} catch (software.coley.recaf.path.IncompletePathException ignored) {}
									}
								}
							}
						} else if ("file".equals(tab.type())) {
							FilePathNode filePath = workspace.findFile(tab.name());
							if (filePath != null) {
								Navigable nav = actions.gotoDeclaration(filePath);
								restoreCaretAndScroll(nav, tab);
							}
						}
					} catch (Exception ex) {
						logger.warn("Failed to restore tab for {}: {}", tab.type(), tab.name());
					}
				}
			});
		}
	}

	private void applyCaretAndScroll(org.fxmisc.richtext.CodeArea area, int line, int scroll) {
		Runnable action = () -> {
			if (line >= 0 && area.getParagraphs().size() > line) {
				area.moveTo(line, 0);
			}
			if (scroll >= 0 && area.getParagraphs().size() > scroll) {
				area.showParagraphAtTop(scroll);
			}
		};
		if (area.getHeight() > 0) {
			action.run();
		} else {
			javafx.beans.value.ChangeListener<Number> listener = new javafx.beans.value.ChangeListener<>() {
				@Override
				public void changed(javafx.beans.value.ObservableValue<? extends Number> ob, Number old, Number cur) {
					if (cur.doubleValue() > 0) {
						FxThreadUtil.delayedRun(50, action);
						area.heightProperty().removeListener(this);
					}
				}
			};
			area.heightProperty().addListener(listener);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T findDescendant(Navigable navigable, Class<T> type) {
		if (type.isInstance(navigable)) return (T) navigable;
		for (Navigable child : navigable.getNavigableChildren()) {
			T result = findDescendant(child, type);
			if (result != null) return result;
		}
		return null;
	}

	private void restoreCaretAndScroll(Navigable navigable, TabState tab) {
		if (config.getVerboseLogging().getValue())
			logger.info("SessionManager: restoreCaretAndScroll called for {}", navigable == null ? "null" : navigable.getClass().getSimpleName());
		if (navigable == null) return;
		int line = tab.line();
		int scroll = tab.scroll();
		boolean active = tab.active();

		// Search the navigable hierarchy for the actual editor pane.
		// gotoDeclaration() returns container panes (e.g. JvmClassPane) that hold
		// the editor as a child, so direct instanceof checks on 'navigable' would miss.
		software.coley.recaf.ui.pane.editing.AbstractDecompilePane decomp =
				findDescendant(navigable, software.coley.recaf.ui.pane.editing.AbstractDecompilePane.class);
		if (decomp != null) {
			org.fxmisc.richtext.CodeArea area = decomp.getEditor().getCodeArea();
			if (!decomp.getDecompileInProgress().getValue()) {
				FxThreadUtil.delayedRun(100, () -> {
					applyCaretAndScroll(area, line, scroll);
					if (active) {
						selectTab(navigable);
						area.requestFocus();
					}
				});
			} else {
				boolean[] restored = { false };
				decomp.getDecompileInProgress().addChangeListener((ob, old, cur) -> {
					if (!cur && !restored[0]) {
						restored[0] = true;
						FxThreadUtil.delayedRun(100, () -> {
							applyCaretAndScroll(area, line, scroll);
							if (active) {
								selectTab(navigable);
								area.requestFocus();
							}
						});
					}
				});
			}
			return;
		}

		software.coley.recaf.ui.pane.editing.assembler.AssemblerPane assemblerPane =
				findDescendant(navigable, software.coley.recaf.ui.pane.editing.assembler.AssemblerPane.class);
		if (assemblerPane != null) {
			org.fxmisc.richtext.CodeArea area = assemblerPane.getEditor().getCodeArea();
			if (!area.getText().isEmpty()) {
				FxThreadUtil.delayedRun(500, () -> {
					applyCaretAndScroll(area, line, scroll);
					if (active) {
						selectTab(navigable);
						area.requestFocus();
					}
				});
			} else {
				javafx.beans.value.ChangeListener<String> listener = new javafx.beans.value.ChangeListener<>() {
					@Override
					public void changed(javafx.beans.value.ObservableValue<? extends String> ob, String old, String cur) {
						if (!cur.isEmpty()) {
							FxThreadUtil.delayedRun(500, () -> {
								applyCaretAndScroll(area, line, scroll);
								if (active) {
									selectTab(navigable);
									area.requestFocus();
								}
							});
							area.textProperty().removeListener(this);
						}
					}
				};
				area.textProperty().addListener(listener);
			}
			return;
		}

		software.coley.recaf.ui.pane.editing.text.TextPane textPane =
				findDescendant(navigable, software.coley.recaf.ui.pane.editing.text.TextPane.class);
		if (textPane != null) {
			org.fxmisc.richtext.CodeArea area = textPane.getEditor().getCodeArea();
			FxThreadUtil.delayedRun(100, () -> {
				applyCaretAndScroll(area, line, scroll);
				if (active) {
					selectTab(navigable);
					area.requestFocus();
				}
			});
			return;
		}

		// Fallback for non-editor navigables (e.g. summary pane)
		if (active) {
			FxThreadUtil.delayedRun(100, () -> {
				selectTab(navigable);
				navigable.requestFocus();
			});
		}
	}

	private int getLine(Navigable navigable) {
		if (navigable == null) return 0;
		if (navigable instanceof software.coley.recaf.ui.pane.editing.AbstractDecompilePane decomp) {
			return decomp.getEditor().getCodeArea().getCurrentParagraph();
		} else if (navigable instanceof software.coley.recaf.ui.pane.editing.assembler.AssemblerPane assemblerPane) {
			return assemblerPane.getEditor().getCodeArea().getCurrentParagraph();
		} else if (navigable instanceof software.coley.recaf.ui.pane.editing.text.TextPane textPane) {
			return textPane.getEditor().getCodeArea().getCurrentParagraph();
		}
		for (Navigable child : navigable.getNavigableChildren()) {
			int line = getLine(child);
			if (line > 0)
				return line;
		}
		return 0;
	}

	private int getScroll(Navigable navigable) {
		if (navigable == null) return 0;
		if (navigable instanceof software.coley.recaf.ui.pane.editing.AbstractDecompilePane decomp) {
			return decomp.getEditor().getCodeArea().firstVisibleParToAllParIndex();
		} else if (navigable instanceof software.coley.recaf.ui.pane.editing.assembler.AssemblerPane assemblerPane) {
			return assemblerPane.getEditor().getCodeArea().firstVisibleParToAllParIndex();
		} else if (navigable instanceof software.coley.recaf.ui.pane.editing.text.TextPane textPane) {
			return textPane.getEditor().getCodeArea().firstVisibleParToAllParIndex();
		}
		for (Navigable child : navigable.getNavigableChildren()) {
			int scroll = getScroll(child);
			if (scroll > 0)
				return scroll;
		}
		return 0;
	}

	private void selectTab(Navigable navigable) {
		if (navigable == null) return;
		if (navigable instanceof Node node) {
			for (software.coley.bentofx.path.DockablePath dp : dockingManager.getBento().search().allDockables()) {
				if (dp.dockable().getNode() == node) {
					dp.dockable().inContainer(software.coley.bentofx.layout.container.DockContainerLeaf::selectDockable);
					break;
				}
			}
		}
	}

	@PreDestroy
	public void saveSessionState() {
		if (!config.getRestoreSession().getValue())
			return;

		Stage mainWindow = windowManager.getMainWindow();
		if (mainWindow != null) {
			config.getWindowMaximized().setValue(mainWindow.isMaximized());
		}

		List<TabState> activeTabs = new ArrayList<>();
		if (workspaceManager.hasCurrentWorkspace()) {
			for (software.coley.bentofx.path.DockablePath dp : dockingManager.getBento().search().allDockables()) {
				if (dp.dockable().getNode() instanceof Navigable navigable) {
					try {
						if (config.getVerboseLogging().getValue())
							logger.info("SessionManager: Processing navigable for save: {}", navigable.getClass().getName());
						PathNode<?> pathNode = navigable.getPath();
						int line = getLine(navigable);
						int scroll = getScroll(navigable);
						boolean active = dp.leafContainer().getSelectedDockable() == dp.dockable();

						if (navigable instanceof software.coley.recaf.ui.pane.editing.assembler.AssemblerPane) {
							if (pathNode instanceof ClassPathNode classNode) {
								if (config.getVerboseLogging().getValue())
									logger.info("SessionManager: Saving assembler_class {} at line {}, scroll {}", classNode.getValue().getName(), line, scroll);
								activeTabs.add(new TabState("assembler_class", classNode.getValue().getName(), line, scroll, active));
							} else if (pathNode instanceof software.coley.recaf.path.ClassMemberPathNode memberNode) {
								String className = memberNode.getParent().getValue().getName();
								String memberName = memberNode.getValue().getName();
								String memberDesc = memberNode.getValue().getDescriptor();
								if (config.getVerboseLogging().getValue())
									logger.info("SessionManager: Saving assembler_member {} at line {}, scroll {}", className + " " + memberName + " " + memberDesc, line, scroll);
								activeTabs.add(new TabState("assembler_member", className + " " + memberName + " " + memberDesc, line, scroll, active));
							}
						} else if (pathNode instanceof ClassPathNode classNode) {
							activeTabs.add(new TabState("class", classNode.getValue().getName(), line, scroll, active));
						} else if (pathNode instanceof FilePathNode fileNode) {
							activeTabs.add(new TabState("file", fileNode.getValue().getName(), line, scroll, active));
						} else if (navigable instanceof software.coley.recaf.ui.pane.WorkspaceInformationPane) {
							activeTabs.add(new TabState("summary", "", 0, 0, active));
						}
					} catch (Exception ex) {
						logger.error("SessionManager: Error saving tab state for {}", navigable.getClass().getName(), ex);
					}
				}
			}
		}

		config.getActiveTabs().setValue(activeTabs);
		if (config.getVerboseLogging().getValue())
			logger.info("Saved {} tabs for session restore", activeTabs.size());
	}

	@Nonnull
	@Override
	public String getServiceId() {
		return ID;
	}

	@Nonnull
	@Override
	public SessionConfig getServiceConfig() {
		return config;
	}
}
