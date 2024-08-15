package net.orange.horsedebugext;

import com.google.common.collect.Lists;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class HorseDebugMain {
    public static final String UTF8_STAR = "\u2B50";
    public static final String UTF8_HEART = "\u2764";
    private static final Logger log = LogManager.getLogger("HorseDebug");
    private static HorseDebugMain instance;

    public static void log(String message) {
        log.info("[" + log.getName() + "] " + message);
    }

    private static double getBaseValue(LivingEntity entity, EntityAttribute attribute) {
        RegistryEntry<EntityAttribute> registryEntry = RegistryEntry.of(attribute);
        EntityAttributeInstance inst = entity.getAttributeInstance(registryEntry);
        if (inst == null) {
            return attribute.getDefaultValue();
        }
        return inst.getBaseValue();
    }

    /**
     * get this mod
     */
    public static HorseDebugMain getMod() {
        return instance;
    }

    /**
     * true if an API is already register for it
     */
    public static boolean isAPIRegister() {
        return instance != null;
    }

    /**
     * register an API for HorseDebug
     *
     * @throws IllegalStateException if an API is already register for it
     */
    public static HorseDebugMain registerAPI(BuildAPI api) throws IllegalStateException {
        instance = new HorseDebugMain();
        log("Starting HorseDebug with " + api.getAPIName());
        return instance;
    }

    public static String getHorseColorNameDescription(HorseColor color) {
        return switch (color.getId()) {
            case 0 -> "white";
            case 1 -> "creamy";
            case 2 -> "chestnut";
            case 3 -> "brown";
            case 4 -> "black";
            case 5 -> "gray";
            case 6 -> "darkbrown";
            default -> "unknown";
        };
    }

    public static String getHorseColorNameDescription(HorseMarking color) {
        return switch (color.getId()) {
            case 0 -> "none";
            case 1 -> "white";
            case 2 -> "white_field";
            case 3 -> "white_dots";
            case 4 -> "black_dots";
            default -> "unknown";
        };
    }

    public static String getHorseColorName(HorseColor color, HorseMarking marking) {
        return I18n.translate("gui.act.invView.horse.variant." + getHorseColorNameDescription(color)) + " / "
                + I18n.translate("gui.act.invView.horse.variant.marking." + getHorseColorNameDescription(marking));
    }

    public static String getCatColorName(CatVariant color) {
        Identifier id = Registries.CAT_VARIANT.getId(color);
        if (id == null) {
            return I18n.translate("gui.act.invView.cat.variant.unknown");
        }
        return I18n.translate("gui.act.invView.cat.variant." + id.getPath());
    }

    private boolean show3DOverlay;
    private KeyBinding configKey;

    private HorseDebugMain() {
        if (isAPIRegister())
            throw new IllegalStateException("An API is already register for this mod!");
    }

    public static final StatValue STAT_HEALTH = StatValue.builder().base(15.0).add(8).add(9).showScale(0.5)
            .build();
    public static final StatValue STAT_JUMP = StatValue.builder().base(0.4000000059604645D).add(0.2).add(0.2).add(
                    0.2).showFunc(d -> -0.2 * Math.pow(d, 3) + 3.7 * Math.pow(d, 2) + 2.1 * d - 0.4)
            .build();
    public static final StatValue STAT_SPEED = StatValue.builder().base(0.44999998807907104D).add(0.3).add(0.3).add(0.3)
            .scale(0.25).showScale(43).build();

    /**
     * @deprecated use {@link #STAT_HEALTH} instead
     */
    @Deprecated
    public static final double BAD_HP = STAT_HEALTH.getBadValue();
    /**
     * @deprecated use {@link #STAT_JUMP} instead
     */
    @Deprecated
    public static final double BAD_JUMP = STAT_JUMP.getBadValue();
    /**
     * @deprecated use {@link #STAT_HEALTH} instead
     */
    @Deprecated
    public static final double BAD_SPEED = STAT_SPEED.getBadValue();
    /**
     * @deprecated use {@link #STAT_HEALTH} instead
     */
    @Deprecated
    public static final double EXELLENT_HP = STAT_HEALTH.getExcellentValue();
    /**
     * @deprecated use {@link #STAT_JUMP} instead
     */
    @Deprecated
    public static final double EXELLENT_JUMP = STAT_JUMP.getExcellentValue();
    /**
     * @deprecated use {@link #STAT_HEALTH} instead
     */
    @Deprecated
    public static final double EXELLENT_SPEED = STAT_HEALTH.getExcellentValue();

    public void drawInventory(DrawContext dContext, MinecraftClient mc, int posX, int posY, String[] addText,
                              LivingEntity entity) {

        int l = addText.length;
        if (l == 0)
            return;
        int sizeX = 0;
        int sizeY = 0;
        for (String s : addText) {
            sizeY += mc.textRenderer.fontHeight + 1;
            int a = mc.textRenderer.getWidth(s) + 10;
            if (a > sizeX)
                sizeX = a;
        }
        if (entity != null) {
            sizeX += 100;
            if (sizeY < 100)
                sizeY = 100;
        }
        Window mw = mc.getWindow();
        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEffectVertexConsumers());
        posX += 5;
        posY += 5;
        if (posX + sizeX > mw.getScaledWidth())
            posX -= sizeX + 10;
        if (posY + sizeY > mw.getScaledHeight())
            posY -= sizeY + 10;
        int posY1 = posY + 5;
        for (String s : addText) {
            drawContext.drawTextWithShadow(mc.textRenderer, s, posX + 5, posY1, 0xffffffff);
            posY1 += (mc.textRenderer.fontHeight + 1);
        }
        if (entity != null) {
            int entitySize = (int) (120 / getCornerLength(entity.getBoundingBox()));
            InventoryScreen.drawEntity(dContext, posX + sizeX - 55, posY + sizeY + 105, posX + sizeX - 55 + sizeX, posY + sizeY + 105 + sizeY, entitySize, 0, 50, 50, entity);
        }
    }

    private static double getJump(LivingEntity e) {
        return getBaseValue(e, EntityAttributes.GENERIC_JUMP_STRENGTH.value());
    }

    public String[] getEntityData(LivingEntity entity) {
        List<String> text = Lists.newArrayList();
        text.add("\u00a7b" + entity.getDisplayName().getString());
        text.add("\u00a77" + EntityType.getId(entity.getType()).toString());

        if (entity instanceof CatEntity cat) {
            var color = cat.getVariant();
            text.add(I18n.translate("gui.act.invView.horse.variant") + ": " + getCatColorName(color.value()) + " (" + Registries.CAT_VARIANT.getId(color.value())
                    + ")");
        } else if (entity instanceof SheepEntity sheep) {
            var color = sheep.getColor();
            text.add(I18n.translate("gui.act.invView.horse.variant") + ": " + color.getName() + " (" + color.getId() + ")");
        } else if (entity instanceof AbstractHorseEntity baby) {
            if (baby instanceof HorseEntity horse) {
                var color = horse.getVariant();
                var markings = horse.getMarking();
                var id = color.getId() + markings.getId() << 8;
                text.add(I18n.translate("gui.act.invView.horse.variant") + ": " + getHorseColorName(color, markings) + " ("
                        + id + ")");
            }

            text.add(I18n.translate("gui.act.invView.horse.jump") + ": "
                    + STAT_JUMP.getFormattedText(getJump(baby)));
            text.add(I18n.translate("gui.act.invView.horse.speed") + ": "
                    + STAT_SPEED.getFormattedText(getBaseValue(baby, EntityAttributes.GENERIC_MOVEMENT_SPEED.value()))
                    + " m/s " + "(" + significantNumbers(getBaseValue(baby, EntityAttributes.GENERIC_MOVEMENT_SPEED.value()))
                    + " iu)");
            text.add(I18n.translate("gui.act.invView.horse.health") + ": "
                    + STAT_HEALTH.getFormattedText((baby.getMaxHealth())) + " HP");
        }
        return text.toArray(String[]::new);
    }

    public static String significantNumbers(double d) {
        boolean negative = d < 0;
        if (negative) {
            d *= -1;
        }
        int d1 = (int) (d);
        d %= 1;
        String s = String.format("%.3G", d);
        if (s.length() > 0)
            s = s.substring(1);
        if (s.contains("E+"))
            s = String.format(Locale.US, "%.0f", Double.valueOf(String.format("%.3G", d)));
        return (negative ? "-" : "") + d1 + s;
    }

    public void renderOverlay(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.getDebugHud().shouldShowDebugHud())
            return;
        Window mw = mc.getWindow();
        if (mc.player.getVehicle() instanceof AbstractHorseEntity baby) {
            drawInventory(drawContext, mc, mw.getScaledWidth(), mw.getScaledHeight(), getEntityData(baby), baby);
        } else {
            HitResult obj = mc.crosshairTarget;
            if (obj instanceof EntityHitResult eo) {
                if (eo.getEntity() instanceof LivingEntity)
                    drawInventory(drawContext, mc, mw.getScaledWidth(), mw.getScaledHeight(),
                            getEntityData((LivingEntity) eo.getEntity()), (LivingEntity) eo.getEntity());
            }
        }
    }

    /**
     * sum the 3 normalized values of jump/health/speed
     *
     * @param jump   the jump power
     * @param health the health
     * @param speed  the speed
     * @return a score
     */
    public double score(double jump, double health, double speed) {
        return STAT_JUMP.normalized(jump) + STAT_HEALTH.normalized(health) + STAT_SPEED.normalized(speed);
    }

    public void renderWorld(Iterable<Entity> entities, MatrixStack matrices,
                            Camera camera, VertexConsumerProvider source) {
        if (!show3DOverlay) {
            return;
        }
        List<AbstractHorseEntity> horses = new ArrayList<>();
        double bestScore = 0;
        double bestJump = 0;
        double bestSpeed = 0;
        double bestHealth = 0;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        for (Entity entity : entities)
            if (entity instanceof AbstractHorseEntity h) {

                double distance = player.getPos().squaredDistanceTo(h.getPos());

                if (distance > 4096.0D) {
                    continue;
                }

                horses.add(h);

                double jump = getJump(h);
                double health = h.getMaxHealth();
                double speed = getBaseValue(h, EntityAttributes.GENERIC_MOVEMENT_SPEED.value());
                double score = score(jump, health, speed);


                if (jump > bestJump) {
                    bestJump = jump;
                }
                if (health > bestHealth) {
                    bestHealth = health;
                }
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                }
                if (score > bestScore) {
                    bestScore = score;
                }
            }
        TextRenderer textRenderer = mc.textRenderer;
        float opacity = mc.options.getTextBackgroundOpacity(0.25F);

        Text[] texts = new Text[4];

        for (AbstractHorseEntity h : horses) {
            double jump = getJump(h);
            double health = h.getMaxHealth();
            double speed = getBaseValue(h, EntityAttributes.GENERIC_MOVEMENT_SPEED.value());
            double score = score(jump, health, speed);

            texts[0] = Text.literal(STAT_JUMP.getFormattedText(jump, " b", jump >= bestJump));
            texts[1] = Text.literal(
                    STAT_HEALTH.getFormattedText(health, " " + Formatting.RED + UTF8_HEART, health >= bestHealth));
            texts[2] = Text.literal(STAT_SPEED.getFormattedText(speed, " m/s", speed >= bestSpeed));

            if (score >= bestScore) {
                texts[3] = Text.literal(Formatting.YELLOW + "" + UTF8_STAR);
            } else {
                texts[3] = null;
            }

            float textHeight = h.getHeight() + 0.5F;

            Entity e = h;

            while (!e.getPassengerList().isEmpty()) {
                e = e.getPassengerList().get(0);
            }

            double textY = e.getY();

            matrices.push();
            matrices.translate(h.getX() - camera.getPos().x, textY - camera.getPos().y + textHeight,
                    h.getZ() - camera.getPos().z);
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            ;
            int background = (int) (opacity * 255.0F) << 24;
            int y = (h.hasCustomName() ? -(textRenderer.fontHeight + 4) : 0);
            for (Text text : texts) {
                if (text == null) {
                    continue;
                }

                float x = (float) (-textRenderer.getWidth(text) / 2);
                textRenderer.draw(text,
                        x, (float) y, 0x22FFFFFF, false, matrix4f, source, TextRenderer.TextLayerType.NORMAL, background, 15728880);
                textRenderer.draw(text,
                        x, (float) y, 0xffFFFFFF, false, matrix4f, source, TextRenderer.TextLayerType.NORMAL, 0, 15728880);

                y -= textRenderer.fontHeight + 2;
            }

            matrices.pop();
        }
    }

    public void setup() {
        log("Initialization");

        configKey = new KeyBinding("gui.act.invView.horse", InputUtil.UNKNOWN_KEY.getCode(), "key.categories.horsedebug");
        KeyBindingHelper.registerKeyBinding(configKey);

        syncConfig();
    }

    public void onKey() {
        if (configKey.wasPressed()) {
            setShow3DOverlay(!isShow3DOverlay());
        }
    }

    public void setShow3DOverlay(boolean show3DOverlay) {
        this.show3DOverlay = show3DOverlay;
        saveConfig();
    }

    public boolean isShow3DOverlay() {
        return show3DOverlay;
    }

    public void syncConfig() {
        Properties prop = new Properties();
        try (InputStream stream = Files.newInputStream(getConfigFile())) {
            prop.load(stream);
        } catch (IOException | IllegalArgumentException e) {
            // ignore
        }

        show3DOverlay = String.valueOf(prop.getOrDefault("show3DOverlay", show3DOverlay)).equals("true");

        saveConfig();
    }

    public void saveConfig() {
        Properties prop = new Properties();
        prop.setProperty("show3DOverlay", String.valueOf(show3DOverlay));

        try (OutputStream stream = Files.newOutputStream(getConfigFile())) {
            prop.store(stream, "");
        } catch (IOException | IllegalArgumentException e) {
            // ignore
        }
    }

    public Path getConfigFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("horsedebug.cfg");
    }


    public Vec3d getMinVec(Box box) {
        return new Vec3d(box.minX, box.minY, box.minZ);
    }

    public Vec3d getMaxVec(Box box) {
        return new Vec3d(box.maxX, box.maxY, box.maxZ);
    }

    public double getCornerLength(Box box) {
        return getMinVec(box).distanceTo(getMaxVec(box));
    }

}
