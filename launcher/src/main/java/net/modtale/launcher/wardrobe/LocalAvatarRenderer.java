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
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads installed Hytale assets only. No network, account writes, extraction or invented avatar.
 * Returns a detached, Y-up scene graph. Invoke on a worker, then attach on the FX thread.
 * Blockymodel attachment/offset and UV conventions follow Hypixel's Blockbench format.
 */
public final class LocalAvatarRenderer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ZipFile zip;
    private final CosmeticCatalogClient catalog;
    private final Map<String,JsonNode> jsonCache = new HashMap<>();
    private final Map<String,BufferedImage> imageCache = new HashMap<>();
    private final Map<String,Group> skeleton = new HashMap<>();
    private final Rig rig = new Rig();
    private long bytesRead, pixels;
    private int nodeCount;

    private LocalAvatarRenderer(ZipFile zip, CosmeticCatalogClient catalog) { this.zip=zip; this.catalog=catalog; }

    public static Group load(Path assetsZip, JsonNode skin) throws IOException {
        if (assetsZip == null || skin == null || !skin.isObject()) throw new IOException("Choose an installed Assets.zip and an outfit object");
        return load(assetsZip, skin, new CosmeticCatalogClient(assetsZip));
    }

    static Group load(Path assetsZip, JsonNode skin, CosmeticCatalogClient catalog) throws IOException {
        try (ZipFile zip=new ZipFile(assetsZip.toFile())) {
            return new LocalAvatarRenderer(zip, catalog).composition(skin);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid local avatar data: " + ex.getMessage(),ex); }
    }

    private Group composition(JsonNode skin) throws IOException {
        Map<String,Part> parts=new LinkedHashMap<>();
        for (var resolved : catalog.resolveComposition(skin)) {
            parts.put(resolved.category(), part(resolved.category(), resolved.selectedId(), resolved.definition()));
        }
        // Explicit category suppression is authoritative; entitlement metadata never affects visual assembly.
        Set<String> disabled=new HashSet<>();
        for(Part part:parts.values()) {
            JsonNode value=part.definition.path("DisableCharacterPartCategory");
            if(value.isTextual()) disabled.add(value.asText().toLowerCase(Locale.ROOT));
            else if(value.isArray())for(JsonNode v:value)disabled.add(v.asText().toLowerCase(Locale.ROOT));
        }
        parts.keySet().removeIf(k->!k.equals("bodyCharacteristic") && disabled.contains(k.toLowerCase(Locale.ROOT)));
        Part hat=parts.get("headAccessory"), hair=parts.get("haircut");
        if(hat!=null && hair!=null) {
            String covering=hat.definition.path("HeadAccessoryType").asText();
            if(covering.equals("FullyCovering"))parts.remove("haircut");
            else if(covering.equals("HalfCovering") && hair.definition.path("RequiresGenericHaircut").asBoolean()) {
                String fallback=json("Cosmetics/CharacterCreator/HaircutFallbacks.json").path(hair.definition.path("HairType").asText()).asText();
                if(fallback.isBlank())throw new IOException("Missing haircut fallback for "+hair.selectedId);
                parts.put("haircut",part("haircut",fallback+"."+colorId(hair.selectedId),catalog.resolve("haircut",fallback+"."+colorId(hair.selectedId))));
            }
        }
        Group root=new Group();root.setId("local-avatar");
        Part base=parts.remove("bodyCharacteristic");
        append(root,base,false);
        for(Part part:parts.values())append(root,part,true);
        rig.options = java.util.stream.Stream.of("Emotes", "EmotesFace", "EmotesInGame")
                .flatMap(kind -> {
                    try { return catalog.animations(kind).stream(); }
                    catch (IllegalArgumentException unavailable) { return java.util.stream.Stream.empty(); }
                }).toList();
        root.getProperties().put(Rig.class, rig);
        return root;
    }

    private static String colorId(String selection) {String[] ids=selection.split("\\.",-1);return ids.length>1?ids[1]:"";}
    private static Part part(String category, String selectedId, JsonNode definition) throws IOException {
        String texture=definition.path("Texture").asText(), gradient=definition.path("GradientTexture").asText();
        if (texture.isBlank() || definition.path("Model").asText().isBlank()) throw new IOException("Missing local model/texture for " + selectedId);
        return new Part(category,selectedId,definition,texture,gradient);
    }

    private void append(Group root,Part part,boolean attachment) throws IOException {
        PhongMaterial material=material(part);
        JsonNode model=json(common(part.definition.path("Model").asText()));
        if(!model.path("nodes").isArray())throw new IOException("Model has no node array");
        Group layer=new Group();layer.setId(part.category+":"+part.selectedId);root.getChildren().add(layer);
        for(JsonNode node:model.get("nodes"))node(node,layer,material,attachment,0,part.category);
    }

    private void node(JsonNode data,Group parent,PhongMaterial material,boolean attachment,int depth,String category) throws IOException {
        checkCancelled();
        if(depth>96 || ++nodeCount>20_000)throw new IOException("Local model hierarchy exceeds limits");
        JsonNode shape=data.path("shape");
        String name=data.path("name").asText();
        boolean piece=attachment && shape.path("settings").path("isPiece").asBoolean();
        Group target=piece?skeleton.get(name):null;
        // Unmatched piece markers are retained in authored parent space, as in the Blockbench importer.
        Group frame=new Group();frame.setId(category+":"+name);
        if(target!=null)target.getChildren().add(frame);
        else {
            parent.getChildren().add(frame);
            double[] pos=vector(data.path("position"),0);
            frame.getTransforms().add(new Translate(pos[0],pos[1],pos[2]));
            frame.getTransforms().add(quaternion(data.path("orientation")));
        }
        double[] offset=vector(shape.path("offset"),0);
        MeshView animatedMesh = null;
        if(!shape.path("type").asText("none").equals("none")) {
            MeshView mesh=geometry(shape,material);
            mesh.setId(category+":"+name+":mesh");
            mesh.setVisible(shape.path("visible").asBoolean(true));
            animatedMesh = mesh;
            mesh.getTransforms().add(new Translate(offset[0],offset[1],offset[2]));
            double[] stretch=vector(shape.path("stretch"),1);
            mesh.getTransforms().add(new Scale(stretch[0],stretch[1],stretch[2]));
            frame.getChildren().add(mesh);
        }
        rig.bindings.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new Binding(frame, animatedMesh, target == null));
        Group anchor=new Group();anchor.setId(category+":"+name+":anchor");
        if(!piece)anchor.getTransforms().add(new Translate(offset[0],offset[1],offset[2]));
        frame.getChildren().add(anchor);
        skeleton.putIfAbsent(name,anchor);
        for(JsonNode child:data.path("children"))node(child,anchor,material,attachment,depth+1,category);
    }

    private MeshView geometry(JsonNode shape,PhongMaterial material) throws IOException {
        String type=shape.path("type").asText();
        if(!type.equals("box")&&!type.equals("quad"))throw new IOException("Unsupported blockymodel shape: "+type);
        double[] size=vector(shape.path("settings").path("size"),0);
        for(double d:size)if(d<0)throw new IOException("Negative shape size");
        String normal=shape.path("settings").path("normal").asText("+Z");
        String onlyFace="";
        if(type.equals("quad")) {
            onlyFace=switch(normal){case "+X"->"right";case "-X"->"left";case "+Y"->"top";case "-Y"->"bottom";case "+Z"->"front";case "-Z"->"back";default->throw new IOException("Unknown quad normal");};
            if(normal.endsWith("X"))size=new double[]{0,size[1],size[0]};
            else if(normal.endsWith("Y"))size=new double[]{size[0],0,size[1]};
            else size[2]=0;
        }
        double x=size[0]/2,y=size[1]/2,z=size[2]/2;
        String[] faces={"front","back","left","right","top","bottom"};
        double[][] corners={
                {-x,y,z, x,y,z, x,-y,z, -x,-y,z},
                {x,y,-z, -x,y,-z, -x,-y,-z, x,-y,-z},
                {-x,y,-z, -x,y,z, -x,-y,z, -x,-y,-z},
                {x,y,z, x,y,-z, x,-y,-z, x,-y,z},
                {-x,y,-z, x,y,-z, x,y,z, -x,y,z},
                {-x,-y,z, x,-y,z, x,-y,-z, -x,-y,-z}};
        TriangleMesh mesh=new TriangleMesh();
        double width=material.getDiffuseMap().getWidth(),height=material.getDiffuseMap().getHeight();
        for(int f=0;f<faces.length;f++) {
            if(!onlyFace.isEmpty()&&!onlyFace.equals(faces[f]))continue;
            JsonNode layout=shape.path("textureLayout").get(onlyFace.isEmpty()?faces[f]:"front");
            if(layout==null)continue;
            double w=f<2?size[0]:f<4?size[2]:size[0], h=f<4?size[1]:size[2];
            int first=mesh.getPoints().size()/3;
            float[] positions=new float[12];for(int i=0;i<12;i++)positions[i]=(float)corners[f][i];mesh.getPoints().addAll(positions);
            mesh.getTexCoords().addAll(uv(layout,w,h,width,height));
            // Outward winding; draw both sides for the paper-thin cosmetic sheets.
            mesh.getFaces().addAll(first,first,first+2,first+2,first+1,first+1,first,first,first+3,first+3,first+2,first+2);
            mesh.getFaceSmoothingGroups().addAll(0,0);
        }
        MeshView result=new MeshView(mesh);result.setMaterial(material);result.setCullFace(CullFace.NONE);return result;
    }

    /** UV offsets are signed anchors, not top-left bounds; quarter-turns rotate around that anchor. */
    static float[] uv(JsonNode layout,double width,double height,double atlasWidth,double atlasHeight) throws IOException {
        double ox=number(layout.path("offset").path("x"),0),oy=number(layout.path("offset").path("y"),0);
        double mx=layout.path("mirror").path("x").asBoolean()?-1:1,my=layout.path("mirror").path("y").asBoolean()?-1:1;
        int angle=layout.path("angle").asInt();
        if(angle!=0&&angle!=90&&angle!=180&&angle!=270)throw new IOException("Invalid texture quarter-turn");
        float[] result=new float[8];double[] u={0,1,1,0},v={0,0,1,1};
        for(int i=0;i<4;i++) {
            double a=u[i]*width*mx,b=v[i]*height*my;
            double tx=switch(angle){case 90->-b;case 180->-a;case 270->b;default->a;};
            double ty=switch(angle){case 90->a;case 180->-b;case 270->-a;default->b;};
            result[2*i]=(float)((ox+tx)/atlasWidth);result[2*i+1]=(float)((oy+ty)/atlasHeight);
        }
        return result;
    }

    private PhongMaterial material(Part part) throws IOException {
        BufferedImage source=image(common(part.texture));
        BufferedImage gradient=part.gradient.isBlank()?null:image(common(part.gradient));
        WritableImage texture=new WritableImage(source.getWidth(),source.getHeight());
        for(int y=0;y<source.getHeight();y++) {
            checkCancelled();
            for(int x=0;x<source.getWidth();x++) {
                int pixel=source.getRGB(x,y);
                if(gradient!=null) {
                    int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                    // Only neutral texels are gradient masks; colored sclera/details remain authored colors.
                    if(r==g && g==b) {
                        int color=gradient.getRGB((int)Math.round(r/255.0*(gradient.getWidth()-1)),gradient.getHeight()/2);
                        pixel=(pixel&0xff000000)|(color&0x00ffffff);
                    }
                }
                texture.getPixelWriter().setArgb(x,y,pixel);
            }
        }
        PhongMaterial material=new PhongMaterial(Color.WHITE);material.setSpecularColor(Color.BLACK);material.setDiffuseMap(texture);return material;
    }

    private BufferedImage image(String path) throws IOException {
        BufferedImage cached=imageCache.get(path);if(cached!=null)return cached;
        byte[] bytes=read(path);
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid texture: "+path);
            var reader=readers.next();
            try {
                reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);pixels+=(long)w*h;
                if(w<=0||h<=0||w>4096||h>4096||pixels>32_000_000)throw new IOException("Avatar texture budget exceeded");
                BufferedImage decoded=reader.read(0);imageCache.put(path,decoded);return decoded;
            } finally {reader.dispose();}
        }
    }
    private JsonNode json(String path) throws IOException {
        if(!jsonCache.containsKey(path))jsonCache.put(path,JSON.readTree(read(path)));
        return jsonCache.get(path);
    }
    private byte[] read(String path) throws IOException {
        checkCancelled();
        if(path.startsWith("/")||path.contains("..")||path.contains("\\")||path.contains(":"))throw new IOException("Invalid asset path");
        var entry=zip.getEntry(path);if(entry==null||entry.isDirectory())throw new IOException("Missing installed asset: "+path);
        if(entry.getSize()>8*1024*1024)throw new IOException("Asset exceeds size limit");
        try(var input=zip.getInputStream(entry)) {
            byte[] bytes=input.readNBytes(8*1024*1024+1);bytesRead+=bytes.length;
            if(bytes.length>8*1024*1024||bytesRead>64*1024*1024)throw new IOException("Avatar asset budget exceeded");return bytes;
        }
    }
    private static String common(String path) {return path.startsWith("Common/")?path:"Common/"+path;}
    private static double number(JsonNode n,double fallback) throws IOException {
        if(n.isMissingNode())return fallback;
        if(!n.isNumber()||!Double.isFinite(n.asDouble())||Math.abs(n.asDouble())>1e6)throw new IOException("Invalid model coordinate");return n.asDouble();
    }
    private static double[] vector(JsonNode n,double fallback) throws IOException {return new double[]{number(n.path("x"),fallback),number(n.path("y"),fallback),number(n.path("z"),fallback)};}
    private static Affine quaternion(JsonNode q) throws IOException {
        double x=number(q.path("x"),0),y=number(q.path("y"),0),z=number(q.path("z"),0),w=number(q.path("w"),1);
        double length=Math.sqrt(x*x+y*y+z*z+w*w);if(length<1e-9)throw new IOException("Invalid model quaternion");x/=length;y/=length;z/=length;w/=length;
        return new Affine(1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w),0,2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w),0,2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y),0);
    }
    /** Animation state belongs to one detached model; call apply/reset on FX once attached. */
    public static Rig rig(Group model) { return (Rig) model.getProperties().get(Rig.class); }

    public static final class Rig {
        private final Map<String,List<Binding>> bindings = new HashMap<>();
        private List<CosmeticCatalogClient.AnimationOption> options = List.of();
        public List<CosmeticCatalogClient.AnimationOption> animations() { return options; }
        public void reset() { bindings.values().forEach(list -> list.forEach(Binding::reset)); }
        public void apply(Clip clip, double seconds) {
            if (!Double.isFinite(seconds)) throw new IllegalArgumentException("Invalid animation time");
            reset();
            double frame = Math.max(0, seconds) * 60;
            if (clip.looping) frame %= clip.duration;
            else if (frame > clip.duration && !clip.hold) return;
            frame = Math.min(frame, clip.duration);
            for (var entry : clip.tracks.entrySet()) {
                for (Binding binding : bindings.getOrDefault(entry.getKey(), List.of())) binding.apply(entry.getValue(), frame);
            }
        }
    }

    private static final class Binding {
        final Group frame;
        final MeshView view;
        final boolean bone;
        final List<javafx.scene.transform.Transform> original;
        final List<javafx.scene.transform.Transform> meshOriginal;
        final float[] uv;
        final boolean visible;
        final double width, height;
        Binding(Group frame, MeshView view, boolean bone) {
            this.frame=frame; this.view=view; this.bone=bone;
            original=List.copyOf(frame.getTransforms());
            meshOriginal=view==null?List.of():List.copyOf(view.getTransforms());
            visible=view!=null && view.isVisible();
            uv=view==null?null:((TriangleMesh)view.getMesh()).getTexCoords().toArray(null);
            var texture=view==null?null:((PhongMaterial)view.getMaterial()).getDiffuseMap();
            width=texture==null?1:texture.getWidth(); height=texture==null?1:texture.getHeight();
        }
        void reset() {
            frame.getTransforms().setAll(original);
            if(view!=null) {
                view.getTransforms().setAll(meshOriginal); view.setVisible(visible);
                ((TriangleMesh)view.getMesh()).getTexCoords().setAll(uv);
            }
        }
        void apply(Map<String,List<Key>> tracks,double time) {
            if(bone) {
                double[] p=sample(tracks.get("position"),time);
                // Position deltas are in parent space; rotation deltas are relative to the rest bone.
                if(p!=null)frame.getTransforms().add(0,new Translate(p[0],p[1],p[2]));
                double[] r=sample(tracks.get("orientation"),time);
                if(r!=null) {
                    frame.getTransforms().add(rotation(r));
                }
            }
            if(view!=null) {
                double[] scale=sample(tracks.get("shapeStretch"),time);
                if(scale!=null)view.getTransforms().add(new Scale(scale[0],scale[1],scale[2]));
                List<Key> visibility=tracks.get("shapeVisible");
                if(visibility!=null && !visibility.isEmpty()) {
                    Key key=null;
                    for(Key next:visibility) { if(next.time>time)break;key=next; }
                    if(key!=null)view.setVisible(key.value[0]!=0);
                }
                double[] offset=null;
                for(Key key:tracks.getOrDefault("shapeUvOffset",List.of())) { if(key.time>time)break;offset=key.value; }
                if(offset!=null) {
                    float[] shifted=uv.clone();
                    for(int i=0;i<shifted.length;i+=2) { shifted[i]+=Math.round(offset[0])/width; shifted[i+1]-=Math.round(offset[1])/height; }
                    ((TriangleMesh)view.getMesh()).getTexCoords().setAll(shifted);
                }
            }
        }
    }

    public static final class Clip {
        private final double duration;
        private final boolean looping, hold;
        private final Map<String,Map<String,List<Key>>> tracks;
        private Clip(double duration,boolean looping,boolean hold,Map<String,Map<String,List<Key>>> tracks) {
            this.duration=duration;this.looping=looping;this.hold=hold;this.tracks=tracks;
        }
        public double durationSeconds() { return duration/60; }
        public boolean looping() { return looping; }
    }
    private record Key(double time,double[] value,boolean smooth) {}

    /** Bounded local ZIP read; invoke on a worker. No clip paths or selections become account data. */
    public static Clip loadAnimation(Path assetsZip,String animation,boolean looping) throws IOException {
        try(ZipFile zip=new ZipFile(assetsZip.toFile())) {
            return parseAnimation(new LocalAvatarRenderer(zip,null).json(common(animation)),looping);
        }
    }
    static Clip parseAnimation(JsonNode data,boolean looping) throws IOException {
        if(data.path("formatVersion").asInt()!=1 || !data.path("nodeAnimations").isObject())throw new IOException("Unsupported animation format");
        double duration=number(data.path("duration"),0);
        if(duration<=0 || duration>216000)throw new IOException("Invalid animation duration");
        Map<String,Map<String,List<Key>>> tracks=new HashMap<>();
        int count=0;
        for(var nodes=data.path("nodeAnimations").properties().iterator();nodes.hasNext();) {
            checkCancelled(); var node=nodes.next(); Map<String,List<Key>> channels=new HashMap<>();
            for(var fields=node.getValue().properties().iterator();fields.hasNext();) {
                var field=fields.next(); String channel=field.getKey();
                if(!Set.of("position","orientation","shapeStretch","shapeVisible","shapeUvOffset").contains(channel))throw new IOException("Unsupported animation channel: "+channel);
                if(!field.getValue().isArray())throw new IOException("Invalid animation track");
                List<Key> keys=new ArrayList<>();
                for(JsonNode key:field.getValue()) {
                    if(++count>100000)throw new IOException("Animation keyframe budget exceeded");
                    double time=number(key.path("time"),-1);
                    if(time<0 || time>duration)throw new IOException("Invalid keyframe time");
                    String interpolation=key.path("interpolationType").asText("linear");
                    if(!interpolation.equals("linear")&&!interpolation.equals("smooth"))throw new IOException("Unsupported interpolation");
                    JsonNode delta=key.path("delta"); double[] value;
                    if(channel.equals("shapeVisible")) {
                        if(!delta.isBoolean())throw new IOException("Invalid visibility keyframe");
                        value=new double[]{delta.asBoolean()?1:0};
                    } else if(channel.equals("orientation")) {
                        value=new double[]{number(delta.path("x"),0),number(delta.path("y"),0),number(delta.path("z"),0),number(delta.path("w"),1)};
                        double length=Math.sqrt(Arrays.stream(value).map(v->v*v).sum());
                        if(length<1e-9)throw new IOException("Invalid animation quaternion");
                        for(int i=0;i<4;i++)value[i]/=length;
                    } else value=vector(delta,channel.equals("shapeStretch")?1:0);
                    keys.add(new Key(time,value,interpolation.equals("smooth")));
                }
                keys.sort(Comparator.comparingDouble(Key::time));
                // Shipped clips contain duplicate timestamps; the last authored key wins.
                for(int i=keys.size()-1;i>0;i--)if(keys.get(i).time==keys.get(i-1).time)keys.remove(i-1);
                channels.put(channel,List.copyOf(keys));
            }
            tracks.put(node.getKey(),Map.copyOf(channels));
        }
        return new Clip(duration,looping,data.path("holdLastKeyframe").asBoolean(),Map.copyOf(tracks));
    }
    private static Affine rotation(double[] q) {
        double x=q[0],y=q[1],z=q[2],w=q[3];
        double length=Math.sqrt(x*x+y*y+z*z+w*w); x/=length;y/=length;z/=length;w/=length;
        return new Affine(1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w),0,2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w),0,2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y),0);
    }
    private static double[] sample(List<Key> keys,double time) {
        if(keys==null || keys.isEmpty())return null;
        if(time<=keys.getFirst().time)return keys.getFirst().value;
        int right=1;while(right<keys.size() && keys.get(right).time<time)right++;
        if(right==keys.size())return keys.getLast().value;
        Key a=keys.get(right-1),b=keys.get(right);
        double t=(time-a.time)/(b.time-a.time);double[] result=new double[a.value.length];
        if(result.length==4) {
            // Hytale's importer uses shortest-arc quaternion interpolation with weighted smooth timing.
            if(a.smooth && b.smooth) {
                double u=1-t,w0=2*u*u*u,w1=3*u*u*t,w2=6*u*t*t,w3=t*t*t;
                t=(.05*w1+.95*w2+w3)/(w0+w1+w2+w3);
            }
            double dot=0;for(int i=0;i<4;i++)dot+=a.value[i]*b.value[i];
            double sign=dot<0?-1:1; dot=Math.min(1,Math.abs(dot));
            double angle=Math.acos(dot),sin=Math.sin(angle);
            double left=sin<1e-6?1-t:Math.sin((1-t)*angle)/sin;
            double rightWeight=sin<1e-6?t:Math.sin(t*angle)/sin;
            for(int i=0;i<4;i++)result[i]=left*a.value[i]+rightWeight*sign*b.value[i];
            return result;
        }
        for(int i=0;i<result.length;i++) {
            if(a.smooth || b.smooth) {
                double p=keys.get(Math.max(0,right-2)).value[i],q=keys.get(Math.min(keys.size()-1,right+1)).value[i];
                result[i]=.5*((2*a.value[i])+(-p+b.value[i])*t+(2*p-5*a.value[i]+4*b.value[i]-q)*t*t+(-p+3*a.value[i]-3*b.value[i]+q)*t*t*t);
            } else result[i]=a.value[i]+(b.value[i]-a.value[i])*t;
        }
        return result;
    }

    /** Tight geometry bounds, avoiding repeated axis-aligned expansion through rotated groups. */
    public static javafx.geometry.Bounds bounds(Group model) throws IOException {
        double[] extent={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        accumulateBounds(model,new Affine(),extent);
        if(!Double.isFinite(extent[0]))throw new IOException("Local model contains no visible geometry");
        return new javafx.geometry.BoundingBox(extent[0],extent[1],extent[2],extent[3]-extent[0],extent[4]-extent[1],extent[5]-extent[2]);
    }
    private static void accumulateBounds(javafx.scene.Node node,javafx.scene.transform.Transform parent,double[] extent) {
        if(!node.isVisible())return;
        var transform=parent.createConcatenation(node.getLocalToParentTransform());
        if(node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            var points=mesh.getPoints();
            for(int i=0;i<points.size();i+=3) {
                var point=transform.transform(points.get(i),points.get(i+1),points.get(i+2));
                extent[0]=Math.min(extent[0],point.getX());extent[3]=Math.max(extent[3],point.getX());
                extent[1]=Math.min(extent[1],point.getY());extent[4]=Math.max(extent[4],point.getY());
                extent[2]=Math.min(extent[2],point.getZ());extent[5]=Math.max(extent[5],point.getZ());
            }
        }
        if(node instanceof javafx.scene.Parent group)for(var child:group.getChildrenUnmodifiable())accumulateBounds(child,transform,extent);
    }

    private static void checkCancelled() throws IOException {if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Local preview cancelled");}
    private record Part(String category,String selectedId,JsonNode definition,String texture,String gradient) {}
}
