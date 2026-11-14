package com.dreadmod.client;

import com.dreadmod.Dread;
import com.dreadmod.entity.StalkerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Высокая, тощая фигура на базе гуманоидной модели с кастомной текстурой и светящимися глазами. */
@Environment(EnvType.CLIENT)
public class StalkerRenderer extends MobEntityRenderer<StalkerEntity, BipedEntityModel<StalkerEntity>> {
    private static final Identifier TEXTURE = Dread.id("textures/entity/stalker.png");

    public StalkerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.4f);
        this.addFeature(new StalkerEyesFeature(this));
    }

    @Override
    public Identifier getTexture(StalkerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(StalkerEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(0.85f, 1.25f, 0.85f);
    }
}
