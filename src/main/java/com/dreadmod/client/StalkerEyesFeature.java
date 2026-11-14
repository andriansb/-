package com.dreadmod.client;

import com.dreadmod.Dread;
import com.dreadmod.entity.StalkerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;

/** Глаза Сталкера светятся в полной темноте. */
@Environment(EnvType.CLIENT)
public class StalkerEyesFeature extends EyesFeatureRenderer<StalkerEntity, BipedEntityModel<StalkerEntity>> {
    private static final RenderLayer EYES =
            RenderLayer.getEyes(Dread.id("textures/entity/stalker_eyes.png"));

    public StalkerEyesFeature(FeatureRendererContext<StalkerEntity, BipedEntityModel<StalkerEntity>> context) {
        super(context);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return EYES;
    }
}
