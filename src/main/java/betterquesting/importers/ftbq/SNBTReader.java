package betterquesting.importers.ftbq;

import betterquesting.core.BetterQuesting;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SNBTReader {
    private static final Pattern BYTE_ARRAY_MATCHER = Pattern.compile("\\[B;([\\s\\d,b]*)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_ARRAY_MATCHER = Pattern.compile("\\[L;([\\s\\d,l]*)]", Pattern.CASE_INSENSITIVE);

    @Nullable
    public static NBTTagCompound read(File file) {
        if (!file.exists())
            return null;

        String trimmedText;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            trimmedText = sb.toString();
        } catch (IOException e) {
            BetterQuesting.logger.error("Failed to read " + file.getAbsolutePath() + ": " + e);
            return null;
        }

        String fixed = fixLongArray(fixByteArray(trimmedText));

        try {
            return JsonToNBT.getTagFromJson(fixed);
        } catch (NBTException e) {
            BetterQuesting.logger.error("Failed to read " + file.getAbsolutePath() + ": " + e);
            return null;
        }
    }

    private static String fixByteArray(String text) {
        StringBuffer sb = new StringBuffer(text.length());
        Matcher matcher = BYTE_ARRAY_MATCHER.matcher(text);

        while (matcher.find()) {
            String s2 = matcher.group(1);

            if (!s2.isEmpty()) {
                String[] s3 = s2.split(",");

                for (int i = 0; i < s3.length; i++) {
                    if (!s3[i].endsWith("b") && !s3[i].endsWith("B")) {
                        s3[i] += 'b';
                    }
                }

                matcher.appendReplacement(sb, "[B;" + String.join(",", s3) + "]");
            } else {
                matcher.appendReplacement(sb, "[B;]");
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String fixLongArray(String text) {
        StringBuffer sb = new StringBuffer(text.length());
        Matcher matcher = LONG_ARRAY_MATCHER.matcher(text);

        while (matcher.find()) {
            String s2 = matcher.group(1);

            if (!s2.isEmpty()) {
                String[] s3 = s2.split(",");

                for (int i = 0; i < s3.length; i++) {
                    if (!s3[i].endsWith("l") && !s3[i].endsWith("L")) {
                        s3[i] += 'L';
                    }
                }

                matcher.appendReplacement(sb, "[L;" + String.join(",", s3) + "]");
            } else {
                matcher.appendReplacement(sb, "[L;]");
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }


}
