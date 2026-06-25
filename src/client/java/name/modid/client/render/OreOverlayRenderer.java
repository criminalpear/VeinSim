package name.modid.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import name.modid.VeinSim;
import name.modid.client.VeinSimClient;
import name.modid.client.ore.OrePredictor;
import name.modid.client.ore.OrePredictor.PredictedOre;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.List;

public class OreOverlayRenderer {

    // Custom pipeline that always passes the depth test — lets boxes show through blocks.
    // Falls back to the standard LINES type if pipeline creation fails.
    private static RenderType espLines;

    private static RenderType getEspLines() {
        if (espLines == null) {
            espLines = buildEspLinesType();
        }
        return espLines;
    }

    private static RenderType buildEspLinesType() {
        try {
            // Extend the LINES_SNIPPET but override depth test to ALWAYS_PASS
            // so the wireframe boxes are visible through terrain.
            RenderPipeline pipeline = RenderPipeline
                    .builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("vein-sim", "esp_lines"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build();
            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            return RenderType.create("vein_sim_esp_lines", setup);
        } catch (Exception e) {
            VeinSim.LOGGER.warn("[VeinSim] Custom ESP pipeline failed, falling back to LINES: {}", e.getMessage());
            return RenderTypes.LINES;
        }
    }

    public static void render(LevelRenderContext ctx) {
        if (!VeinSimClient.overlayActive) return;

        List<PredictedOre> predictions = OrePredictor.getPredictions();
        if (predictions.isEmpty()) return;

        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        PoseStack poseStack = ctx.poseStack();
        RenderType lineType = getEspLines();

        for (PredictedOre ore : predictions) {
            poseStack.pushPose();
            poseStack.translate(
                ore.x() - cam.x,
                ore.y() - cam.y,
                ore.z() - cam.z
            );

            int argb = 0xFF000000 | ore.type().color;

            ctx.submitNodeCollector().submitShapeOutline(
                poseStack,
                Shapes.block(),
                lineType,
                argb,
                2.0f,
                true
            );

            poseStack.popPose();
        }
    }
}
