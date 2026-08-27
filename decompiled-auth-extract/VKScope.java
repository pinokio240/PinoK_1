package com.vk.api.sdk.auth;

import xsna.aqo;
import xsna.zpo;

/* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
/* JADX WARN: Unknown enum class pattern. Please report as an issue! */
/* compiled from: VKScope.kt */
/* loaded from: /home/z/my-project/vk-dex/classes15.dex */
public final class VKScope {
    private static final /* synthetic */ zpo $ENTRIES;
    private static final /* synthetic */ VKScope[] $VALUES;
    public static final VKScope ADS;
    public static final VKScope AUDIO;
    public static final VKScope DOCS;
    public static final VKScope EMAIL;
    public static final VKScope FRIENDS;
    public static final VKScope GROUPS;
    public static final VKScope MARKET;
    public static final VKScope MESSAGES;
    public static final VKScope NOTES;
    public static final VKScope NOTIFICATIONS;
    public static final VKScope NOTIFY;
    public static final VKScope OFFLINE;
    public static final VKScope PAGES;
    public static final VKScope PHONE;
    public static final VKScope PHOTOS;
    public static final VKScope STATS;
    public static final VKScope STATUS;
    public static final VKScope STORIES;
    public static final VKScope VIDEO;
    public static final VKScope WALL;

    static {
        VKScope vKScope = new VKScope("NOTIFY", 0);
        NOTIFY = vKScope;
        VKScope vKScope2 = new VKScope("FRIENDS", 1);
        FRIENDS = vKScope2;
        VKScope vKScope3 = new VKScope("PHOTOS", 2);
        PHOTOS = vKScope3;
        VKScope vKScope4 = new VKScope("AUDIO", 3);
        AUDIO = vKScope4;
        VKScope vKScope5 = new VKScope("VIDEO", 4);
        VIDEO = vKScope5;
        VKScope vKScope6 = new VKScope("STORIES", 5);
        STORIES = vKScope6;
        VKScope vKScope7 = new VKScope("PAGES", 6);
        PAGES = vKScope7;
        VKScope vKScope8 = new VKScope("STATUS", 7);
        STATUS = vKScope8;
        VKScope vKScope9 = new VKScope("NOTES", 8);
        NOTES = vKScope9;
        VKScope vKScope10 = new VKScope("MESSAGES", 9);
        MESSAGES = vKScope10;
        VKScope vKScope11 = new VKScope("WALL", 10);
        WALL = vKScope11;
        VKScope vKScope12 = new VKScope("ADS", 11);
        ADS = vKScope12;
        VKScope vKScope13 = new VKScope("OFFLINE", 12);
        OFFLINE = vKScope13;
        VKScope vKScope14 = new VKScope("DOCS", 13);
        DOCS = vKScope14;
        VKScope vKScope15 = new VKScope("GROUPS", 14);
        GROUPS = vKScope15;
        VKScope vKScope16 = new VKScope("NOTIFICATIONS", 15);
        NOTIFICATIONS = vKScope16;
        VKScope vKScope17 = new VKScope("STATS", 16);
        STATS = vKScope17;
        VKScope vKScope18 = new VKScope("EMAIL", 17);
        EMAIL = vKScope18;
        VKScope vKScope19 = new VKScope("MARKET", 18);
        MARKET = vKScope19;
        VKScope vKScope20 = new VKScope("PHONE", 19);
        PHONE = vKScope20;
        VKScope[] vKScopeArr = {vKScope, vKScope2, vKScope3, vKScope4, vKScope5, vKScope6, vKScope7, vKScope8, vKScope9, vKScope10, vKScope11, vKScope12, vKScope13, vKScope14, vKScope15, vKScope16, vKScope17, vKScope18, vKScope19, vKScope20};
        $VALUES = vKScopeArr;
        $ENTRIES = new aqo(vKScopeArr);
    }

    public VKScope() {
        throw null;
    }

    public static VKScope valueOf(String str) {
        return (VKScope) Enum.valueOf(VKScope.class, str);
    }

    public static VKScope[] values() {
        return (VKScope[]) $VALUES.clone();
    }
}
