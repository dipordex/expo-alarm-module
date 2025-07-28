package com.expoalarmmodule;

import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.goodforgod.gson.configuration.deserializer.LocalDateDeserializer;
import io.goodforgod.gson.configuration.deserializer.ZonedDateTimeDeserializer;
import io.goodforgod.gson.configuration.serializer.LocalDateSerializer;
import io.goodforgod.gson.configuration.serializer.ZonedDateTimeSerializer;

public class GsonUtil {

    // Prevent instantiation
    private GsonUtil() {}

    /**
     * Creates a basic Gson instance with default configuration.
     */
    public static Gson create() {
        return new GsonBuilder().create();
    }

    /**
     * Creates a Gson instance with support for LocalDate and ZonedDateTime,
     * using ISO 8601 format in UTC for Android O and above.
     */
    public static Gson createSerialize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .registerTypeAdapter(LocalDate.class,
                            new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE))
                    .registerTypeAdapter(LocalDate.class,
                            new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE))
                    .registerTypeAdapter(ZonedDateTime.class,
                            new ZonedDateTimeSerializer(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"))))
                    .registerTypeAdapter(ZonedDateTime.class,
                            new ZonedDateTimeDeserializer(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"))))
                    .create();
        } else {
            return new GsonBuilder().create(); // Fallback for older Android
        }
    }
}
