package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutfitDraftTest {
    @Test void editsAreReversibleAndPreserveUnknownCosmetics() throws Exception {
        var original = new ObjectMapper().readTree("{\"bodyCharacteristic\":\"Default.02\",\"haircut\":\"Morning.Black\",\"futureCosmetic\":{\"id\":\"keep\"}}");
        OutfitDraft draft = new OutfitDraft(original);
        draft.choose("haircut", "Bun.Copper"); draft.choose("cape", "Cape_Forest_Guardian.Green.Neck_Piece");
        assertEquals("Morning.Black", original.path("haircut").asText());
        assertEquals("keep", draft.skin().path("futureCosmetic").path("id").asText());
        draft.undo(); assertEquals("", draft.selected("cape"));
        draft.undo(); assertFalse(draft.dirty());
        draft.redo(); assertEquals("Bun.Copper", draft.selected("haircut"));
        draft.choose("eyes", "Plain_Eyes.Blue"); assertFalse(draft.canRedo());
        draft.reset(); assertEquals(original, draft.skin());
        draft.undo(); assertTrue(draft.dirty());
    }
    @Test void clearingOptionalPartsIsExplicitAndCannotRemoveRequiredBody() throws Exception {
        var draft = new OutfitDraft(new ObjectMapper().readTree("{\"bodyCharacteristic\":\"Default.02\",\"cape\":\"Forest\"}"));
        draft.remove("cape"); assertTrue(draft.skin().get("cape").isNull());
        assertThrows(IllegalArgumentException.class, () -> draft.remove("bodyCharacteristic"));
        draft.markApplied(); assertFalse(draft.dirty()); assertFalse(draft.canUndo());
        var detached = draft.skin(); detached.put("cape", "Changed"); assertEquals("", draft.selected("cape"));
    }
}
