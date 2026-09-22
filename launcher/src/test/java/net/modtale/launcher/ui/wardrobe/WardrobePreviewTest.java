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

    @Test void clearAndDisposeCancelQueuedLocalWork() throws Exception {
        List<Runnable> jobs = new ArrayList<>();
        var preview = fx(() -> new WardrobePreview(jobs::add, false));
        var skin = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("bodyCharacteristic", "Default");
        fx(() -> { preview.showLocal(java.nio.file.Path.of("missing.zip"), skin); preview.clear(); return null; });
        jobs.removeFirst().run();
        fx(() -> { assertEquals("Select an outfit to preview", label(preview)); preview.dispose(); return null; });
        fx(() -> { preview.showLocal(java.nio.file.Path.of("missing.zip"), skin); return null; });
        assertTrue(jobs.isEmpty());
    }

    @Test void localLoadFailureCanBeRetriedWithoutOpeningABrowser() throws Exception {
        List<Runnable> jobs = new ArrayList<>();
        var preview = fx(() -> new WardrobePreview(jobs::add, false));
        try {
            var skin = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("bodyCharacteristic", "Default");
            fx(() -> { preview.showLocal(java.nio.file.Path.of("missing.zip"), skin); return null; });
            jobs.removeFirst().run(); await(() -> label(preview).startsWith("Preview unavailable:"));
            fx(() -> { assertTrue(button(preview, "Retry").isVisible()); button(preview, "Retry").fire(); return null; });
            assertEquals(1, jobs.size());
        } finally { fx(() -> { preview.dispose(); return null; }); }
    }

    @Test void softwarePreviewRendersInstalledAssetsWithoutNative3d() throws Exception {
        String assets = System.getenv("WARDROBE_ASSETS_ZIP");
        org.junit.jupiter.api.Assumptions.assumeTrue(assets != null);
        var path = java.nio.file.Path.of(assets);
        var skin = new net.modtale.launcher.wardrobe.CosmeticCatalogClient(path).defaultSkin();
        var preview = fx(() -> new WardrobePreview(Runnable::run, false));
        try {
            fx(() -> { preview.showLocal(path, skin); return null; });
            await(() -> label(preview).contains("Static image"));
            fx(() -> {
                var rendered = find(preview.view(), ImageView.class).getImage();
                int visible = 0;
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) if ((rendered.getPixelReader().getArgb(x, y) >>> 24) > 0) visible++;
                assertTrue(visible > 2000); assertNull(find(preview.view(), javafx.scene.SubScene.class));
                return null;
            });
        } finally { fx(() -> { preview.dispose(); return null; }); }
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
        WardrobePreview preview=fx(()->new WardrobePreview(localJobs::add,true));
        try {
            fx(()->{preview.showLocal(path,skin);skin.remove("bodyCharacteristic");return null;});
            assertEquals(1,localJobs.size());
            localJobs.removeFirst().run(); // Preview owns a deep copy even if the editor mutates its draft.
            await(()->label(preview).startsWith("Local outfit preview ·"));
            fx(()->{
                assertTrue(buttons(preview.view()).stream().noneMatch(b -> "Open 3D".equals(b.getText())));
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
        WardrobePreview preview=fx(()->new WardrobePreview(jobs::add,true));
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
