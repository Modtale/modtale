package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalAvatarRendererTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path directory;

    @Test void bindsAttachmentsAtBodyShapeCenterAndAppliesPaletteWithoutLosingAlpha() throws Exception {
        Path zip=fixture();
        Group model=LocalAvatarRenderer.load(zip,JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\",\"cape\":\"Test.Green\"}"));
        MeshView body=find(model,"bodyCharacteristic:Chest:mesh");
        assertNotNull(body);
        var map=((PhongMaterial)body.getMaterial()).getDiffuseMap();
        assertEquals(0xffaa3300,map.getPixelReader().getArgb(0,0));
        assertEquals(8,map.getWidth());
        assertEquals(8,map.getHeight());
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)
            assertEquals(0xffaa3300,map.getPixelReader().getArgb(x,y));
        assertEquals(0,map.getPixelReader().getArgb(4,0)>>>24);
        assertArrayEquals(new float[]{0,0,1,0,1,1,0,1},
                ((TriangleMesh)body.getMesh()).getTexCoords().toArray(null),
                "Replicated textures must retain the full authored face UV coverage");
        MeshView cape=find(model,"cape:Cloth:mesh");
        var origin=cape.localToScene(0,0,0);
        // Body pivot y=10 plus shape center y=4 plus cape child y=2. Attachment export position 999 is ignored.
        assertEquals(16,origin.getY(),1e-6);
        assertEquals(4,((TriangleMesh)cape.getMesh()).getPoints().size()/3);
        assertEquals(6,((TriangleMesh)cape.getMesh()).getFaces().size()/2);
    }

    @Test void explicitSuppressionWinsOverHatFallbackAndOnlyRequiredHairUsesGenericModel() throws Exception {
        Path zip=fixture();
        ObjectNode skin=(ObjectNode)JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\",\"haircut\":\"LongHair.Black\",\"headAccessory\":\"Half.Default\"}");
        Group half=LocalAvatarRenderer.load(zip,skin);
        assertTrue(hasId(half,"haircut:GenericLong.Black"));
        assertFalse(hasId(half,"haircut:LongHair.Black"));
        skin.put("haircut","GenericLong.Black");
        assertTrue(hasId(LocalAvatarRenderer.load(zip,skin),"haircut:GenericLong.Black"));
        for(String hat:List.of("Full.Default","Disabled.Default")) {
            skin.put("headAccessory",hat);
            Group model=LocalAvatarRenderer.load(zip,skin);
            assertFalse(hasId(model,"haircut:GenericLong.Black"));
        }
    }

    @Test void rotatesUvAroundSignedAnchorAndHonorsMirroring() throws Exception {
        JsonNode layout=JSON.readTree("{\"offset\":{\"x\":8,\"y\":2},\"mirror\":{\"x\":true},\"angle\":90}");
        assertArrayEquals(new float[]{.5f,.125f,.5f,0,.25f,0,.25f,.125f},LocalAvatarRenderer.uv(layout,2,4,16,16));
    }

    @Test void rejectsMissingBodyUnknownSelectionMissingAssetsAndTraversal() throws Exception {
        Path zip=fixture();
        assertThrows(IOException.class,()->LocalAvatarRenderer.load(zip,JSON.readTree("{}")));
        assertThrows(IOException.class,()->LocalAvatarRenderer.load(zip,JSON.readTree("{\"bodyCharacteristic\":\"Unknown.Warm\"}")));
        assertThrows(IOException.class,()->LocalAvatarRenderer.load(zip,JSON.readTree("{\"bodyCharacteristic\":\"Default.Unknown\"}")));
        assertThrows(IOException.class,()->LocalAvatarRenderer.load(zip,JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\",\"cape\":\"Test.Missing\"}")));
    }

    @Test void realInstalledArchiveBuildsAllAvailableCosmeticCategories() throws Exception {
        String path=System.getenv("WARDROBE_ASSETS_ZIP");assumeTrue(path!=null,"Set WARDROBE_ASSETS_ZIP for installed-asset validation");
        CosmeticCatalogClient catalog=new CosmeticCatalogClient(Path.of(path));
        ObjectNode skin=catalog.defaultSkin();
        for (var category : catalog.categories()) {
            var options=catalog.browse(category.key(),"",1,1).options();
            if (!options.isEmpty()) skin.put(category.key(),options.getFirst().id());
        }
        Group model=LocalAvatarRenderer.load(Path.of(path),skin);
        assertTrue(meshCount(model)>50);
        assertTrue(LocalAvatarRenderer.bounds(model).getHeight()>50);
        assertTrue(LocalAvatarRenderer.bounds(model).getHeight()<200,"Attachments must use the body's skeleton, not export-space positions");
        String export=System.getenv("WARDROBE_LOCAL_SKIN");if(export!=null)Files.writeString(Path.of(export),skin.toPrettyString());
    }

    @Test void everyInstalledModelVariantAttachesToThePlayerSkeleton() throws Exception {
        String assets=System.getenv("WARDROBE_ASSETS_ZIP");
        assumeTrue(assets!=null && System.getenv("WARDROBE_RENDERER_SWEEP")!=null,"Opt in with WARDROBE_RENDERER_SWEEP=1 and WARDROBE_ASSETS_ZIP");
        Path path=Path.of(assets);CosmeticCatalogClient catalog=new CosmeticCatalogClient(path);
        ObjectNode base=catalog.defaultSkin();Set<String> tested=new HashSet<>();List<String> failures=new ArrayList<>();
        for(var category:catalog.categories()) {
            int page=1;
            while(true) {
                var options=catalog.browse(category.key(),"",page++,100);
                for(var option:options.options()) {
                    var definition=catalog.resolve(category.key(),option.id());
                    String key=category.key()+":"+definition.path("Model").asText()+":"+definition.path("Texture").asText();
                    if(!tested.add(key))continue;
                    ObjectNode skin=base.deepCopy();skin.put(category.key(),option.id());
                    try {
                        Group model=LocalAvatarRenderer.load(path,skin,catalog);
                        assertTrue(meshCount(model)>0);
                        assertTrue(LocalAvatarRenderer.bounds(model).getHeight()<250,"Unexpected attachment bounds: "+option.id());
                    } catch(Exception | AssertionError ex) { failures.add(category.key()+":"+option.id()+" — "+ex.getMessage()); }
                }
                if(options.options().size()<100)break;
            }
        }
        assertTrue(tested.size()>350,"Sweep must cover the installed geometry variants");
        assertTrue(failures.isEmpty(),String.join("\n",failures));
        System.out.println("Rendered installed model/texture combinations: "+tested.size());
    }

    @Test void animationMovesAttachedCapeOnceAndRestoresAllChannels() throws Exception {
        Group model=LocalAvatarRenderer.load(fixture(),JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\",\"cape\":\"Test.Green\"}"));
        MeshView cape=find(model,"cape:Cloth:mesh");
        var mesh=(TriangleMesh)cape.getMesh();
        float[] originalUv=mesh.getTexCoords().toArray(null);
        var rest=cape.localToScene(0,0,0);
        var rig=LocalAvatarRenderer.rig(model);
        var clip=LocalAvatarRenderer.parseAnimation(JSON.readTree("""
                {"formatVersion":1,"duration":60,"holdLastKeyframe":true,"nodeAnimations":{
                  "Chest":{"position":[{"time":0,"delta":{"x":0}},{"time":60,"delta":{"x":10}}],
                    "orientation":[{"time":0,"delta":{"w":1}},{"time":60,"delta":{"z":1,"w":0}}]},
                  "Cloth":{"shapeStretch":[{"time":0,"delta":{"x":2,"y":3,"z":1}}],
                    "shapeVisible":[{"time":45,"delta":false}],
                    "shapeUvOffset":[{"time":30,"delta":{"x":1,"y":-1}}]}}}
                """),false);
        rig.apply(clip,.5);
        // Translation 5, then Chest turns 90 degrees: its attached child is six units above its pivot.
        var moved=cape.localToScene(0,0,0);
        assertEquals(-1,moved.getX(),1e-5);assertEquals(10,moved.getY(),1e-5);
        assertTrue(cape.isVisible(),"Visibility is stepped and must retain rest state before its first key");
        assertEquals(originalUv[0]+.5,mesh.getTexCoords().get(0),1e-6);
        assertEquals(originalUv[1]+.5,mesh.getTexCoords().get(1),1e-6);
        assertEquals(2,cape.getLocalToParentTransform().getMxx(),1e-6);
        assertEquals(3,cape.getLocalToParentTransform().getMyy(),1e-6);
        rig.apply(clip,.8);assertFalse(cape.isVisible());
        rig.reset();assertTrue(cape.isVisible());assertEquals(rest,cape.localToScene(0,0,0));
        assertArrayEquals(originalUv,mesh.getTexCoords().toArray(null));
        assertEquals(1,cape.getLocalToParentTransform().getMxx(),1e-6);
        // Sampling repeatedly must not accumulate deltas.
        rig.apply(clip,.5);assertEquals(moved,cape.localToScene(0,0,0));rig.reset();
    }

    @Test void animationLoopsAtSixtyFpsAndRejectsInvalidChannelsAndCoordinates() throws Exception {
        Group model=LocalAvatarRenderer.load(fixture(),JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\"}"));
        var rig=LocalAvatarRenderer.rig(model);MeshView body=find(model,"bodyCharacteristic:Chest:mesh");
        JsonNode data=JSON.readTree("""
            {"formatVersion":1,"duration":60,"nodeAnimations":{"Chest":{"position":[
              {"time":0,"delta":{"x":0}},{"time":60,"delta":{"x":12}}]}}}
            """);
        var loop=LocalAvatarRenderer.parseAnimation(data,true);
        assertEquals(1,loop.durationSeconds());rig.apply(loop,1.5);
        assertEquals(6,body.localToScene(0,0,0).getX(),1e-6);
        rig.apply(LocalAvatarRenderer.parseAnimation(data,false),1.1);
        assertEquals(0,body.localToScene(0,0,0).getX(),1e-6);
        ObjectNode invalid=data.deepCopy();((ObjectNode)invalid.path("nodeAnimations").path("Chest")).putArray("invented");
        assertThrows(IOException.class,()->LocalAvatarRenderer.parseAnimation(invalid,false));
        ((ObjectNode)data.path("nodeAnimations").path("Chest").path("position").get(1)).put("time",61);
        assertThrows(IOException.class,()->LocalAvatarRenderer.parseAnimation(data,false));
        assertThrows(IOException.class,()->LocalAvatarRenderer.loadAnimation(fixture(),"../escape.blockyanim",true));
    }

    @Test void hiddenShapesCanBecomeVisibleAndQuaternionSignsDoNotCauseFullTurns() throws Exception {
        Path assets=fixture();
        try(var archive=java.nio.file.FileSystems.newFileSystem(assets)) {
            Path body=archive.getPath("/Common/body.blockymodel");
            ObjectNode definition=(ObjectNode)JSON.readTree(Files.readString(body));
            ((ObjectNode)definition.path("nodes").get(0).path("shape")).put("visible",false);
            Files.writeString(body,definition.toString());
        }
        Group model=LocalAvatarRenderer.load(assets,JSON.readTree("{\"bodyCharacteristic\":\"Default.Warm\"}"));
        MeshView body=find(model,"bodyCharacteristic:Chest:mesh");assertNotNull(body);assertFalse(body.isVisible());
        var rig=LocalAvatarRenderer.rig(model);
        var clip=LocalAvatarRenderer.parseAnimation(JSON.readTree("""
            {"formatVersion":1,"duration":60,"nodeAnimations":{"Chest":{
              "shapeVisible":[{"time":30,"delta":true}],
              "orientation":[{"time":0,"delta":{"z":0.7071067811865476,"w":0.7071067811865476}},
                             {"time":60,"delta":{"z":-0.7071067811865476,"w":-0.7071067811865476}}]}}}
            """),false);
        rig.apply(clip,.25);assertFalse(body.isVisible());
        rig.apply(clip,.5);assertTrue(body.isVisible());
        assertEquals(-4,body.localToScene(0,0,0).getX(),1e-6);
        assertEquals(10,body.localToScene(0,0,0).getY(),1e-6);
        rig.reset();assertFalse(body.isVisible());
    }

    @Test void allInstalledAnimationCatalogClipsLoadAndSampleIncludingFacialAtlases() throws Exception {
        String assets=System.getenv("WARDROBE_ASSETS_ZIP");assumeTrue(assets!=null,"Set WARDROBE_ASSETS_ZIP for real animation validation");
        Path path=Path.of(assets);CosmeticCatalogClient catalog=new CosmeticCatalogClient(path);
        ObjectNode skin=catalog.defaultSkin();skin.put("cape","Cape_Forest_Guardian.Green.Neck_Piece");
        Group model=LocalAvatarRenderer.load(path,skin);var rig=LocalAvatarRenderer.rig(model);
        var rest=LocalAvatarRenderer.bounds(model);int count=0;
        for(var option:rig.animations()) {
            var clip=LocalAvatarRenderer.loadAnimation(path,option.animation(),option.looping());
            for(int i=0;i<=8;i++) {
                rig.apply(clip,clip.durationSeconds()*i/8);
                var bounds=LocalAvatarRenderer.bounds(model);
                assertTrue(Double.isFinite(bounds.getWidth()) && bounds.getHeight()<500,option.id());
            }
            rig.reset();assertEquals(rest,LocalAvatarRenderer.bounds(model),option.id());count++;
        }
        assertTrue(count>=51);System.out.println("Validated installed animation catalog entries: "+count);
        MeshView mouth=find(model,"mouth:Mouth:mesh");assertNotNull(mouth);
        var uv=((TriangleMesh)mouth.getMesh()).getTexCoords();float first=uv.get(0);
        var angry=catalog.animations("EmotesFace").stream().filter(o->o.id().equals("Angry")).findFirst().orElseThrow();
        rig.apply(LocalAvatarRenderer.loadAnimation(path,angry.animation(),true),0);
        assertNotEquals(first,uv.get(0),"Facial expression must select the authored mouth atlas frame");
        rig.reset();assertEquals(first,uv.get(0));
    }

    private Path fixture() throws Exception {
        Path file=directory.resolve("Assets.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file))) {
            for(String category : List.of("Underwear","Faces","Ears","Mouths","FacialHair","Eyebrows","Eyes","Pants","Overpants","Undertops","Overtops","Shoes","FaceAccessory","EarAccessory","SkinFeatures","Gloves"))
                add(zip,"Cosmetics/CharacterCreator/"+category+".json","[]");
            add(zip,"Cosmetics/CharacterCreator/HaircutFallbacks.json","{\"Long\":\"GenericLong\"}");
            add(zip,"Cosmetics/CharacterCreator/Haircuts.json","[{\"Id\":\"LongHair\",\"Model\":\"cape.blockymodel\",\"HairType\":\"Long\",\"RequiresGenericHaircut\":true,\"Textures\":{\"Black\":{\"Texture\":\"body.png\"}}},{\"Id\":\"GenericLong\",\"Model\":\"cape.blockymodel\",\"HairType\":\"Long\",\"RequiresGenericHaircut\":false,\"Textures\":{\"Black\":{\"Texture\":\"body.png\"}}}]");
            add(zip,"Cosmetics/CharacterCreator/HeadAccessory.json","[{\"Id\":\"Half\",\"HeadAccessoryType\":\"HalfCovering\",\"Model\":\"cape.blockymodel\",\"Textures\":{\"Default\":{\"Texture\":\"body.png\"}}},{\"Id\":\"Full\",\"HeadAccessoryType\":\"FullyCovering\",\"Model\":\"cape.blockymodel\",\"Textures\":{\"Default\":{\"Texture\":\"body.png\"}}},{\"Id\":\"Disabled\",\"HeadAccessoryType\":\"HalfCovering\",\"DisableCharacterPartCategory\":\"Haircut\",\"Model\":\"cape.blockymodel\",\"Textures\":{\"Default\":{\"Texture\":\"body.png\"}}}]");
            add(zip,"Cosmetics/CharacterCreator/BodyCharacteristics.json","[{\"Id\":\"Default\",\"Model\":\"body.blockymodel\",\"GreyscaleTexture\":\"body.png\",\"GradientSet\":\"Skin\"}]");
            add(zip,"Cosmetics/CharacterCreator/Capes.json","[{\"Id\":\"Test\",\"Model\":\"cape.blockymodel\",\"Textures\":{\"Green\":{\"Texture\":\"body.png\"}}}]");
            add(zip,"Cosmetics/CharacterCreator/GradientSets.json","[{\"Id\":\"Skin\",\"Gradients\":{\"Warm\":{\"Texture\":\"gradient.png\"}}}]");
            add(zip,"Common/body.blockymodel","{\"nodes\":[{\"name\":\"Chest\",\"position\":{\"y\":10},\"shape\":{\"offset\":{\"y\":4},\"type\":\"box\",\"settings\":{\"size\":{\"x\":2,\"y\":2,\"z\":2}},\"textureLayout\":{\"front\":{\"offset\":{\"x\":0,\"y\":0}}}}}]}");
            add(zip,"Common/cape.blockymodel","{\"nodes\":[{\"name\":\"Chest\",\"position\":{\"y\":999},\"shape\":{\"type\":\"none\",\"settings\":{\"isPiece\":true}},\"children\":[{\"name\":\"Cloth\",\"position\":{\"y\":2},\"shape\":{\"type\":\"quad\",\"settings\":{\"normal\":\"+Z\",\"size\":{\"x\":2,\"y\":2}},\"textureLayout\":{\"front\":{}}}}]}]}");
            BufferedImage texture=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);texture.setRGB(0,0,0xff808080);png(zip,"Common/body.png",texture);
            BufferedImage gradient=new BufferedImage(256,1,BufferedImage.TYPE_INT_ARGB);for(int x=0;x<256;x++)gradient.setRGB(x,0,0xffaa3300);png(zip,"Common/gradient.png",gradient);
        }
        return file;
    }
    private static void add(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
    private static void png(ZipOutputStream zip,String name,BufferedImage image)throws IOException{var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);zip.putNextEntry(new ZipEntry(name));zip.write(bytes.toByteArray());zip.closeEntry();}
    private static boolean hasId(Node node,String id) { if(id.equals(node.getId()))return true; if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())if(hasId(child,id))return true;return false; }
    private static MeshView find(Node n,String id){if(n instanceof MeshView m && id.equals(m.getId()))return m;if(n instanceof Parent p)for(Node c:p.getChildrenUnmodifiable()){MeshView m=find(c,id);if(m!=null)return m;}return null;}
    private static int meshCount(Node n){int count=n instanceof MeshView?1:0;if(n instanceof Parent p)for(Node c:p.getChildrenUnmodifiable())count+=meshCount(c);return count;}
}
