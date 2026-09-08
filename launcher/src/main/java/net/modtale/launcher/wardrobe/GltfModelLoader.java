package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

/** Bounded, self-contained GLB 2.0 loader for Hyvatar's static avatar exports.
 * Builds a detached scene graph; call on a worker and attach the result on the FX thread.
 * Unsupported compression, external resources, animation and skinning fail explicitly.
 */
public final class GltfModelLoader {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ELEMENTS = 1_000_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JsonNode document;
    private final ByteBuffer binary;
    private int nodes;
    private long elements;
    private long vertexValues;
    private long texturePixels;
    private final java.util.Map<Integer, PhongMaterial> materials = new java.util.HashMap<>();

    private GltfModelLoader(JsonNode document, ByteBuffer binary) {
        this.document = document;
        this.binary = binary;
    }

    public static Group load(byte[] bytes) throws IOException {
        try {
            if (bytes == null || bytes.length < 28 || bytes.length > MAX_BYTES) fail("Invalid GLB size");
            ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (input.getInt() != 0x46546c67 || input.getInt() != 2 || input.getInt() != bytes.length)
                fail("Expected a GLB 2.0 file");
            JsonNode json = null;
            ByteBuffer bin = null;
            int chunk = 0;
            while (input.hasRemaining()) {
                if (input.remaining() < 8) fail("Truncated GLB chunk");
                int length = input.getInt(), type = input.getInt();
                if (length < 0 || length > input.remaining() || length % 4 != 0) fail("Invalid GLB chunk length");
                if (chunk++ == 0 && type != 0x4e4f534a) fail("JSON must be the first GLB chunk");
                ByteBuffer data = input.slice().order(ByteOrder.LITTLE_ENDIAN);
                data.limit(length);
                if (type == 0x4e4f534a) {
                    if (json != null) fail("Duplicate JSON chunk");
                    byte[] text = new byte[length]; data.get(text);
                    json = JSON.readTree(text);
                } else if (type == 0x004e4942) {
                    if (bin != null) fail("Duplicate binary chunk");
                    bin = data;
                }
                input.position(input.position() + length);
            }
            if (json == null || bin == null || !"2.0".equals(json.path("asset").path("version").asText()))
                fail("Missing GLB 2.0 data");
            if (json.path("extensionsRequired").size() != 0 || json.path("skins").size() != 0
                    || json.path("animations").size() != 0) fail("Unsupported required extension, skinning or animation");
            JsonNode buffers = json.path("buffers");
            if (buffers.size() != 1 || buffers.get(0).has("uri")) fail("Only embedded GLB buffers are supported");
            int declared = integer(buffers.get(0), "byteLength", -1);
            if (declared < 0 || declared > bin.limit() || bin.limit() - declared > 3) fail("Invalid binary buffer length");
            bin.limit(declared);
            return new GltfModelLoader(json, bin).scene();
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw new IOException("Malformed GLB data", ex);
        }
    }

    private Group scene() throws IOException {
        JsonNode scene = entry("scenes", integer(document, "scene", 0));
        Group root = new Group();
        for (JsonNode id : scene.path("nodes")) root.getChildren().add(node(index(id), new HashSet<>(), 0));
        if (elements == 0) fail("GLB scene has no triangles");
        return root;
    }

    private Group node(int id, Set<Integer> ancestors, int depth) throws IOException {
        if (Thread.currentThread().isInterrupted()) fail("GLB loading cancelled");
        if (depth > 64 || ++nodes > 4096 || !ancestors.add(id)) fail("Invalid or excessive node hierarchy");
        JsonNode node = entry("nodes", id);
        if (node.has("skin")) fail("Skinned nodes are unsupported");
        Group group = new Group();
        group.setId(node.path("name").asText("node-" + id));
        if (node.has("matrix")) {
            if (node.has("translation") || node.has("rotation") || node.has("scale")) fail("Mixed matrix and TRS transform");
            double[] m = vector(node.get("matrix"), 16);
            if (m[3] != 0 || m[7] != 0 || m[11] != 0 || m[15] != 1) fail("Non-affine node matrix");
            group.getTransforms().add(new Affine(m[0], m[4], m[8], m[12], m[1], m[5], m[9], m[13], m[2], m[6], m[10], m[14]));
        } else {
            if (node.has("translation")) {
                double[] v = vector(node.get("translation"), 3);
                group.getTransforms().add(new Translate(v[0], v[1], v[2]));
            }
            if (node.has("rotation")) {
                double[] q = vector(node.get("rotation"), 4);
                double length = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
                if (length < 1e-12) fail("Invalid node quaternion");
                double x=q[0]/length, y=q[1]/length, z=q[2]/length, w=q[3]/length;
                group.getTransforms().add(new Affine(1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w), 0,
                        2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w), 0,
                        2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y), 0));
            }
            if (node.has("scale")) {
                double[] v = vector(node.get("scale"), 3);
                group.getTransforms().add(new Scale(v[0], v[1], v[2]));
            }
        }
        if (node.has("mesh")) {
            JsonNode mesh = entry("meshes", index(node.get("mesh")));
            for (JsonNode primitive : mesh.path("primitives")) group.getChildren().add(primitive(primitive));
        }
        for (JsonNode child : node.path("children")) group.getChildren().add(node(index(child), ancestors, depth + 1));
        ancestors.remove(id);
        return group;
    }

    private MeshView primitive(JsonNode primitive) throws IOException {
        if (integer(primitive, "mode", 4) != 4 || primitive.has("targets") || primitive.has("extensions"))
            fail("Only uncompressed static triangles are supported");
        JsonNode attributes = primitive.path("attributes");
        float[] points = floats(index(attributes.path("POSITION")), "VEC3", 3);
        int count = points.length / 3;
        float[] uv = attributes.has("TEXCOORD_0") ? floats(index(attributes.get("TEXCOORD_0")), "VEC2", 2) : new float[]{0, 0};
        float[] normals = attributes.has("NORMAL") ? floats(index(attributes.get("NORMAL")), "VEC3", 3) : null;
        if ((uv.length != 2 && uv.length / 2 != count) || (attributes.has("TEXCOORD_0") && uv.length / 2 != count)
                || (normals != null && normals.length != points.length)) fail("Mismatched vertex attributes");
        int[] indices;
        if (primitive.has("indices")) {
            Accessor a = accessor(index(primitive.get("indices")), "SCALAR", 1);
            if (a.type != 5121 && a.type != 5123 && a.type != 5125 || a.normalized) fail("Invalid index component type");
            indices = new int[a.count];
            for (int i=0; i<indices.length; i++) {
                long value = switch (a.type) {
                    case 5121 -> Byte.toUnsignedInt(binary.get(a.offset + i*a.stride));
                    case 5123 -> Short.toUnsignedInt(binary.getShort(a.offset + i*a.stride));
                    default -> Integer.toUnsignedLong(binary.getInt(a.offset + i*a.stride));
                };
                if (value >= count) fail("Triangle index out of bounds");
                indices[i] = (int) value;
            }
        } else {
            indices = new int[count];
            for (int i=0; i<count; i++) indices[i]=i;
        }
        elements += indices.length;
        if (indices.length == 0 || indices.length % 3 != 0 || elements > MAX_ELEMENTS) fail("Invalid or excessive triangles");
        TriangleMesh mesh = new TriangleMesh(normals == null ? VertexFormat.POINT_TEXCOORD : VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(uv);
        if (normals != null) mesh.getNormals().setAll(normals);
        int width = normals == null ? 2 : 3;
        int[] faces = new int[indices.length * width];
        for (int i=0; i<indices.length; i++) {
            faces[i*width] = indices[i];
            if (normals != null) faces[i*width+1] = indices[i];
            faces[i*width+width-1] = attributes.has("TEXCOORD_0") ? indices[i] : 0;
        }
        mesh.getFaces().setAll(faces);
        mesh.getFaceSmoothingGroups().setAll(new int[indices.length / 3]);
        MeshView view = new MeshView(mesh);
        int material = integer(primitive, "material", -1);
        view.setMaterial(material(material));
        // Mirrored node transforms and paper-thin cosmetic layers must remain visible.
        view.setCullFace(CullFace.NONE);
        return view;
    }

    private PhongMaterial material(int id) throws IOException {
        if (materials.containsKey(id)) return materials.get(id);
        JsonNode material = id < 0 ? JSON.createObjectNode() : entry("materials", id);
        JsonNode pbr = material.path("pbrMetallicRoughness");
        double[] factor = pbr.has("baseColorFactor") ? vector(pbr.get("baseColorFactor"), 4) : new double[]{1,1,1,1};
        for (double f : factor) if (f < 0 || f > 1) fail("Invalid material color");
        String alpha = material.path("alphaMode").asText("OPAQUE");
        if (!Set.of("OPAQUE", "MASK", "BLEND").contains(alpha)) fail("Invalid alpha mode");
        double cutoff = material.path("alphaCutoff").asDouble(0.5);
        if (!Double.isFinite(cutoff) || cutoff < 0 || cutoff > 1) fail("Invalid alpha cutoff");
        PhongMaterial result = new PhongMaterial(Color.color(factor[0],factor[1],factor[2], alpha.equals("OPAQUE") ? 1 : factor[3]));
        result.setSpecularColor(Color.BLACK);
        if (pbr.has("baseColorTexture")) {
            JsonNode textureInfo = pbr.get("baseColorTexture");
            if (integer(textureInfo, "texCoord", 0) != 0 || textureInfo.has("extensions")) fail("Unsupported texture coordinates");
            JsonNode texture = entry("textures", index(textureInfo.path("index")));
            JsonNode image = entry("images", index(texture.path("source")));
            if (image.has("uri") || !"image/png".equals(image.path("mimeType").asText())) fail("Expected embedded PNG texture");
            JsonNode view = entry("bufferViews", index(image.path("bufferView")));
            int start = viewOffset(view), length = integer(view, "byteLength", -1);
            byte[] bytes = new byte[length];
            binary.duplicate().position(start).get(bytes);
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) fail("Invalid PNG texture");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    texturePixels += (long) w*h;
                    if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || texturePixels > 16_777_216) fail("Texture dimensions exceed limit");
                    var decoded = reader.read(0);
                    WritableImage map = new WritableImage(w,h);
                    var writer = map.getPixelWriter();
                    for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
                        int pixel = decoded.getRGB(x,y);
                        double opacity = (pixel >>> 24) / 255.0 * factor[3];
                        int a = alpha.equals("OPAQUE") ? 255 : alpha.equals("MASK") ? (opacity >= cutoff ? 255 : 0) : (int)Math.round(opacity*255);
                        int r = (int)(((pixel >>> 16)&255)*factor[0]);
                        int g = (int)(((pixel >>> 8)&255)*factor[1]);
                        int b = (int)((pixel&255)*factor[2]);
                        writer.setArgb(x,y,(a<<24)|(r<<16)|(g<<8)|b);
                    }
                    result.setDiffuseColor(Color.WHITE);
                    result.setDiffuseMap(map);
                } finally { reader.dispose(); }
            }
        }
        materials.put(id,result);
        return result;
    }

    private float[] floats(int id, String shape, int components) throws IOException {
        Accessor a = accessor(id, shape, components);
        if (a.type != 5126 && !(a.normalized && (a.type == 5121 || a.type == 5123))) fail("Unsupported vertex component type");
        vertexValues += (long)a.count * components;
        if (vertexValues > 8_000_000) fail("Excessive vertex data");
        float[] values = new float[a.count * components];
        for (int i=0; i<a.count; i++) for (int c=0; c<components; c++) {
            int offset = a.offset+i*a.stride+c*a.size;
            float value = switch (a.type) {
                case 5121 -> Byte.toUnsignedInt(binary.get(offset))/255f;
                case 5123 -> Short.toUnsignedInt(binary.getShort(offset))/65535f;
                default -> binary.getFloat(offset);
            };
            if (!Float.isFinite(value) || Math.abs(value) > 1e8) fail("Invalid vertex value");
            values[i*components+c] = value;
        }
        return values;
    }

    private Accessor accessor(int id, String shape, int components) throws IOException {
        JsonNode a = entry("accessors",id);
        if (a.has("sparse") || !shape.equals(a.path("type").asText())) fail("Unsupported accessor shape or sparse data");
        int type = integer(a,"componentType",-1);
        int size = switch(type) { case 5121 -> 1; case 5123 -> 2; case 5125,5126 -> 4; default -> 0; };
        int count = integer(a,"count",-1);
        if (size == 0 || count <= 0 || count > MAX_ELEMENTS) fail("Invalid accessor type or count");
        JsonNode view = entry("bufferViews", index(a.path("bufferView")));
        int start = viewOffset(view), offset = integer(a,"byteOffset",0), stride = integer(view,"byteStride",size*components);
        if (offset < 0 || stride < size*components || stride > 252 || stride % size != 0 || offset % size != 0
                || (long) offset+(long)(count-1)*stride+size*components > integer(view,"byteLength",-1)) fail("Accessor exceeds buffer view");
        return new Accessor(start+offset,stride,size,count,type,a.path("normalized").asBoolean(false));
    }

    private int viewOffset(JsonNode view) throws IOException {
        int offset = integer(view,"byteOffset",0), length = integer(view,"byteLength",-1);
        if (integer(view,"buffer",-1) != 0 || offset < 0 || length < 0 || (long)offset+length > binary.limit()) fail("Invalid buffer view");
        return offset;
    }

    private JsonNode entry(String name, int id) throws IOException {
        JsonNode array = document.path(name);
        if (!array.isArray() || id < 0 || id >= array.size() || !array.get(id).isObject()) fail("Invalid " + name + " reference");
        return array.get(id);
    }

    private static int index(JsonNode node) throws IOException {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) fail("Invalid GLB index");
        return node.intValue();
    }

    private static int integer(JsonNode node, String key, int fallback) throws IOException {
        return node.has(key) ? index(node.get(key)) : fallback;
    }

    private static double[] vector(JsonNode array, int count) throws IOException {
        if (!array.isArray() || array.size() != count) fail("Invalid transform or color vector");
        double[] result = new double[count];
        for (int i=0;i<count;i++) {
            if (!array.get(i).isNumber()) fail("Non-numeric vector");
            result[i]=array.get(i).doubleValue();
            if (!Double.isFinite(result[i]) || Math.abs(result[i]) > 1e8) fail("Invalid vector value");
        }
        return result;
    }

    private static void fail(String message) throws IOException { throw new IOException(message); }
    private record Accessor(int offset, int stride, int size, int count, int type, boolean normalized) {}
}
