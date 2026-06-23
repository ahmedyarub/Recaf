package software.coley.recaf.services.mapping.view;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.coley.observables.ObservableBoolean;
import software.coley.observables.ObservableString;
import software.coley.recaf.config.BasicConfigContainer;
import software.coley.recaf.config.BasicConfigValue;
import software.coley.recaf.config.ConfigGroups;

/**
 * Configuration for bytecode-source view mapping annotations.
 * Controls whether and how bytecode annotations are displayed in the decompiler view
 * and source annotations in the assembler view.
 *
 * @author Recaf Contributors
 */
@ApplicationScoped
public class ViewMappingConfig extends BasicConfigContainer {
	/**
	 * Annotation display mode: inline comments in the source text.
	 */
	public static final String MODE_INLINE = "inline";
	/**
	 * Annotation display mode: gutter tooltips on hover.
	 */
	public static final String MODE_GUTTER = "gutter";

	private final ObservableBoolean enabled = new ObservableBoolean(true);
	private final ObservableString displayMode = new ObservableString(MODE_GUTTER);

	@Inject
	public ViewMappingConfig() {
		super(ConfigGroups.SERVICE_UI, "view-mapping" + CONFIG_SUFFIX);
		addValue(new BasicConfigValue<>("enabled", boolean.class, enabled));
		addValue(new BasicConfigValue<>("display-mode", String.class, displayMode));
	}

	/**
	 * @return {@code true} if bytecode/source annotations should be shown in views.
	 */
	@Nonnull
	public ObservableBoolean getEnabled() {
		return enabled;
	}

	/**
	 * @return The display mode for annotations: {@value #MODE_INLINE} for inline comments,
	 * {@value #MODE_GUTTER} for gutter tooltip icons.
	 */
	@Nonnull
	public ObservableString getDisplayMode() {
		return displayMode;
	}

	/**
	 * @return {@code true} if inline comment mode is selected.
	 */
	public boolean isInlineMode() {
		return MODE_INLINE.equals(displayMode.getValue());
	}

	/**
	 * @return {@code true} if gutter tooltip mode is selected.
	 */
	public boolean isGutterMode() {
		return MODE_GUTTER.equals(displayMode.getValue());
	}
}
