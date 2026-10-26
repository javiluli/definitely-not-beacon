package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.util.MessageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Verifica la carga de idiomas y los tooltips con el codec y el formateador reales de Hytale. */
final class BeaconTextChecks {
    private BeaconTextChecks() {}

    static void run(List<String> results) {
        for (String locale : new String[]{"en-US", "es-ES"}) {
            for (BeaconPower power : BeaconPower.values()) {
                for (int rank = 1; rank <= (power == BeaconPower.REGENERATION ? 1 : 2); rank++) {
                    Message tooltip = BeaconText.powerTooltip(power, rank, false);
                    Message decoded = Message.CODEC.decode(Message.CODEC.encode(tooltip, new ExtraInfo()), new ExtraInfo());
                    String text = localized(decoded.getFormattedMessage(), locale);
                    check(!text.contains("{") && !text.contains("beacon."), "Unresolved tooltip: " + locale + " " + power);
                    String effect = I18nModule.get().getMessage(locale, "beacon.effects." + power.effectId(rank) + ".name");
                    check(effect != null && text.startsWith(effect + ":"), "Effect name and tooltip disagree: " + locale + " " + power);
                }
            }
            for (String status : new String[]{"invalidMove", "invalidSelection", "alreadyActive", "paymentRequired"}) {
                String text = localized(Message.translation("beacon.ui." + status).getFormattedMessage(), locale);
                check(!text.isBlank(), "Empty status: " + locale + " " + status);
            }
        }
        tooltip(BeaconPower.HASTE, 1, false, "en-US", "Haste I: +20% mining per hit; +40% attack speed");
        tooltip(BeaconPower.HASTE, 2, false, "es-ES", "Prisa II: +40% de minado por golpe; +60% de cadencia de ataque");
        tooltip(BeaconPower.RESISTANCE, 1, false, "es-ES", "Resistencia I: +5% de resistencia física y de proyectiles; +9 de salud máxima");
        tooltip(BeaconPower.RESISTANCE, 2, false, "en-US", "Resistance II: +8% physical and projectile resistance; +14 maximum health");
        tooltip(BeaconPower.JUMP_BOOST, 1, false, "es-ES", "Salto I: +15% de impulso de salto; -20% de daño por caída");
        tooltip(BeaconPower.JUMP_BOOST, 2, false, "es-ES", "Salto II: +30% de impulso de salto; -40% de daño por caída");
        tooltip(BeaconPower.REGENERATION, 1, false, "en-US", "Regeneration I: Restores 2 health every 1.0 s");
        tooltip(BeaconPower.REGENERATION, 1, false, "es-ES", "Regeneración I: Recupera 2 puntos de vida cada 1.0 s");
        tooltip(BeaconPower.SPEED, 1, true, "en-US", "Speed I: +40% movement speed; -40% sprint stamina cost · Active");
        tooltip(BeaconPower.SPEED, 1, true, "es-ES", "Velocidad I: +40% de velocidad de movimiento; -40% de gasto de stamina al esprintar · Activado");
        tooltip(BeaconPower.SPEED, 2, false, "es-ES", "Velocidad II: +60% de velocidad de movimiento; -60% de gasto de stamina al esprintar");
        tooltip(BeaconPower.STRENGTH, 1, false, "es-ES", "Fuerza I: +40% de daño físico cuerpo a cuerpo");
        tooltip(BeaconPower.STRENGTH, 2, false, "en-US", "Strength II: +80% physical melee damage");
        boolean rejected = false;
        try { BeaconText.powerTooltip(BeaconPower.REGENERATION, 2, false); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Tooltip accepted nonexistent Regeneration II");
        results.add("Beacon text: en-US/es-ES catalogs loaded; all 11 power tooltips survive native serialization; balance, active labels and status translations resolve");
    }

    private static void tooltip(BeaconPower power, int rank, boolean active, String locale, String expected) {
        String actual = localized(BeaconText.powerTooltip(power, rank, active).getFormattedMessage(), locale);
        check(expected.equals(actual), locale + " tooltip: expected '" + expected + "', got '" + actual + "'");
    }

    /** El helper de texto plano nativo fija en-US; aquí resolvemos los hijos en el idioma probado. */
    static String localized(FormattedMessage message, String locale) {
        String template = message.rawText != null ? message.rawText : I18nModule.get().getMessage(locale, message.messageId);
        check(template != null, "Missing translation: " + locale + " " + message.messageId);
        Map<String, FormattedMessage> children = new HashMap<>();
        if (message.messageParams != null) {
            message.messageParams.forEach((key, child) -> {
                FormattedMessage resolved = new FormattedMessage();
                resolved.rawText = localized(child, locale);
                children.put(key, resolved);
            });
        }
        return MessageUtil.formatText(template, message.params, children);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
