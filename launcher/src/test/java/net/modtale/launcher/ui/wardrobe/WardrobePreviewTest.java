package net.modtale.launcher.ui.wardrobe;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WardrobePreviewTest {
    @BeforeAll static void toolkit() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX display unavailable: " + unavailable.getMessage());
        }
        fx(()->{net.modtale.launcher.ui.common.LauncherFonts.load();return null;});
    }

    @Test void buildsVerifiedHyvatarUrlsAndEncodesOverrides() {
        var request=new WardrobePreview.Request("NPC","skin &=?","Cape_Forest_Guardian.Green.Neck_Piece");
        assertEquals("https://hyvatar.io/render/glb/NPC?download=true&skin_id=skin%20%26%3D%3F&cape=Cape_Forest_Guardian.Green.Neck_Piece",request.glb().toString());
        assertTrue(request.png().toString().startsWith("https://hyvatar.io/render/full/NPC?size=1024&rotate=25&skin_id="));
        assertFalse(request.preview().toString().contains("download="));
        assertTrue(request.preview().toString().contains("&cape="));
        assertEquals("https://hyvatar.io/render/glb/NPC?download=true",new WardrobePreview.Request("NPC",null,"").glb().toString());
        assertThrows(IllegalArgumentException.class,()->new WardrobePreview.Request("../bad",null,null));
    }

    @Test void boundsDeclaredAndChunkedFetchBodiesAndRejectsHttpErrors() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ok",exchange->{ exchange.sendResponseHeaders(200,3); exchange.getResponseBody().write(new byte[]{1,2,3}); exchange.close(); });
        server.createContext("/large",exchange->{ exchange.sendResponseHeaders(200,64); exchange.getResponseBody().write(new byte[64]); exchange.close(); });
        server.createContext("/chunked",exchange->{ exchange.sendResponseHeaders(200,0); exchange.getResponseBody().write(new byte[64]); exchange.close(); });
        server.createContext("/error",exchange->{ exchange.sendResponseHeaders(503,-1); exchange.close(); });
        server.createContext("/redirect",exchange->{ exchange.getResponseHeaders().add("Location","/ok"); exchange.sendResponseHeaders(302,-1); exchange.close(); });
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            assertArrayEquals(new byte[]{1,2,3},WardrobePreview.fetch(URI.create(base+"/ok"),8));
            for(String path:List.of("/large","/chunked","/error","/redirect"))
                assertThrows(IOException.class,()->WardrobePreview.fetch(URI.create(base+path),8));
        } finally { server.stop(0); }
    }

    @Test void fallbackLoadsOffFxThreadWithDirectExecutorAndOpensMatching3d() throws Exception {
        byte[] bytes=png(0xffff0000);
        CountDownLatch opened=new CountDownLatch(1);
        AtomicReference<URI> uri=new AtomicReference<>();
        WardrobePreview preview=fx(()->new WardrobePreview(Runnable::run,false,(url,limit)->{
            assertFalse(Platform.isFxApplicationThread()); assertTrue(url.getPath().contains("/full/")); return bytes;
        },url->{uri.set(url);opened.countDown();}));
        try {
            fx(()->{preview.show("NPC","skin","cape");return null;});
            await(()->label(preview).startsWith("Static PNG"));
            fx(()->{
                assertNotNull(find(preview.view(),ImageView.class));
                button(preview,"Open 3D").fire();
                return null;
            });
            assertTrue(opened.await(3,TimeUnit.SECONDS));
            assertEquals("https://hyvatar.io/render/glb/NPC?bg=101b2d&skin_id=skin&cape=cape",uri.get().toString());
        } finally { fx(()->{preview.dispose();return null;}); }
    }

    @Test void failureCanRetryAndInvalidImageNeverClaimsLoaded() throws Exception {
        AtomicInteger attempts=new AtomicInteger(); byte[] bytes=png(0xff00ff00);
        WardrobePreview preview=fx(()->new WardrobePreview(Runnable::run,false,(uri,limit)-> attempts.incrementAndGet()==1 ? new byte[]{1,2,3}:bytes,uri->{}));
        try {
            fx(()->{preview.show("NPC",null,null);return null;});
            await(()->label(preview).contains("could not be loaded"));
            fx(()->{
                assertTrue(find(preview.view(),Label.class).isVisible());
                assertTrue(find(preview.view(),Label.class).isManaged());
                assertTrue(button(preview,"Retry").isVisible());button(preview,"Retry").fire();
                assertTrue(find(preview.view(),Label.class).isVisible());
                assertEquals("Loading preview…",label(preview));return null;
            });
            await(()->label(preview).startsWith("Static PNG"));
            assertEquals(2,attempts.get());
            fx(()->{
                assertFalse(find(preview.view(),Label.class).isVisible());
                assertFalse(find(preview.view(),Label.class).isManaged());
                assertTrue(find(preview.view(),ImageView.class).getParent().getAccessibleHelp().contains("Static preview"));
                return null;
            });
        } finally { fx(()->{preview.dispose();return null;}); }
    }

    @Test void queuedLoadsAreCancelledByClearAndDispose() throws Exception {
        List<Runnable> jobs=new ArrayList<>(); AtomicInteger fetches=new AtomicInteger();
        WardrobePreview preview=fx(()->new WardrobePreview(jobs::add,false,(uri,limit)->{fetches.incrementAndGet();return png(0xffff0000);},uri->{}));
        fx(()->{preview.show("NPC",null,null);preview.clear();preview.show("NPC","next",null);preview.dispose();preview.show("NPC","ignored",null);return null;});
        assertEquals(2,jobs.size());
        for(Runnable job:jobs)job.run();
        assertEquals(0,fetches.get());
        assertEquals("Select an outfit to preview",fx(()->label(preview)));
    }

    @Test void staleCompletionCannotReplaceNewerOutfit() throws Exception {
        CountDownLatch firstStarted=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), firstFinished=new CountDownLatch(1);
        byte[] red=png(0xffff0000), green=png(0xff00ff00);
        WardrobePreview preview=fx(()->new WardrobePreview(Runnable::run,false,(uri,limit)->{
            if(uri.getQuery().contains("skin_id=old")) {
                firstStarted.countDown();
                // Simulate a transport that completes despite cancellation.
                boolean released=false;
                while(!released)try {released=releaseFirst.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
                firstFinished.countDown(); return red;
            }
            return green;
        },uri->{}));
        try {
            fx(()->{preview.show("NPC","old",null);return null;});
            assertTrue(firstStarted.await(3,TimeUnit.SECONDS));
            fx(()->{preview.show("NPC","new",null);return null;});
            await(()->label(preview).startsWith("Static PNG"));
            releaseFirst.countDown(); assertTrue(firstFinished.await(3,TimeUnit.SECONDS));
            // Drain queued FX completions, then inspect the current texture.
            fx(()->null);
            assertEquals(0xff00ff00,fx(()->find(preview.view(),ImageView.class).getImage().getPixelReader().getArgb(0,0)));
        } finally { releaseFirst.countDown();fx(()->{preview.dispose();return null;}); }
    }

    @Test void staleCapeRenderCannotReplaceNewSelection() throws Exception {
        CountDownLatch firstStarted=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), firstFinished=new CountDownLatch(1);
        byte[] red=png(0xffff0000), green=png(0xff00ff00);
        WardrobePreview preview=fx(()->new WardrobePreview(Runnable::run,true,(uri,limit)->{
            if(uri.getQuery().contains("cape=old")) {
                firstStarted.countDown();
                // Simulate a transport that completes despite cancellation.
                boolean released=false;
                while(!released)try {released=releaseFirst.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
                firstFinished.countDown(); return red;
            }
            return green;
        },uri->{}));
        try {
            fx(()->{preview.show("NPC",null,"old");return null;});
            assertTrue(firstStarted.await(3,TimeUnit.SECONDS));
            fx(()->{preview.show("NPC",null,"new");return null;});
            await(()->label(preview).startsWith("Rendered cape preview"));
            releaseFirst.countDown(); assertTrue(firstFinished.await(3,TimeUnit.SECONDS));
            // Drain queued FX completions, then inspect the current texture.
            fx(()->null);
            assertEquals(0xff00ff00,fx(()->find(preview.view(),ImageView.class).getImage().getPixelReader().getArgb(0,0)));
        } finally { releaseFirst.countDown();fx(()->{preview.dispose();return null;}); }
    }

    @Test void rejectedExecutorProducesRetryState() throws Exception {
        WardrobePreview preview=fx(()->new WardrobePreview(job->{throw new java.util.concurrent.RejectedExecutionException();},false,(uri,limit)->new byte[0],uri->{}));
        try {fx(()->{preview.show("NPC",null,null);assertTrue(label(preview).contains("could not be loaded"));assertTrue(button(preview,"Retry").isVisible());return null;});}
        finally {fx(()->{preview.dispose();return null;});}
    }

    @Test void standaloneCapeUsesRenderedRouteWithReleaseOnlyRotationAndLocalZoom() throws Exception {
        var fetched=new java.util.concurrent.LinkedBlockingQueue<URI>();
        var opened=new java.util.concurrent.LinkedBlockingQueue<URI>();
        byte[] bytes=png(0xff00ff00);
        WardrobePreview preview=fx(()->new WardrobePreview(Runnable::run,true,(uri,limit)->{
            assertFalse(Platform.isFxApplicationThread());fetched.add(uri);return bytes;
        },opened::add));
        try {
            fx(()->{preview.show("NPC","","Cape_Test.Green");return null;});
            await(()->label(preview).startsWith("Rendered cape preview"));
            assertEquals("https://hyvatar.io/render/cape/NPC?size=1024&rotate=180&cape=Cape_Test.Green",fetched.remove().toString());
            fx(()->{button(preview,"Rotate left").fire();return null;});
            await(()->label(preview).startsWith("Rendered cape preview"));
            assertTrue(fetched.remove().getQuery().contains("rotate=150"));
            fx(()->{
                ImageView image=find(preview.view(),ImageView.class);
                StackPane viewport=(StackPane)image.getParent();
                assertFalse(find(preview.view(),Label.class).isManaged());
                assertTrue(viewport.getAccessibleHelp().contains("Drag and release"));
                GroupAccess.rotateByDrag(viewport);
                assertTrue(fetched.isEmpty(),"Dragging must not queue network requests");
                viewport.getOnMouseReleased().handle(GroupAccess.mouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED,50,20));
                return null;
            });
            await(()->label(preview).startsWith("Rendered cape preview"));
            assertTrue(fetched.remove().getQuery().contains("rotate=180"));
            fx(()->{
                StackPane host=new StackPane(preview.view());new Scene(host,260,335);
                assertInspectorFits(host,preview);
                ImageView image=find(preview.view(),ImageView.class);
                StackPane viewport=(StackPane)image.getParent();
                viewport.getOnScroll().handle(new javafx.scene.input.ScrollEvent(javafx.scene.input.ScrollEvent.SCROLL,
                        100,100,100,100,false,false,false,false,false,false,0,120,0,120,
                        javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE,0,
                        javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));
                assertNotEquals(1,image.getScaleX());assertNotNull(viewport.getClip());assertTrue(fetched.isEmpty());
                button(preview,"View render").fire();return null;
            });
            assertTrue(opened.poll(3,TimeUnit.SECONDS).getPath().contains("/render/cape/"));
            fx(()->{button(preview,"Reset view").fire();return null;});
            await(()->label(preview).startsWith("Rendered cape preview"));
            assertEquals(1,fx(()->find(preview.view(),ImageView.class).getScaleX()));
            assertTrue(fetched.remove().getQuery().contains("rotate=180"));
        } finally {fx(()->{preview.dispose();return null;});}
    }

    @Test void actualSampleSupportsNativeControlsAndOptionalFallbackSnapshot() throws Exception {
        String glb=System.getenv("WARDROBE_SAMPLE_GLB"), pngPath=System.getenv("WARDROBE_SAMPLE_PNG");
        org.junit.jupiter.api.Assumptions.assumeTrue(glb!=null && pngPath!=null,
                "Set WARDROBE_SAMPLE_GLB and WARDROBE_SAMPLE_PNG for the real-sample render harness");
        byte[] model=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(glb));
        byte[] fallback=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(pngPath));
        WardrobePreview nativePreview=fx(()->new WardrobePreview(Runnable::run,true,(uri,limit)->model,uri->{}));
        try {
            fx(()->{nativePreview.show("NPC",null,null);return null;});
            await(()->label(nativePreview).startsWith("Live 3D"));
            fx(()->{
                StackPane host=new StackPane(nativePreview.view());
                Scene hostScene=new Scene(host,260,335);
                hostScene.getStylesheets().add(WardrobePreviewTest.class.getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                host.applyCss();host.layout();
                javafx.scene.SubScene scene=find(nativePreview.view(),javafx.scene.SubScene.class);
                assertNotNull(scene);
                assertTrue(scene.isDepthBuffer());
                assertEquals(javafx.scene.paint.Color.TRANSPARENT,scene.getFill());
                assertInspectorFits(host,nativePreview);
                double initial=scene.getCamera().getTranslateZ();
                StackPane viewport=(StackPane)scene.getParent();
                viewport.getOnScroll().handle(new javafx.scene.input.ScrollEvent(javafx.scene.input.ScrollEvent.SCROLL,
                        100,100,100,100,false,false,false,false,false,false,0,120,0,120,
                        javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE,0,
                        javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));
                assertTrue(scene.getCamera().getTranslateZ()>initial);
                GroupAccess.rotateByDrag(viewport);
                var rotated=(javafx.scene.Group)scene.getRoot().getChildrenUnmodifiable().getFirst();
                var yaw=(javafx.scene.transform.Rotate)rotated.getTransforms().getFirst();
                assertNotEquals(-20,yaw.getAngle());
                button(nativePreview,"Reset view").fire();
                assertEquals(-20,yaw.getAngle());
                assertEquals(initial,scene.getCamera().getTranslateZ(),1e-8);
                nativePreview.clear();
                assertTrue(scene.getRoot().getChildrenUnmodifiable().isEmpty());
                return null;
            });
        } finally {fx(()->{nativePreview.dispose();return null;});}
        WardrobePreview staticPreview=fx(()->new WardrobePreview(Runnable::run,false,(uri,limit)->fallback,uri->{}));
        try {
            fx(()->{staticPreview.show("NPC",null,null);return null;});
            await(()->label(staticPreview).startsWith("Static PNG"));
            fx(()->{
                StackPane host=new StackPane(staticPreview.view());
                Scene hostScene=new Scene(host,260,335);
                hostScene.getStylesheets().add(WardrobePreviewTest.class.getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                host.applyCss();host.layout();
                assertInspectorFits(host,staticPreview);
                var image=host.snapshot(null,null);
                String target=System.getenv("WARDROBE_PREVIEW_SNAPSHOT");
                if(target!=null) {
                    BufferedImage output=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    ImageIO.write(output,"png",java.nio.file.Path.of(target).toFile());
                }
                assertTrue(find(staticPreview.view(),ImageView.class).getBoundsInParent().getHeight()>100);
                return null;
            });
        } finally {fx(()->{staticPreview.dispose();return null;});}
    }

    @Test void localCompositionRendersInstalledAssetsAndCannotOpenRemotePreview() throws Exception {
        String assets=System.getenv("WARDROBE_ASSETS_ZIP");
        org.junit.jupiter.api.Assumptions.assumeTrue(assets!=null,"Set WARDROBE_ASSETS_ZIP for local composition rendering");
        var path=java.nio.file.Path.of(assets);
        var catalog=new net.modtale.launcher.wardrobe.CosmeticCatalogClient(path);
        var skin=catalog.defaultSkin();
        skin.put("bodyCharacteristic","Default.01");
        skin.put("haircut","Morning.Blond");
        skin.put("cape","Cape_Forest_Guardian.Green.Neck_Piece");
        skin.put("undertop","SurvivorShirtBoy.White");
        skin.put("overtop","PuffyJacket.Blue");
        skin.put("pants","ApprenticePants.Brown");
        skin.put("shoes","BasicBoots.Black");
        List<Runnable> localJobs=new ArrayList<>();
        WardrobePreview preview=fx(()->new WardrobePreview(localJobs::add,true,(uri,limit)->{
            throw new AssertionError("Local composition must never fetch a remote render");
        },uri->{throw new AssertionError("Local composition has no remote preview URL");}));
        try {
            fx(()->{preview.showLocal(path,skin);skin.remove("bodyCharacteristic");return null;});
            assertEquals(1,localJobs.size());
            localJobs.removeFirst().run(); // Preview owns a deep copy even if the editor mutates its draft.
            await(()->label(preview).startsWith("Local outfit preview ·"));
            fx(()->{
                assertFalse(button(preview,"Open 3D").isVisible());
                assertFalse(find(preview.view(),Label.class).isManaged());
                StackPane host=new StackPane(preview.view());
                Scene hostScene=new Scene(host,420,540);
                hostScene.getStylesheets().add(WardrobePreviewTest.class.getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                host.applyCss();host.layout();
                String target=System.getenv("WARDROBE_LOCAL_SNAPSHOT");
                if(target!=null) {
                    writeSnapshot(host,java.nio.file.Path.of(target));
                    var scene=find(preview.view(),javafx.scene.SubScene.class);
                    var rotated=(javafx.scene.Group)scene.getRoot().getChildrenUnmodifiable().getFirst();
                    ((javafx.scene.transform.Rotate)rotated.getTransforms().getFirst()).setAngle(160);
                    writeSnapshot(host,java.nio.file.Path.of(target.replace(".png","-back.png")));
                }
                assertNotNull(find(preview.view(),javafx.scene.SubScene.class));
                preview.clear();assertNull(find(preview.view(),javafx.scene.SubScene.class));
                return null;
            });
        } finally {fx(()->{preview.dispose();return null;});}
    }

    @Test void localAnimationDefaultsToIdleRestoresAndCancelsPendingSelection() throws Exception {
        String assets=System.getenv("WARDROBE_ASSETS_ZIP");
        org.junit.jupiter.api.Assumptions.assumeTrue(assets!=null,"Set WARDROBE_ASSETS_ZIP for animation UI validation");
        var path=java.nio.file.Path.of(assets);
        var skin=new net.modtale.launcher.wardrobe.CosmeticCatalogClient(path).defaultSkin();
        List<Runnable> jobs=new ArrayList<>();
        WardrobePreview preview=fx(()->new WardrobePreview(jobs::add,true,(uri,limit)->{throw new AssertionError("Local animation must stay offline");},uri->{}));
        try {
            fx(()->{preview.showLocal(path,skin);return null;});jobs.removeFirst().run();
            await(()->label(preview).startsWith("Local outfit preview"));
            StackPane host=fx(()->{
                StackPane pane=new StackPane(preview.view());Scene hostScene=new Scene(pane,310,335);
                hostScene.getStylesheets().add(WardrobePreviewTest.class.getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                pane.applyCss();pane.layout();assertInspectorFits(pane,preview);return pane;
            });
            var choices=fx(()->find(preview.view(),javafx.scene.control.ComboBox.class));
            assertEquals("Idle",fx(()->choices.getValue().toString()));
            await(()->!jobs.isEmpty()); jobs.removeFirst().run();
            await(()->label(preview).startsWith("Local outfit preview"));
            assertTrue(fx(()->buttons(preview.view()).stream().noneMatch(button -> "Pause".equals(button.getText()))));
            fx(()->{choices.getSelectionModel().selectFirst();return null;});
            var arm=fx(()->nodeById(find(preview.view(),javafx.scene.SubScene.class).getRoot(),"bodyCharacteristic:L-Arm"));
            assertNotNull(arm);
            var original=fx(()->transform(arm));
            fx(()->{selectClip(choices,"Walk");return null;});jobs.removeFirst().run();
            await(()->!java.util.Arrays.equals(transform(arm),original));
            fx(()->{animationSnapshot(host,"walk");return null;});
            fx(()->{choices.getSelectionModel().selectFirst();return null;});
            assertArrayEquals(original,fx(()->transform(arm)));
            assertFalse(fx(()->find(preview.view(),Label.class).isManaged()));
            fx(()->{selectClip(choices,"Angry · Face");return null;});jobs.removeFirst().run();
            fx(()->{animationSnapshot(host,"angry");choices.getSelectionModel().selectFirst();return null;});
            // A queued facial clip must not install after Rest pose, clear, or disposal.
            fx(()->{selectClip(choices,"Angry · Face");choices.getSelectionModel().selectFirst();return null;});
            jobs.removeFirst().run();fx(()->null);
            // Finish parsing while FX is occupied, then invalidate the queued completion before it can attach.
            fx(()->{
                selectClip(choices,"Angry · Face");
                Thread worker=Thread.startVirtualThread(jobs.removeFirst());worker.join(3000);
                assertFalse(worker.isAlive());choices.getSelectionModel().selectFirst();return null;
            });
            fx(()->{selectClip(choices,"Angry · Face");preview.clear();return null;});
            jobs.removeFirst().run();fx(()->null);
            assertNull(fx(()->find(preview.view(),javafx.scene.SubScene.class)));
            assertArrayEquals(original,fx(()->transform(arm)));
            fx(()->{preview.showLocal(path,skin);return null;});jobs.removeFirst().run();
            await(()->label(preview).startsWith("Local outfit preview"));
            fx(()->{selectClip(choices,"Walk");preview.dispose();return null;});
            jobs.removeFirst().run();fx(()->null);
            assertNull(fx(()->find(preview.view(),javafx.scene.SubScene.class)));
        } finally {fx(()->{preview.dispose();return null;});}
    }
    private static void animationSnapshot(StackPane host,String name) throws IOException {
        String directory=System.getenv("WARDROBE_ANIMATION_SNAPSHOT");
        if(directory!=null) {
            host.resize(310,335);host.applyCss();host.layout();
            writeSnapshot(host,java.nio.file.Path.of(directory,"wardrobe-animation-"+name+".png"));
        }
    }
    private static double[] transform(Node node) { return node.getLocalToParentTransform().toArray(javafx.scene.transform.MatrixType.MT_3D_3x4); }
    private static void selectClip(javafx.scene.control.ComboBox<?> choices,String label) {
        for(int i=0;i<choices.getItems().size();i++)if(choices.getItems().get(i).toString().equals(label)) {
            choices.getSelectionModel().select(i);return;
        }
        fail("Missing animation "+label);
    }
    private static Node nodeById(Node node,String id) {
        if(id.equals(node.getId()))return node;
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable()) {
            Node found=nodeById(child,id);if(found!=null)return found;
        }
        return null;
    }

    private static void writeSnapshot(StackPane host,java.nio.file.Path path) throws IOException {
        var image=host.snapshot(null,null);
        BufferedImage output=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(output,"png",path.toFile());
    }

    private static void assertInspectorFits(StackPane host, WardrobePreview preview) {
        for (int width : new int[]{310,285,260}) {
            host.resize(width,335);host.applyCss();host.layout();
            for (Button button:buttons(preview.view())) {
                if (!button.isVisible()) continue;
                var bounds=button.localToScene(button.getBoundsInLocal());
                assertTrue(bounds.getMinX()>=0 && bounds.getMaxX()<=width,"Footer must fit "+width+"px inspector");
                assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=335,"Footer must fit inspector height");
                assertTrue(button.getStyleClass().containsAll(List.of("btn","secondary","small")));
            }
        }
    }

    private static final class GroupAccess {
        static void rotateByDrag(StackPane viewport) {
            viewport.getOnMousePressed().handle(mouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED,0,0));
            viewport.getOnMouseDragged().handle(mouse(javafx.scene.input.MouseEvent.MOUSE_DRAGGED,50,20));
        }
        static javafx.scene.input.MouseEvent mouse(javafx.event.EventType<javafx.scene.input.MouseEvent> type,double x,double y) {
            return new javafx.scene.input.MouseEvent(type,x,y,x,y,javafx.scene.input.MouseButton.PRIMARY,1,
                    false,false,false,false,true,false,false,false,false,false,null);
        }
    }

    private static byte[] png(int argb) throws IOException {
        BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<2;y++)for(int x=0;x<2;x++)image.setRGB(x,y,argb);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
    private static String label(WardrobePreview preview) {return find(preview.view(),Label.class).getText();}
    private static Button button(WardrobePreview preview,String text) {return buttons(preview.view()).stream().filter(b->(text.equals(b.getText()) || text.equals(b.getAccessibleText()))).findFirst().orElseThrow();}
    private static List<Button> buttons(Node node) {
        List<Button> result=new ArrayList<>();if(node instanceof Button b)result.add(b);
        if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())result.addAll(buttons(child));return result;
    }
    private static <T> T find(Node node,Class<T> type) {
        if(type.isInstance(node))return type.cast(node);
        if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable()){T result=find(child,type);if(result!=null)return result;}return null;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){if(fx(condition))return;Thread.sleep(10);}fail("Preview did not reach expected state");
    }
    private static <T> T fx(Callable<T> action) throws Exception {FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(5,TimeUnit.SECONDS);}
}
