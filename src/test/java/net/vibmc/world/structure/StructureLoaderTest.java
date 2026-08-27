package net.vibmc.world.structure;

import net.vibmc.world.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLoaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void parsesVanillaInspiredPaletteAndBlockPositions() throws Exception {
        String data="name=test:rock\nsize=1,1,1\ndimension=overworld\nspacing=8\nchance=1\n"
                +"palette.0=minecraft:stone\nblock=0,0,0,0\n";
        StructureTemplate template=StructureLoader.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        assertEquals("test:rock",template.name());
        assertEquals(Blocks.STONE,template.blocks().get(0).block);
    }

    @Test
    void shipsTreesByDefaultAndLoadsExternalTemplates() throws Exception {
        Files.write(temporaryDirectory.resolve("custom.vstruct"),
                ("name=test:custom\nsize=1,1,1\ndimension=end\nspacing=4\nchance=1\n"
                        +"palette.0=minecraft:end_stone\nblock=0,0,0,0\n").getBytes(StandardCharsets.UTF_8));
        List<StructureTemplate> templates=StructureLoader.load(temporaryDirectory);
        assertTrue(templates.stream().anyMatch(t->t.name().equals("minecraft:oak_tree")));
        assertTrue(templates.stream().anyMatch(t->t.name().equals("test:custom")));
    }
}
