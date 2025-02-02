package me.coley.recaf.ui.pane;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import me.coley.recaf.code.*;
import me.coley.recaf.config.Configs;
import me.coley.recaf.ui.behavior.ClassRepresentation;
import me.coley.recaf.ui.behavior.SaveResult;
import me.coley.recaf.ui.behavior.Updatable;
import me.coley.recaf.ui.context.ContextBuilder;
import me.coley.recaf.ui.util.Icons;
import me.coley.recaf.ui.util.Lang;
import me.coley.recaf.util.AccessFlag;
import me.coley.recaf.util.StringUtil;
import me.coley.recaf.util.TextDisplayUtil;
import me.coley.recaf.util.Types;
import me.coley.recaf.util.threading.FxThreadUtil;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.stream.Collectors;

import static me.coley.recaf.ui.util.Icons.getIconView;

/**
 * Visualization of the fields and methods of a {@link ClassInfo}.
 *
 * @author Matt Coley
 */
public class OutlinePane extends BorderPane implements ClassRepresentation {
	private static final SimpleBooleanProperty showTypes = new SimpleBooleanProperty();
	private static final SimpleBooleanProperty showSynthetics = new SimpleBooleanProperty(true);
	private final OutlineTree tree = new OutlineTree();
	private final ClassRepresentation parent;
	private CommonClassInfo classInfo;

	/**
	 * New outline panel.
	 *
	 * @param parent
	 * 		The parent panel the outline belongs to.
	 */
	public OutlinePane(ClassRepresentation parent) {
		this.parent = parent;
		setCenter(tree);
		setBottom(createButtonBar());
	}

	@Override
	public void onUpdate(CommonClassInfo newValue) {
		classInfo = newValue;
		tree.onUpdate(newValue);
	}

	@Override
	public CommonClassInfo getCurrentClassInfo() {
		return classInfo;
	}

	@Override
	public boolean supportsMemberSelection() {
		return true;
	}

	@Override
	public boolean isMemberSelectionReady() {
		// Should be always ready
		return tree.getRoot() != null;
	}

	@Override
	public void selectMember(MemberInfo memberInfo) {
		// Cancel if selection already matches
		if (memberInfo.equals(tree.getSelectionModel().getSelectedItem().getValue()))
			return;
		// Find the member in the tree and select it
		OutlineItem root = (OutlineItem) tree.getRoot();
		for (TreeItem<?> child : root.getChildren()) {
			if (child instanceof OutlineItem) {
				OutlineItem memberItem = (OutlineItem) child;
				if (memberInfo.equals(memberItem.getValue())) {
					tree.getSelectionModel().select(memberItem);
				}
			}
		}
	}

	@Override
	public SaveResult save() {
		throw new UnsupportedOperationException("Outline pane does not support modification");
	}

	@Override
	public boolean supportsEditing() {
		// Outline is just for show. Save keybind does nothing here.
		return false;
	}

	@Override
	public Node getNodeRepresentation() {
		return this;
	}

	/**
	 * @return Box containing tree display options.
	 */
	private Node createButtonBar() {
		HBox box = new HBox();
		// Show synthetics
		Button btnShowSynthetics = new Button();
		Tooltip tipShowSynthetics = new Tooltip();
		tipShowSynthetics.textProperty().bind(Lang.getBinding("conf.editor.outline.showoutlinedsynths"));
		btnShowSynthetics.setTooltip(tipShowSynthetics);
		btnShowSynthetics.setGraphic(getIconView(Icons.SYNTHETIC));
		btnShowSynthetics.setOnAction(e -> {
			boolean old = showSynthetics.get();
			showSynthetics.set(!old);
			Configs.editor().showOutlinedSynthetics = !old;
			onUpdate(classInfo);
			updateButton(btnShowSynthetics, !old);
		});
		updateButton(btnShowSynthetics, showSynthetics.get());
		// Show types
		Button btnShowTypes = new Button();
		Tooltip tipShowTypes = new Tooltip();
		tipShowTypes.textProperty().bind(Lang.getBinding("conf.editor.outline.showoutlinedtypes"));
		btnShowTypes.setTooltip(tipShowTypes);
		btnShowTypes.setGraphic(getIconView(Icons.CODE));
		btnShowTypes.setOnAction(e -> {
			boolean old = showTypes.get();
			showTypes.set(!old);
			Configs.editor().showOutlinedTypes = !old;
			onUpdate(classInfo);
			updateButton(btnShowTypes, !old);
		});
		updateButton(btnShowTypes, showTypes.get());
		box.getChildren().addAll(btnShowTypes, btnShowSynthetics);
		return box;
	}

	private static void updateButton(Button button, boolean active) {
		if (active) {
			button.setOpacity(1.0);
		} else {
			button.setOpacity(0.4);
		}
	}

	static {
		showTypes.set(Configs.editor().showOutlinedTypes);
		showSynthetics.set(Configs.editor().showOutlinedSynthetics);
	}

	/**
	 * Tree that represents the {@link MemberInfo} of a {@link CommonClassInfo}.
	 */
	class OutlineTree extends TreeView<MemberInfo> implements Updatable<CommonClassInfo> {
		private OutlineTree() {
			getStyleClass().add("transparent-tree");
		}

		@Override
		public void onUpdate(CommonClassInfo info) {
			OutlineItem outlineRoot = new OutlineItem(null);
			for (FieldInfo fieldInfo : info.getFields()) {
				if (!showSynthetics.get() && AccessFlag.isSynthetic(fieldInfo.getAccess()))
					continue;
				outlineRoot.getChildren().add(new OutlineItem(fieldInfo));
			}
			for (MethodInfo methodInfo : info.getMethods()) {
				if (!showSynthetics.get() && AccessFlag.isSynthetic(methodInfo.getAccess()))
					continue;
				outlineRoot.getChildren().add(new OutlineItem(methodInfo));
			}
			outlineRoot.setExpanded(true);
			// Set factory to null while we update the root. This allows existing cells to be aware that they should
			// not attempt to put effort into redrawing since they are being replaced anyways.
			setCellFactory(null);
			FxThreadUtil.run(() -> {
				setRoot(outlineRoot);
				// Now that the root is set we can reinstate the intended cell factory. Cells for the root and its children
				// will use this factory when the FX thread requests them.
				setCellFactory(param -> new OutlineCell(info));
			});
		}
	}

	/**
	 * Item of a {@link MemberInfo}.
	 */
	static class OutlineItem extends TreeItem<MemberInfo> {
		private OutlineItem(MemberInfo member) {
			super(member);
		}
	}

	/**
	 * Cell of a {@link MemberInfo}.
	 */
	class OutlineCell extends TreeCell<MemberInfo> {
		private final CommonClassInfo classInfo;

		private OutlineCell(CommonClassInfo classInfo) {
			this.classInfo = classInfo;
			getStyleClass().add("transparent-cell");
			getStyleClass().add("monospace");
		}

		@Override
		protected void updateItem(MemberInfo item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || isRootBeingUpdated()) {
				setText(null);
				setGraphic(null);
				setOnMouseClicked(null);
				setContextMenu(null);
			} else if (item == null) {
				// Null is the edge case for the root
				setGraphic(Icons.getClassIcon(classInfo));
				setText(TextDisplayUtil.escapeShortenPath(classInfo.getName()));
				setOnMouseClicked(null);
				if (classInfo instanceof ClassInfo) {
					setContextMenu(ContextBuilder.forClass((ClassInfo) classInfo)
							.setDeclaration(true)
							.build());
				} else {
					setContextMenu(null);
				}
			} else {
				int max = Configs.display().maxTreeTextLength;
				String name = TextDisplayUtil.escapeShortenPath(item.getName());
				String desc = item.getDescriptor();
				if (item.isField()) {
					String text = name;
					if (showTypes.get()) {
						String type;
						if (Types.isValidDesc(desc))
							type = TextDisplayUtil.escapeShortenPath(Type.getType(desc).getInternalName());
						else type = "<INVALID>";
						text = type + " " + text;
					}
					setText(StringUtil.limit(text, "...", max));
					setGraphic(Icons.getFieldIcon((FieldInfo) item));
					setContextMenu(ContextBuilder.forField(classInfo, (FieldInfo) item)
							.setDeclaration(true)
							.build());
				} else {
					MethodInfo methodInfo = (MethodInfo) item;
					String text = name;
					if (showTypes.get()) {
						text += "(" + Arrays.stream(Type.getArgumentTypes(desc))
								.map(argType -> TextDisplayUtil.escapeShortenPath(argType.getInternalName()))
								.collect(Collectors.joining(", ")) +
								")" + TextDisplayUtil.escapeShortenPath(Type.getReturnType(desc).getInternalName());
					}
					setText(StringUtil.limit(text, "...", max));
					setGraphic(Icons.getMethodIcon(methodInfo));
					setContextMenu(ContextBuilder.forMethod(classInfo, (MethodInfo) item)
							.setDeclaration(true)
							.build());
				}
				// Clicking the outline member selects it in the parent view
				setOnMouseClicked(e -> parent.selectMember(item));
			}
		}

		private boolean isRootBeingUpdated() {
			return getTreeView().getCellFactory() == null;
		}
	}
}
