# UI framework

The core mod has a small framework for the screens of all Folkhold mods: `io.github.nicolasgoyon.folkhold.core.client.ui`. All screens use the same look, "wood and parchment": a dark wood frame for the navigation, a light parchment for what the player reads.

The classes are client classes. Only client code (screens, HUD, network handlers that open a screen) uses them.

## Rules

- **Sizes.** A screen works at the minimum size of 320 × 240 GUI pixels (a panel of 308 × 228 pixels). It can use more space when it is available. The vanilla font has a line of 9 pixels.
- **Texts.** All texts come from translation keys, in English and in French. A French text is often 15 % to 30 % longer. A text that does not fit ends with "…", and the tooltip shows the complete text.
- **No pause.** The screens do not pause the game, and the game stays visible behind the panel. Keep the screens compact.
- **Server data.** The server owns all data. A button sends an action to the server, and shows the waiting state until the answer comes (`UiButton.setPending`).
- **Colors.** A color never gives a meaning alone: add a word or a glyph (✓, ✕, ▲, ●).
- **Dangerous actions** need a confirmation (`UiDialog`).
- **Keyboard.** Escape closes, Tab moves the focus, Enter clicks the focused button, the left and right arrow keys change the section tabs.

## Classes

| Class | Use |
| --- | --- |
| `UiTheme` | The colors and the sizes. |
| `UiTone` | The meaning of a badge, a status line or a toast: gold, good, warning, bad, information, neutral, muted. |
| `UiReputation` | The colors and the glyph of each reputation tier. |
| `UiRect` | A rectangle and its cuts: `inset`, `top`, `belowTop`, `left`, `afterLeft`, `column`, `row`, `centered`. |
| `UiText` | Texts with "…", wrapped texts. |
| `UiDraw` | Frames, boxes, dashed outlines, progress bars, segment bars, badges, chips, banners, item icons, scroll bars. |
| `UiButton` | Button: normal, primary, dangerous, menu row, chip. Disabled with a reason (tooltip). Waiting state. |
| `UiHoldButton` | Dangerous button that the player holds for a time. |
| `UiTabs` | Tab bar. |
| `UiTextField` | Text field. |
| `UiList` | List of rows that scrolls. Optional selection. Empty state. |
| `UiKeyValueList`, `UiRow`, `UiMeter` | List of label-value rows, with headings and meters. |
| `UiScreen` | Base screen: panel, header (banner, title, subtitle, badges, close button), body, status line, footer hint. |
| `UiOverlay` | Base of a screen above another screen. |
| `UiMenu` | Drop-down menu: actions, value fields, separators. |
| `UiDialog` | Confirmation dialog: click, hold, or type the name. |
| `UiToast` | Toast: a parchment note at the top right. It replaces a chat message. |

## A new screen

```java
public class ExampleScreen extends UiScreen {
    private int tab;

    public ExampleScreen(Component title) {
        super(title);
    }

    @Override
    protected List<Badge> badges() {
        return List.of(Badge.of(Component.literal("Lv 7"), UiTone.GOLD));
    }

    @Override
    protected void initBody(UiRect body) {
        UiRect inner = body.inset(UiTheme.PADDING);
        UiTabs tabs = addRenderableWidget(new UiTabs(inner.top(UiTheme.CONTROL), labels(), tab, index -> {
            tab = index;
            rebuildWidgets();
        }));
        setSectionTabs(tabs);
        UiKeyValueList rows = addRenderableWidget(new UiKeyValueList(inner.belowTop(UiTheme.CONTROL, 4)));
        rows.setItems(rowsOf(tab));
        addRenderableWidget(UiButton.primary(inner.bottomPart(UiTheme.CONTROL).left(120),
                Component.translatable("example.action"), button -> {
                    button.setPending(true);
                    PacketDistributor.sendToServer(new ExampleActionPayload());
                }));
    }
}
```

- The game calls `init` again after a resize and after an overlay closes. Keep the state of the screen in fields (the selected tab, the scroll, the text of a field) and make the widgets again from it.
- `isCompact()` is true for a small panel: show a shorter layout.
- Draw the content that is not a widget in `renderBody`. It is behind the widgets.
- `showStatus(text, tone)` shows the result of an action in the screen, in place of a chat message. The status line covers the bottom of the body for some seconds.
- A menu or a dialog is a screen above its parent. A network handler that updates the parent finds it below the overlays: follow `UiOverlay.parent()` while the current screen is an overlay.
- A menu action runs after the parent screen made its widgets again. If the action changes the state of the screen, call `rebuildWidgets()` in the action.

## A drop-down menu

```java
UiMenu.open(this, buttonRect, title, List.of(
        UiMenu.Action.of(Component.translatable("example.teleport"), this::teleport),
        new UiMenu.Value(Component.translatable("example.level"), "8", text -> text.matches("\\d{0,2}"),
                text -> setLevel(Integer.parseInt(text))),
        new UiMenu.Separator(),
        UiMenu.Action.danger(Component.translatable("example.ruin"), this::confirmRuin)));
```

## A confirmation dialog

```java
UiDialog.builder(this, Component.translatable("example.ruin.title", name))
        .danger()
        .tag(Component.translatable("example.admin"))
        .message(Component.translatable("example.ruin.message"))
        .typeToConfirm(name)
        .confirm(Component.translatable("example.ruin"), () -> send("ruin"))
        .open();
```

- `hold(millis)`: the player holds the confirm button.
- `parchment()`: the parchment look (with `danger()`, a red band with the title).
- `comparison(before, after)`: a "before → after" comparison, for example the reputation.
- `bullet(text)`: one consequence of the action.
