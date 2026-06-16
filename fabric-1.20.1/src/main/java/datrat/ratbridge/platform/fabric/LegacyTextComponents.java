package datrat.ratbridge.platform.fabric;

import datrat.ratbridge.bridge.LegacyMinecraftFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

final class LegacyTextComponents {
    private LegacyTextComponents() {
    }

    static Component parse(String message) {
        String text = LegacyMinecraftFormat.normalizeCodes(message);
        MutableComponent result = Component.empty();
        StringBuilder segment = new StringBuilder();
        List<ChatFormatting> active = new ArrayList<>();

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u00A7' && index + 1 < text.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(index + 1));
                if (formatting != null) {
                    appendSegment(result, segment, active);
                    applyFormatting(active, formatting);
                    index++;
                    continue;
                }
            }
            segment.append(current);
        }
        appendSegment(result, segment, active);
        return result;
    }

    private static void appendSegment(MutableComponent target, StringBuilder segment, List<ChatFormatting> active) {
        if (segment.length() == 0) {
            return;
        }
        MutableComponent component = Component.literal(segment.toString());
        if (!active.isEmpty()) {
            component.withStyle(active.toArray(ChatFormatting[]::new));
        }
        target.append(component);
        segment.setLength(0);
    }

    private static void applyFormatting(List<ChatFormatting> active, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) {
            active.clear();
            return;
        }
        if (formatting.isColor()) {
            active.clear();
        }
        if (!active.contains(formatting)) {
            active.add(formatting);
        }
    }
}
