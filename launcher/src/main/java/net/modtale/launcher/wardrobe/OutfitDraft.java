package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/** Local, reversible edits. No account mutation occurs until Apply is chosen. */
public final class OutfitDraft {
    private static final int HISTORY_LIMIT = 100;
    private static final Set<String> REQUIRED = Set.of("bodyCharacteristic", "underwear", "face", "ears", "mouth", "eyes");
    private final Deque<ObjectNode> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private ObjectNode original;
    private ObjectNode current;

    public OutfitDraft(JsonNode skin) { load(skin); }
    public void load(JsonNode skin) {
        if (skin == null || !skin.isObject()) throw new IllegalArgumentException("An outfit must be a cosmetic JSON object");
        original = ((ObjectNode) skin).deepCopy(); current = original.deepCopy(); undo.clear(); redo.clear();
    }
    public ObjectNode skin() { return current.deepCopy(); }
    public String selected(String category) { return current.path(category).asText(""); }
    public boolean dirty() { return !current.equals(original); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public static boolean canRemove(String category) { return !REQUIRED.contains(category); }
    public void choose(String category, String id) {
        if (category == null || !category.matches("[a-zA-Z]{2,40}")) throw new IllegalArgumentException("Invalid cosmetic category");
        if (id == null || id.isBlank() || id.length() > 512) throw new IllegalArgumentException("Choose a valid cosmetic option");
        ObjectNode next = current.deepCopy(); next.put(category, id); replace(next);
    }
    public void remove(String category) {
        if (!canRemove(category)) throw new IllegalArgumentException("This part of your character cannot be removed");
        ObjectNode next = current.deepCopy(); next.putNull(category); replace(next);
    }
    public void replace(JsonNode skin) {
        if (skin == null || !skin.isObject()) throw new IllegalArgumentException("An outfit must be a cosmetic JSON object");
        ObjectNode next = ((ObjectNode) skin).deepCopy();
        if (current.equals(next)) return;
        undo.addLast(current.deepCopy()); if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear(); current = next;
    }
    public void reset() { replace(original); }
    public void undo() { if (!undo.isEmpty()) { redo.addLast(current); current = undo.removeLast(); } }
    public void redo() { if (!redo.isEmpty()) { undo.addLast(current); current = redo.removeLast(); } }
    public void markApplied() { original = current.deepCopy(); undo.clear(); redo.clear(); }
}
