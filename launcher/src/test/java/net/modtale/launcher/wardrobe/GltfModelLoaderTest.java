package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.scene.Group;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GltfModelLoaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void loadsEmbeddedTextureNormalsUvsAndTriangles() throws Exception {
        Fixture f = new Fixture();
        Group root = GltfModelLoader.load(f.bytes());
        MeshView view = mesh(root);
        TriangleMesh mesh = (TriangleMesh)view.getMesh();
        assertEquals(9,mesh.getPoints().size());
        assertEquals(9,mesh.getNormals().size());
        assertEquals(6,mesh.getTexCoords().size());
        assertArrayEquals(new int[]{0,0,0,1,1,1,2,2,2},mesh.getFaces().toArray(null));
        var image = ((PhongMaterial)view.getMaterial()).getDiffuseMap();
        assertEquals(2,image.getWidth());
        assertEquals(0,image.getPixelReader().getArgb(0,0) >>> 24);
        assertEquals(255,image.getPixelReader().getArgb(1,0) >>> 24);
    }

    @Test void composesParentTrsAndChildColumnMajorMatrix() throws Exception {
        Fixture f = new Fixture();
        f.json.set("nodes",JSON.readTree("""
                [{"translation":[10,0,0],"rotation":[0,0,0.7071067811865476,0.7071067811865476],"scale":[2,2,2],"children":[1]},
                 {"mesh":0,"matrix":[1,0,0,0,0,1,0,0,0,0,1,0,1,0,0,1]}]
                """));
        Group parent = (Group)GltfModelLoader.load(f.bytes()).getChildren().getFirst();
        Group child = (Group)parent.getChildren().getFirst();
        var point = parent.localToParent(child.localToParent(0,0,0));
        assertEquals(10,point.getX(),1e-8);
        assertEquals(2,point.getY(),1e-8);
    }

    @Test void handlesInterleavedAccessorOffsetsAndUnsignedIntIndices() throws Exception {
        Fixture f = new Fixture();
        // Position and normal share a 24-byte stride, with a 12-byte normal offset.
        ByteBuffer b=ByteBuffer.wrap(f.bin).order(ByteOrder.LITTLE_ENDIAN);
        float[] vertices={0,0,0,0,0,1, 1,0,0,0,0,1, 0,1,0,0,0,1};
        for(int i=0;i<vertices.length;i++) b.putFloat(i*4,vertices[i]);
        ((ObjectNode)f.json.path("bufferViews").get(0)).put("byteLength",72).put("byteStride",24);
        ((ObjectNode)f.json.path("accessors").get(1)).put("bufferView",0).put("byteOffset",12);
        TriangleMesh mesh=(TriangleMesh)mesh(GltfModelLoader.load(f.bytes())).getMesh();
        assertArrayEquals(new float[]{0,0,0,1,0,0,0,1,0},mesh.getPoints().toArray(null));
        assertArrayEquals(new float[]{0,0,1,0,0,1,0,0,1},mesh.getNormals().toArray(null));
    }

    @Test void supportsUnindexedTrianglesWithoutNormalsOrUvs() throws Exception {
        Fixture f=new Fixture();
        ObjectNode p=(ObjectNode)f.json.path("meshes").get(0).path("primitives").get(0);
        p.remove("indices"); p.remove("material");
        ((ObjectNode)p.path("attributes")).remove(java.util.List.of("NORMAL","TEXCOORD_0"));
        TriangleMesh m=(TriangleMesh)mesh(GltfModelLoader.load(f.bytes())).getMesh();
        assertArrayEquals(new int[]{0,0,1,0,2,0},m.getFaces().toArray(null));
    }

    @Test void respectsSelectedSceneAndNodeInstancing() throws Exception {
        Fixture f=new Fixture();
        f.json.set("nodes",JSON.readTree("[{\"mesh\":0},{\"mesh\":0,\"translation\":[3,0,0]}]"));
        f.json.set("scenes",JSON.readTree("[{\"nodes\":[0]},{\"nodes\":[0,1]}]"));
        f.json.put("scene",1);
        assertEquals(2,GltfModelLoader.load(f.bytes()).getChildren().size());
    }

    @Test void rejectsBrokenHeadersChunksAndTruncation() throws Exception {
        byte[] valid=new Fixture().bytes();
        for(int length : new int[]{0,12,27,valid.length-1}) assertThrows(IOException.class,()->GltfModelLoader.load(java.util.Arrays.copyOf(valid,length)));
        byte[] bad=valid.clone(); bad[0]=0;
        byte[] header=bad;
        assertThrows(IOException.class,()->GltfModelLoader.load(header));
        bad=valid.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(12,Integer.MAX_VALUE);
        byte[] chunk=bad;
        assertThrows(IOException.class,()->GltfModelLoader.load(chunk));
    }

    @Test void rejectsOutOfRangeAccessorsIndicesAndNonFinitePositions() throws Exception {
        Fixture f=new Fixture();
        ((ObjectNode)f.json.path("accessors").get(0)).put("byteOffset",1_000_000);
        assertThrows(IOException.class,()->GltfModelLoader.load(f.bytes()));
        Fixture index=new Fixture(); ByteBuffer.wrap(index.bin).order(ByteOrder.LITTLE_ENDIAN).putInt(96,0xffffffff);
        assertThrows(IOException.class,()->GltfModelLoader.load(index.bytes()));
        Fixture nan=new Fixture(); ByteBuffer.wrap(nan.bin).order(ByteOrder.LITTLE_ENDIAN).putFloat(0,Float.NaN);
        assertThrows(IOException.class,()->GltfModelLoader.load(nan.bytes()));
    }

    @Test void rejectsCyclesExternalTexturesAndUnsupportedFeatures() throws Exception {
        Fixture cycle=new Fixture(); ((ObjectNode)cycle.json.path("nodes").get(0)).putArray("children").add(0);
        assertThrows(IOException.class,()->GltfModelLoader.load(cycle.bytes()));
        Fixture external=new Fixture(); ((ObjectNode)external.json.path("images").get(0)).put("uri","https://example.com/image.png");
        assertThrows(IOException.class,()->GltfModelLoader.load(external.bytes()));
        Fixture extension=new Fixture(); extension.json.putArray("extensionsRequired").add("KHR_draco_mesh_compression");
        assertThrows(IOException.class,()->GltfModelLoader.load(extension.bytes()));
        Fixture sparse=new Fixture(); ((ObjectNode)sparse.json.path("accessors").get(0)).putObject("sparse");
        assertThrows(IOException.class,()->GltfModelLoader.load(sparse.bytes()));
    }

    @Test void loadsActualHyvatarSampleWhenProvided() throws Exception {
        String sample=System.getenv("WARDROBE_SAMPLE_GLB");
        assumeTrue(sample != null,"Set WARDROBE_SAMPLE_GLB to a curl-downloaded Hyvatar GLB for live-format validation");
        Group root=GltfModelLoader.load(Files.readAllBytes(Path.of(sample)));
        assertFalse(root.getChildren().isEmpty());
        Group node=(Group)root.getChildren().getFirst();
        assertTrue(node.getChildren().size()>1);
        for(var child:node.getChildren()) {
            MeshView mesh=(MeshView)child;
            assertNotNull(((PhongMaterial)mesh.getMaterial()).getDiffuseMap());
            assertTrue(((TriangleMesh)mesh.getMesh()).getFaces().size()>0);
        }
    }

    private static MeshView mesh(Group root) { return (MeshView)((Group)root.getChildren().getFirst()).getChildren().getFirst(); }

    private static final class Fixture {
        final ObjectNode json;
        final byte[] bin;
        Fixture() throws Exception {
            BufferedImage image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,0x40ff0000); image.setRGB(1,0,0xffff0000);
            var png=new ByteArrayOutputStream(); ImageIO.write(image,"png",png);
            byte[] texture=png.toByteArray();
            bin=new byte[108+texture.length];
            ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
            for(float n:new float[]{0,0,0,1,0,0,0,1,0, 0,0,1,0,0,1,0,0,1, 0,0,1,0,0,1}) b.putFloat(n);
            b.putInt(0).putInt(1).putInt(2).put(texture);
            json=(ObjectNode)JSON.readTree("""
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":0}],
                "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":36},
                {"buffer":0,"byteOffset":72,"byteLength":24},{"buffer":0,"byteOffset":96,"byteLength":12},
                {"buffer":0,"byteOffset":108,"byteLength":0}],
                "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":2,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":3,"componentType":5125,"count":3,"type":"SCALAR"}],
                "images":[{"bufferView":4,"mimeType":"image/png"}],"textures":[{"source":0}],
                "materials":[{"alphaMode":"MASK","pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
                "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0}]}],
                "nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """);
            ((ObjectNode)json.path("buffers").get(0)).put("byteLength",bin.length);
            ((ObjectNode)json.path("bufferViews").get(4)).put("byteLength",texture.length);
        }
        byte[] bytes() throws IOException {
            byte[] text=JSON.writeValueAsBytes(json);
            int jsonSize=(text.length+3)&~3, binSize=(bin.length+3)&~3;
            ByteBuffer out=ByteBuffer.allocate(28+jsonSize+binSize).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(0x46546c67).putInt(2).putInt(out.capacity()).putInt(jsonSize).putInt(0x4e4f534a).put(text);
            while(out.position()<20+jsonSize)out.put((byte)' ');
            out.putInt(binSize).putInt(0x004e4942).put(bin);
            return out.array();
        }
    }
}
