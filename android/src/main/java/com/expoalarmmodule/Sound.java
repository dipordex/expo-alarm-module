package com.expoalarmmodule;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

class Sound {

    private static final String TAG = "AlarmSound";
    private static final long DEFAULT_VIBRATION = 100;

    private AudioManager audioManager;
    private int userVolume;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private Context context;

    Sound(Context context) {
        Log.d(TAG, "Sound constructor called");
        this.context = context;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.userVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        this.mediaPlayer = new MediaPlayer();
    }

    void play(String sound, Boolean isVibration) {
        Log.d(TAG, "play called with sound: " + sound);
        Uri soundUri = getSoundUri(sound);
        playSound(soundUri);
        if (isVibration) {
            startVibration();
        }
    }

    void stop() {
        Log.d(TAG, "stop called");
        try {
            if (mediaPlayer.isPlaying()) {
                stopSound();
                stopVibration();
                mediaPlayer.release();
            }
        } catch (IllegalStateException e) {
            Log.d(TAG, "Sound has probably been released already");
        }
    }

    private void playSound(Uri soundUri) {
        Log.d(TAG, "playSound called with URI: " + soundUri);
        try {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.reset();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                mediaPlayer.setLooping(true);

                if (soundUri.getScheme() != null && soundUri.getScheme().startsWith("http")) {
                    mediaPlayer.setDataSource(soundUri.toString());
                } else {
                    mediaPlayer.setDataSource(context, soundUri);
                }

                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.prepareAsync();

                mediaPlayer.setOnPreparedListener(MediaPlayer::start);

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                    return true;
                });

                Log.d(TAG, "Alarm sound initiated");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound", e);
        }
    }

    private void stopSound() {
        Log.d(TAG, "stopSound called");
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, userVolume, AudioManager.FLAG_PLAY_SOUND);
            mediaPlayer.stop();
            mediaPlayer.reset();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "ringtone: " + e.getMessage());
        }
    }

    private void startVibration() {
        Log.d(TAG, "startVibration called");
        vibrator.vibrate(DEFAULT_VIBRATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(500);
        }

        long[] pattern = {0, 100, 1000};
        vibrator.vibrate(pattern, 0);
    }

    private void stopVibration() {
        Log.d(TAG, "stopVibration called");
        vibrator.cancel();
    }

    private Uri getSoundUri(String soundName) {
        Log.d(TAG, "getSoundUri called with soundName: " + soundName);

        Uri soundUri;
        try {
            if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("default")) {
                soundUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
            } else if (soundName.startsWith("http://") || soundName.startsWith("https://") || soundName.startsWith("content://")) {
                soundUri = Uri.parse(soundName);
            } else {
                soundUri = getSystemAlarmSoundUriByName(soundName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving sound URI", e);
            soundUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
        }

        return soundUri;
    }

    private Uri getSystemAlarmSoundUriByName(String name) {
        Log.d(TAG, "getSystemAlarmSoundUriByName called with name: " + name);

        RingtoneManager ringtoneMgr = new RingtoneManager(context);
        ringtoneMgr.setType(RingtoneManager.TYPE_ALARM);
        Cursor cursor = ringtoneMgr.getCursor();

        Log.d(TAG, "Available system alarm sounds:");
        int index = 0;
        while (cursor.moveToNext()) {
            String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            Uri uri = ringtoneMgr.getRingtoneUri(cursor.getPosition());
            Log.d(TAG, "  " + (++index) + ". " + title + " — URI: " + uri.toString());
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            Uri uri = ringtoneMgr.getRingtoneUri(cursor.getPosition());
            if (title.equalsIgnoreCase(name)) {
                Log.d(TAG, "Matched system tone: " + title);
                return uri;
            }
        }

        Log.w(TAG, "No matching system alarm tone found for: " + name + " — using default");
        return Settings.System.DEFAULT_ALARM_ALERT_URI;
    }
}
