package software.coley.recaf.ui.config;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.coley.observables.ObservableBoolean;
import software.coley.observables.ObservableCollection;
import software.coley.observables.ObservableObject;
import software.coley.recaf.config.BasicCollectionConfigValue;
import software.coley.recaf.config.BasicConfigContainer;
import software.coley.recaf.config.BasicConfigValue;
import software.coley.recaf.config.ConfigGroups;

import software.coley.recaf.services.ServiceConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Config for session state tracking.
 *
 * @author Matt Coley
 */
@ApplicationScoped
public class SessionConfig extends BasicConfigContainer implements ServiceConfig {
	public static final String ID = "session";
	private final ObservableBoolean restoreSession = new ObservableBoolean(false);
	private final ObservableBoolean windowMaximized = new ObservableBoolean(false);
	private final ObservableBoolean verboseLogging = new ObservableBoolean(false);
	private final ObservableCollection<TabState, List<TabState>> activeTabs = new ObservableCollection<>(ArrayList::new);

	@Inject
	public SessionConfig() {
		super(ConfigGroups.SERVICE_UI, ID + CONFIG_SUFFIX);
		// Add values
		addValue(new BasicConfigValue<>("restore", boolean.class, restoreSession));
		addValue(new BasicConfigValue<>("window-maximized", boolean.class, windowMaximized, true));
		addValue(new BasicConfigValue<>("verbose-logging", boolean.class, verboseLogging));
		addValue(new BasicCollectionConfigValue<>("active-tabs", List.class, TabState.class, activeTabs, true));
	}

	/**
	 * @return Window maximized state saved from the last session.
	 */
	@Nonnull
	public ObservableBoolean getWindowMaximized() {
		return windowMaximized;
	}

	/**
	 * @return Auto-restore session toggle.
	 */
	@Nonnull
	public ObservableBoolean getRestoreSession() {
		return restoreSession;
	}

	/**
	 * @return Verbose logging toggle for session restore diagnostics.
	 */
	@Nonnull
	public ObservableBoolean getVerboseLogging() {
		return verboseLogging;
	}

	/**
	 * @return Active tabs saved from the last session.
	 */
	@Nonnull
	public ObservableObject<List<TabState>> getActiveTabs() {
		return activeTabs;
	}

	/**
	 * Model for tracking an active tab from a session.
	 * 
	 * @param type ID of the PathNode type (e.g. "class", "file", "summary")
	 * @param name The name or path of the content (e.g. package/MyClass)
	 * @param line The caret line position
	 * @param scroll The scroll line position
	 * @param active Whether this tab was focused
	 */
	public record TabState(String type, String name, int line, int scroll, boolean active) {
		public TabState(String type, String name, int line) {
			this(type, name, line, 0, false);
		}
	}
}
